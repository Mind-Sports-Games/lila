package lila.plan

import com.softwaremill.tagging._
import org.joda.time.DateTime
import play.api.i18n.Lang
import reactivemongo.api._
import scala.concurrent.duration._
import cats.syntax.all._
import org.joda.time.{ DateTime, Days }

import lila.common.config.Secret
import lila.common.{ Bus, IpAddress }
import lila.db.dsl._
import lila.memo.CacheApi._
import lila.user.{ User, UserRepo }
import lila.common.EmailAddress

final class PlanApi(
    stripeClient: StripeClient,
    payPalClient: PayPalClient,
    patronColl: Coll @@ PatronColl,
    chargeColl: Coll @@ ChargeColl,
    notifier: PlanNotifier,
    userRepo: UserRepo,
    lightUserApi: lila.user.LightUserApi,
    cacheApi: lila.memo.CacheApi,
    mongoCache: lila.memo.MongoCache.Api,
    monthlyGoalApi: MonthlyGoalApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  import BsonHandlers._
  import PatronHandlers._
  import ChargeHandlers._

  def switch(user: User, cents: Cents): Fu[StripeSubscription] =
    stripe.userCustomer(user) flatMap {
      case None => fufail(s"Can't switch non-existent customer ${user.id}")
      case Some(customer) =>
        customer.firstSubscription match {
          case None                                       => fufail(s"Can't switch non-existent subscription of ${user.id}")
          case Some(sub) if sub.item.price.cents == cents => fuccess(sub)
          case Some(sub)                                  => stripeClient.updateSubscription(sub, cents)
        }
    }

  def cancel(user: User): Funit = {
    def onCancel =
      isLifetime(user).flatMap { lifetime =>
        !lifetime ?? setDbUserPlan(user, user.plan.disable)
      } >>
        patronColl.update
          .one($id(user.id), $unset("stripe", "payPal", "payPalCheckout", "expiresAt"))
          .void >>-
        logger.info(s"Canceled subscription of ${user.username}")
    stripe.userCustomer(user) flatMap {
      case Some(customer) =>
        customer.firstSubscription match {
          case None      => fufail(s"Can't cancel non-existent subscription of ${user.id}")
          case Some(sub) => stripeClient.cancelSubscription(sub) >> onCancel
        }
      case None =>
        payPal.userSubscription(user) flatMap {
          case None      => fufail(s"Can't cancel non-existent customer ${user.id}")
          case Some(sub) => payPalClient.cancelSubscription(sub) >> onCancel
        }
    }
  }

  object stripe {

    def getEvent = stripeClient.getEvent _

    def onCharge(stripeCharge: StripeCharge): Funit =
      customerIdPatron(stripeCharge.customer) flatMap { patronOption =>
        val charge = Charge.make(
          userId = patronOption.map(_.userId),
          stripe = Charge.Stripe(stripeCharge.id, stripeCharge.customer).some,
          cents = stripeCharge.amount
        )
        addCharge(charge, stripeCharge.country) >> {
          patronOption match {
            case None =>
              logger.info(s"Charged anon customer $charge")
              funit
            case Some(patron) =>
              logger.info(s"Charged $charge $patron")
              userRepo byId patron.userId orFail s"Missing user for $patron" flatMap { user =>
                val p2 = patron
                  .copy(
                    stripe = Patron.Stripe(stripeCharge.customer).some,
                    free = none
                  )
                  .levelUpIfPossible
                patronColl.update.one($id(patron.id), p2) >>
                  setDbUserPlanOnCharge(user, patron.canLevelUp) >> {
                    stripeCharge.lifetimeWorthy ?? setLifetime(user)
                  }
              }
          }
        }
      }

    def onSubscriptionDeleted(sub: StripeSubscription): Funit =
      customerIdPatron(sub.customer) flatMap {
        _ ?? { patron =>
          if (patron.isLifetime) funit
          else
            userRepo byId patron.userId orFail s"Missing user for $patron" flatMap { user =>
              setDbUserPlan(user, user.plan.disable) >>
                patronColl.update.one($id(user.id), patron.removeStripe).void >>-
                notifier.onExpire(user) >>-
                logger.info(s"Unsubed ${user.username} $sub")
            }
        }
      }

    def onCompletedSession(completedSession: StripeCompletedSession): Funit =
      customerIdPatron(completedSession.customer) flatMap {
        case None =>
          logger.warn(s"Completed Session of unknown patron $completedSession")
          funit
        case Some(prevPatron) =>
          userRepo byId prevPatron.userId orFail s"Missing user for $prevPatron" flatMap { user =>
            val patron = prevPatron
              .copy(lastLevelUp = Some(DateTime.now))
              .removePayPal
              .expireInOneMonth(!completedSession.freq.renew)
            patronColl.update.one($id(user.id), patron, upsert = true).void
          }
      }

    def customerInfo(user: User, customer: StripeCustomer): Fu[Option[CustomerInfo]] =
      stripeClient.getNextInvoice(customer.id) zip
        stripeClient.getPastInvoices(customer.id) zip
        customer.firstSubscription.??(stripeClient.getPaymentMethod) map {
          case ((Some(nextInvoice), pastInvoices), paymentMethod) =>
            customer.firstSubscription match {
              case Some(sub) => MonthlyCustomerInfo(sub, nextInvoice, pastInvoices, paymentMethod).some
              case None =>
                logger.warn(s"Can't identify ${user.username} monthly subscription $customer")
                none
            }
          case ((None, _), _) => OneTimeCustomerInfo(customer).some
        }

    private def saveCustomer(user: User, customerId: StripeCustomerId): Funit =
      userPatron(user) flatMap { patronOpt =>
        val patron = patronOpt
          .getOrElse(Patron(_id = Patron.UserId(user.id)))
          .copy(stripe = Patron.Stripe(customerId).some)
        patronColl.update.one($id(user.id), patron, upsert = true).void
      }

    def userCustomerId(user: User): Fu[Option[StripeCustomerId]] =
      userPatron(user) map {
        _.flatMap { _.stripe.map(_.customerId) }
      }

    def userCustomer(user: User): Fu[Option[StripeCustomer]] =
      userCustomerId(user) flatMap {
        _ ?? stripeClient.getCustomer
      }

    def getOrMakeCustomer(user: User, data: PlanCheckout): Fu[StripeCustomer] =
      userCustomer(user) getOrElse makeCustomer(user, data)

    def makeCustomer(user: User, data: PlanCheckout): Fu[StripeCustomer] =
      stripeClient.createCustomer(user, data) flatMap { customer =>
        saveCustomer(user, customer.id) inject customer
      }

    def getOrMakeCustomerId(user: User, data: PlanCheckout): Fu[StripeCustomerId] =
      getOrMakeCustomer(user, data).map(_.id)

    def patronCustomer(patron: Patron): Fu[Option[StripeCustomer]] =
      patron.stripe.map(_.customerId) ?? stripeClient.getCustomer

    private def customerIdPatron(id: StripeCustomerId): Fu[Option[Patron]] =
      patronColl.one[Patron]($doc("stripe.customerId" -> id))

    def createSession(data: CreateStripeSession)(implicit lang: Lang): Fu[StripeSession] =
      data.checkout.freq match {
        case Freq.Onetime => stripeClient.createOneTimeSession(data)
        case Freq.Monthly => stripeClient.createMonthlySession(data)
      }

    def createPaymentUpdateSession(sub: StripeSubscription, nextUrls: NextUrls): Fu[StripeSession] =
      stripeClient.createPaymentUpdateSession(sub, nextUrls)

    def updatePayment(sub: StripeSubscription, sessionId: String) =
      stripeClient.getSession(sessionId) flatMap {
        _ ?? { session =>
          stripeClient.setCustomerPaymentMethod(sub.customer, session.setup_intent.payment_method) zip
            stripeClient.setSubscriptionPaymentMethod(sub, session.setup_intent.payment_method) void
        }
      }
  }

  object payPal {

    def getEvent = payPalClient.getEvent _

    def userSubscriptionId(user: User): Fu[Option[PayPalSubscriptionId]] =
      userPatron(user) map {
        _.flatMap { _.payPalCheckout.flatMap(_.subscriptionId) }
      }

    def userSubscription(user: User): Fu[Option[PayPalSubscription]] =
      userSubscriptionId(user) flatMap {
        _ ?? payPalClient.getSubscription
      }

    def createOrder(checkout: PlanCheckout, user: User) =
      for {
        isLifetime <- isLifetime(user)
        order      <- payPalClient.createOrder(CreatePayPalOrder(checkout, user, isLifetime))
      } yield order

    def createSubscription(checkout: PlanCheckout, user: User) =
      payPalClient.createSubscription(checkout, user)

    def captureOrder(orderId: PayPalOrderId, ip: IpAddress) = for {
      order <- payPalClient.captureOrder(orderId)
      cents <- order.capturedMoney.fold[Fu[Cents]](fufail(s"Invalid paypal capture $order"))(fuccess)
      _ <-
        if (cents.value < 100) {
          logger.info(s"Ignoring invalid paypal amount from $ip ${order.userId} $cents ${orderId}")
          funit
        } else {
          val charge = Charge.make(
            userId = order.userId,
            payPalCheckout = Patron.PayPalCheckout(order.payer.id, none).some,
            cents = cents
          )
          addCharge(charge, order.country) >>
            (order.userId ?? userRepo.named) flatMap {
              _ ?? { user =>
                def newPayPalCheckout = Patron.PayPalCheckout(order.payer.id, none)
                userPatron(user).flatMap {
                  case None =>
                    patronColl.insert.one(
                      Patron(
                        _id = Patron.UserId(user.id),
                        payPalCheckout = newPayPalCheckout.some,
                        lastLevelUp = Some(DateTime.now)
                      ).expireInOneMonth
                    ) >>
                      setDbUserPlanOnCharge(user, levelUp = false)
                  case Some(patron) =>
                    val p2 = patron
                      .copy(
                        payPalCheckout = patron.payPalCheckout orElse newPayPalCheckout.some,
                        free = none
                      )
                      .levelUpIfPossible
                      .expireInOneMonth
                    patronColl.update.one($id(patron.id), p2) >>
                      setDbUserPlanOnCharge(user, patron.canLevelUp)
                } >> {
                  charge.lifetimeWorthy ?? setLifetime(user)
                } >>- logger.info(s"Charged ${user.username} with paypal: $cents")
              }
            }
        }
    } yield ()

    def captureSubscription(
        orderId: PayPalOrderId,
        subId: PayPalSubscriptionId,
        user: User,
        ip: IpAddress
    ) = for {
      order <- payPalClient.getOrder(orderId) orFail s"Missing paypal order for id $orderId"
      sub   <- payPalClient.getSubscription(subId) orFail s"Missing paypal subscription for order $order"
      cents = sub.capturedMoney
      _ <-
        if (cents.value < 100) {
          logger.info(s"Ignoring invalid paypal amount from $ip ${order.userId} $cents $orderId")
          funit
        } else {
          val charge = Charge.make(
            userId = user.id.some,
            payPalCheckout = Patron.PayPalCheckout(order.payer.id, sub.id.some).some,
            cents = cents
          )
          addCharge(charge, order.country) >> {
            val payPalCheckout = Patron.PayPalCheckout(order.payer.id, subId.some)
            userPatron(user).flatMap {
              case None =>
                patronColl.insert.one(
                  Patron(
                    _id = Patron.UserId(user.id),
                    payPalCheckout = payPalCheckout.some,
                    lastLevelUp = Some(DateTime.now)
                  ).expireInOneMonth
                ) >>
                  setDbUserPlanOnCharge(user, levelUp = false)
              case Some(patron) =>
                val p2 = patron
                  .copy(
                    payPalCheckout = payPalCheckout.some,
                    stripe = none,
                    free = none
                  )
                  .levelUpIfPossible
                  .expireInOneMonth
                patronColl.update.one($id(patron.id), p2) >>
                  setDbUserPlanOnCharge(user, patron.canLevelUp)
            } >> {
              charge.lifetimeWorthy ?? setLifetime(user)
            } >>- logger.info(s"Charged ${user.username} with paypal checkout: $cents")
          }
        }
    } yield ()

    def subscriptionUser(id: PayPalSubscriptionId): Fu[Option[User]] =
      subscriptionIdPatron(id) flatMap { _.map(_.id.value) ?? userRepo.byId }

    private def subscriptionIdPatron(id: PayPalSubscriptionId): Fu[Option[Patron]] =
      patronColl.one[Patron]($doc("payPalCheckout.subscriptionId" -> id))
  }

  private def setDbUserPlanOnCharge(user: User, levelUp: Boolean): Funit = {
    val plan =
      if (levelUp) user.plan.incMonths
      else user.plan.enable
    Bus.publish(lila.hub.actorApi.plan.MonthInc(user.id, plan.months), "plan")
    if (plan.months > 1) notifier.onRenew(user.copy(plan = plan))
    else notifier.onStart(user)
    setDbUserPlan(user, plan)
  }

  import PlanApi.SyncResult.{ ReloadUser, Synced }

  def sync(user: User): Fu[PlanApi.SyncResult] =
    userPatron(user) flatMap {

      case None if user.plan.active =>
        logger.warn(s"${user.username} sync: disable plan of non-patron")
        setDbUserPlan(user, user.plan.disable) inject ReloadUser

      case None => fuccess(Synced(none, none, none))

      case Some(patron) =>
        (patron.stripe, patron.payPalCheckout, patron.payPal) match {

          case (Some(stripe), _, _) =>
            stripeClient.getCustomer(stripe.customerId) flatMap {
              case None =>
                logger.warn(s"${user.username} sync: unset DB patron that's not in stripe")
                patronColl.update.one($id(patron.id), patron.removeStripe) >> sync(user)
              case Some(customer) if customer.firstSubscription.exists(_.isActive) && !user.plan.active =>
                logger.warn(s"${user.username} sync: enable plan of customer with a stripe subscription")
                setDbUserPlan(user, user.plan.enable) inject ReloadUser
              case customer => fuccess(Synced(patron.some, customer, none))
            }

          case (_, Some(Patron.PayPalCheckout(_, Some(subId))), _) =>
            payPalClient.getSubscription(subId) flatMap {
              case None =>
                logger.warn(s"${user.username} sync: unset DB patron that's not in paypal")
                patronColl.update.one($id(patron.id), patron.removePayPalCheckout) >> sync(user)
              case Some(subscription) if subscription.isActive && !user.plan.active =>
                logger.warn(s"${user.username} sync: enable plan of customer with a payPal subscription")
                setDbUserPlan(user, user.plan.enable) inject ReloadUser
              case subscription => fuccess(Synced(patron.some, none, subscription))
            }
          case (_, _, Some(_)) =>
            if (!user.plan.active) {
              logger.warn(s"${user.username} sync: enable plan of customer with paypal")
              setDbUserPlan(user, user.plan.enable) inject ReloadUser
            } else fuccess(Synced(patron.some, none, none))

          case (None, None, None) if patron.isLifetime => fuccess(Synced(patron.some, none, none))

          case (None, None, None) if user.plan.active && patron.free.isEmpty =>
            logger.warn(s"${user.username} sync: disable plan of patron with no paypal or stripe")
            setDbUserPlan(user, user.plan.disable) inject ReloadUser

          case _ => fuccess(Synced(patron.some, none, none))
        }
    }

  def isLifetime(user: User): Fu[Boolean] =
    userPatron(user) map {
      _.exists(_.isLifetime)
    }

  def setLifetime(user: User): Funit = {
    if (user.plan.isEmpty) Bus.publish(lila.hub.actorApi.plan.MonthInc(user.id, 0), "plan")
    userRepo.setPlan(
      user,
      user.plan.enable
    ) >> patronColl.update
      .one(
        $id(user.id),
        $set(
          "lastLevelUp" -> DateTime.now,
          "lifetime"    -> true,
          "free"        -> Patron.Free(DateTime.now)
        ),
        upsert = true
      )
      .void >>- lightUserApi.invalidate(user.id)
  }

  def giveMonth(user: User): Funit =
    patronColl.update
      .one(
        $id(user.id),
        $set(
          "lastLevelUp" -> DateTime.now,
          "lifetime"    -> false,
          "free"        -> Patron.Free(DateTime.now),
          "expiresAt"   -> DateTime.now.plusMonths(1)
        ),
        upsert = true
      )
      .void >> setDbUserPlanOnCharge(user, levelUp = false)

  def remove(user: User): Funit =
    userRepo.unsetPlan(user) >>
      patronColl.unsetField($id(user.id), "lifetime").void >>-
      lightUserApi.invalidate(user.id)

  private val recentChargeUserIdsNb = 100
  private val recentChargeUserIdsCache = cacheApi.unit[List[User.ID]] {
    _.refreshAfterWrite(30 minutes)
      .buildAsyncFuture { _ =>
        chargeColl.primitive[User.ID](
          $empty,
          sort = $doc("date" -> -1),
          nb = recentChargeUserIdsNb * 3 / 2,
          "userId"
        ) flatMap filterUserIds dmap (_ take recentChargeUserIdsNb)
      }
  }

  def recentChargeUserIds: Fu[List[User.ID]] = recentChargeUserIdsCache.getUnit

  def recentChargesOf(user: User): Fu[List[Charge]] =
    chargeColl.find($doc("userId" -> user.id)).sort($doc("date" -> -1)).cursor[Charge]().list()

  private[plan] def onEmailChange(userId: User.ID, email: EmailAddress): Funit =
    userRepo enabledById userId flatMap {
      _ ?? { user =>
        stripe.userCustomer(user) flatMap {
          _.filterNot(_.email.has(email.value)) ?? {
            stripeClient.setCustomerEmail(_, email)
          }
        }
      }
    }

  private val topPatronUserIdsNb = 300
  private val topPatronUserIdsCache = mongoCache.unit[List[User.ID]](
    "patron:top",
    59 minutes
  ) { loader =>
    _.refreshAfterWrite(60 minutes)
      .buildAsyncFuture {
        loader { _ =>
          chargeColl
            .aggregateList(
              maxDocs = topPatronUserIdsNb * 2,
              readPreference = ReadPreference.secondaryPreferred
            ) { framework =>
              import framework._
              Match($doc("userId" $exists true)) -> List(
                GroupField("userId")("total" -> SumField("cents")),
                Sort(Descending("total")),
                Limit(topPatronUserIdsNb * 3 / 2)
              )
            }
            .dmap {
              _.flatMap { _.getAsOpt[User.ID]("_id") }
            } flatMap filterUserIds dmap (_ take topPatronUserIdsNb)
        }
      }
  }

  def topPatronUserIds: Fu[List[User.ID]] = topPatronUserIdsCache.get {}

  private def filterUserIds(ids: List[User.ID]): Fu[List[User.ID]] = {
    val dedup = ids.distinct
    userRepo.filterByEnabledPatrons(dedup) map { enableds =>
      dedup filter enableds.contains
    }
  }

  private def addCharge(charge: Charge, country: Option[Country]): Funit =
    monitorCharge(charge, country) >>
      chargeColl.insert.one(charge).void >>- {
        recentChargeUserIdsCache.invalidateUnit()
        monthlyGoalApi.get foreach { m =>
          Bus.publish(
            lila.hub.actorApi.plan.ChargeEvent(
              username = charge.userId.flatMap(lightUserApi.sync).fold("Anonymous")(_.name),
              amount = charge.cents.value,
              percent = m.percent,
              DateTime.now
            ),
            "plan"
          )
          lila.mon.plan.goal.update(m.goal.value)
          lila.mon.plan.current.update(m.current.value)
          lila.mon.plan.percent.update(m.percent)
          if (charge.isPayPalCheckout) lila.mon.plan.paypalCheckout.amount.record(charge.cents.value)
          else if (charge.isStripe) lila.mon.plan.stripe.record(charge.cents.value)
        }
      }

  private def monitorCharge(charge: Charge, country: Option[Country]): Funit = {
    lila.mon.plan.charge
      .countryCents(country = country.fold("unknown")(_.code), service = charge.serviceName)
      .record(charge.cents.value)
    charge.userId ?? { userId =>
      chargeColl.exists($doc("userId" -> userId)) map {
        case false => lila.mon.plan.charge.first(charge.serviceName).increment().unit
        case _     =>
      }
    }
  }

  private def setDbUserPlan(user: User, plan: lila.user.Plan): Funit =
    userRepo.setPlan(user, plan) >>- lightUserApi.invalidate(user.id)

  def userPatron(user: User): Fu[Option[Patron]] = patronColl.one[Patron]($id(user.id))
}

object PlanApi {

  sealed trait SyncResult
  object SyncResult {
    case object ReloadUser extends SyncResult
    case class Synced(
        patron: Option[Patron],
        stripeCustomer: Option[StripeCustomer],
        payPalSubscription: Option[PayPalSubscription]
    ) extends SyncResult
  }
}
