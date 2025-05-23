package controllers

import play.api.libs.json._
import play.api.mvc._
import scala.concurrent.duration._

import lila.api.Context
import lila.app._
import lila.common.{ EmailAddress, HTTPRequest }
import lila.plan.StripeClient.StripeException
import lila.plan.{
  Cents,
  CreateStripeSession,
  Freq,
  MonthlyCustomerInfo,
  NextUrls,
  OneTimeCustomerInfo,
  Patron,
  PayPalOrderId,
  PayPalSubscription,
  PayPalSubscriptionId,
  PlanCheckout,
  StripeCustomer,
  StripeCustomerId
}
import lila.user.{ User => UserModel }
import views._

final class Plan(env: Env)(implicit system: akka.actor.ActorSystem) extends LilaController(env) {

  private val logger = lila.log("plan")

  def index =
    Open { implicit ctx =>
      pageHit
      ctx.me.fold(indexAnon) { me =>
        import lila.plan.PlanApi.SyncResult._
        env.plan.api.sync(me) flatMap {
          case ReloadUser => Redirect(routes.Plan.index).fuccess
          case Synced(Some(patron), None, None) =>
            env.user.repo email me.id flatMap { email =>
              renderIndex(email, patron.some)
            }
          case Synced(Some(patron), Some(stripeCus), _) => indexStripePatron(me, patron, stripeCus)
          case Synced(Some(patron), _, Some(payPalSub)) => indexPayPalPatron(me, patron, payPalSub)
          case _                                        => indexFreeUser(me)
        }
      }
    }

  def list =
    Open { implicit ctx =>
      ctx.me.fold(Redirect(routes.Plan.index).fuccess) { me =>
        import lila.plan.PlanApi.SyncResult._
        env.plan.api.sync(me) flatMap {
          case ReloadUser            => Redirect(routes.Plan.list).fuccess
          case Synced(Some(_), _, _) => indexFreeUser(me)
          case _                     => Redirect(routes.Plan.index).fuccess
        }
      }
    }

  private def indexAnon(implicit ctx: Context) = renderIndex(email = none, patron = none)

  private def indexFreeUser(me: UserModel)(implicit ctx: Context) =
    env.user.repo email me.id flatMap { email =>
      renderIndex(email, patron = none)
    }

  private def renderIndex(email: Option[EmailAddress], patron: Option[lila.plan.Patron])(implicit
      ctx: Context
  ): Fu[Result] =
    for {
      recentIds <- env.plan.api.recentChargeUserIds
      bestIds   <- env.plan.api.topPatronUserIds
      _         <- env.user.lightUserApi preloadMany { recentIds ::: bestIds }
    } yield Ok(
      html.plan.index(
        stripePublicKey = env.plan.stripePublicKey,
        payPalPublicKey = env.plan.payPalPublicKey,
        email = email,
        patron = patron,
        recentIds = recentIds,
        bestIds = bestIds
      )
    )

  private def indexStripePatron(me: UserModel, patron: lila.plan.Patron, customer: StripeCustomer)(implicit
      ctx: Context
  ) =
    env.plan.api.stripe.customerInfo(me, customer) flatMap {
      case Some(info: MonthlyCustomerInfo) =>
        Ok(html.plan.indexStripe(me, patron, info, stripePublicKey = env.plan.stripePublicKey)).fuccess
      case Some(info: OneTimeCustomerInfo) =>
        renderIndex(info.customer.email map EmailAddress.apply, patron.some)
      case None =>
        env.user.repo email me.id flatMap { email =>
          renderIndex(email, patron.some)
        }
    }

  private def indexPayPalPatron(me: UserModel, patron: lila.plan.Patron, subscription: PayPalSubscription)(
      implicit ctx: Context
  ) = Ok(html.plan.indexPayPal(me, patron, subscription)).fuccess

  def features =
    Open { implicit ctx =>
      pageHit
      fuccess {
        html.plan.features()
      }
    }

  def switch =
    AuthBody { implicit ctx => me =>
      implicit val req = ctx.body
      lila.plan.Switch.form
        .bindFromRequest()
        .fold(
          _ => funit,
          data => env.plan.api.switch(me, data.cents)
        ) inject Redirect(routes.Plan.index)
    }

  def cancel =
    AuthBody { _ => me =>
      env.plan.api.cancel(me) inject Redirect(routes.Plan.index)
    }

  def thanks =
    Open { implicit ctx =>
      // wait for the payment data from stripe or paypal
      lila.common.Future.delay(2.seconds) {
        ctx.me ?? env.plan.api.userPatron flatMap { patron =>
          patron ?? env.plan.api.stripe.patronCustomer map { customer =>
            Ok(html.plan.thanks(patron, customer))
          }
        }
      }
    }

  def webhook =
    Action.async(parse.json) { req =>
      if (req.headers.hasHeader("PAYPAL-TRANSMISSION-SIG"))
        env.plan.webhook.payPal(req.body) inject Ok("kthxbye")
      else
        env.plan.webhook.stripe(req.body) inject Ok("kthxbye")
    }

  def badStripeSession[A: Writes](err: A) = BadRequest(jsonError(err))
  def badStripeApiCall: PartialFunction[Throwable, Result] = { case e: StripeException =>
    logger.error("Plan.stripeCheckout", e)
    badStripeSession("Stripe API call failed")
  }

  private def createStripeSession(checkout: PlanCheckout, customerId: StripeCustomerId)(implicit
      ctx: Context
  ) = {
    for {
      session <- env.plan.api.stripe
        .createSession(
          CreateStripeSession(
            customerId,
            checkout,
            NextUrls(
              cancel = s"${env.net.baseUrl}${routes.Plan.index}",
              success = s"${env.net.baseUrl}${routes.Plan.thanks}"
            ),
            isLifetime = checkout.cents.value >= Cents.lifetime.value
          )
        )
    } yield JsonOk(Json.obj("session" -> Json.obj("id" -> session.id.value)))
  }.recover(badStripeApiCall)

  def switchStripePlan(user: UserModel, cents: Cents) = {
    env.plan.api
      .switch(user, cents)
      .inject(JsonOk(Json.obj("switch" -> Json.obj("cents" -> cents.value))))
      .recover(badStripeApiCall)
  }

  private val CheckoutRateLimit = lila.memo.RateLimit.composite[lila.common.IpAddress](
    key = "plan.checkout.ip",
    enforce = env.net.rateLimit.value
  )(
    ("fast", 8, 10.minute),
    ("slow", 40, 1.day)
  )

  private val CaptureRateLimit = lila.memo.RateLimit.composite[lila.common.IpAddress](
    key = "plan.capture.ip",
    enforce = env.net.rateLimit.value
  )(
    ("fast", 8, 10.minute),
    ("slow", 40, 1.day)
  )

  def stripeCheckout =
    AuthBody { implicit ctx => me =>
      implicit val req = ctx.body
      CheckoutRateLimit(HTTPRequest ipAddress req) {
        lila.plan.PlanCheckout.form
          .bindFromRequest()
          .fold(
            err => {
              logger.info(s"Plan.stripeCheckout 400: $err")
              badStripeSession(err.toString).fuccess
            },
            checkout =>
              env.plan.api.stripe.userCustomer(me) flatMap {
                case Some(customer) if checkout.freq == Freq.Onetime =>
                  createStripeSession(checkout, customer.id)
                case Some(customer) if customer.firstSubscription.isDefined =>
                  switchStripePlan(me, checkout.amount)
                case _ =>
                  env.plan.api.stripe
                    .makeCustomer(me, checkout)
                    .flatMap(customer => createStripeSession(checkout, customer.id))
              }
          )
      }(rateLimitedFu)
    }

  def updatePayment =
    AuthBody { implicit ctx => me =>
      implicit val req = ctx.body
      CaptureRateLimit(HTTPRequest ipAddress req) {
        env.plan.api.stripe.userCustomer(me) flatMap {
          _.flatMap(_.firstSubscription) ?? { sub =>
            env.plan.api.stripe
              .createPaymentUpdateSession(
                sub,
                NextUrls(
                  cancel = s"${env.net.baseUrl}${routes.Plan.index}",
                  success =
                    s"${env.net.baseUrl}${routes.Plan.updatePaymentCallback}?session={CHECKOUT_SESSION_ID}"
                )
              )
              .map(session => JsonOk(Json.obj("session" -> Json.obj("id" -> session.id.value))))
              .recover(badStripeApiCall)
          }
        }
      }(rateLimitedFu)
    }

  def updatePaymentCallback =
    AuthBody { implicit ctx => me =>
      get("session") ?? { session =>
        env.plan.api.stripe.userCustomer(me) flatMap {
          _.flatMap(_.firstSubscription) ?? { sub =>
            env.plan.api.stripe.updatePayment(sub, session) inject Redirect(routes.Plan.index)
          }
        }
      }
    }

  def payPalCheckout =
    AuthBody { implicit ctx => me =>
      implicit val req = ctx.body
      CheckoutRateLimit(ctx.ip) {
        lila.plan.PlanCheckout.form
          .bindFromRequest()
          .fold(
            err => {
              logger.info(s"Plan.payPalCheckout 400: $err")
              BadRequest(jsonError(err.errors.map(_.message) mkString ", ")).fuccess
            },
            checkout => {
              if (checkout.freq.renew) for {
                sub <- env.plan.api.payPal.createSubscription(checkout, me)
              } yield JsonOk(Json.obj("subscription" -> Json.obj("id" -> sub.id.value)))
              else
                for {
                  order <- env.plan.api.payPal.createOrder(checkout, me)
                } yield JsonOk(Json.obj("order" -> Json.obj("id" -> order.id.value)))
            }
          )
      }(rateLimitedFu)
    }

  def payPalCapture(orderId: String) =
    Auth { implicit ctx => me =>
      CaptureRateLimit(ctx.ip) {
        (get("sub") map PayPalSubscriptionId match {
          case None => env.plan.api.payPal.captureOrder(PayPalOrderId(orderId), ctx.ip)
          case Some(subId) =>
            env.plan.api.payPal.captureSubscription(PayPalOrderId(orderId), subId, me, ctx.ip)
        }) inject jsonOkResult
      }(rateLimitedFu)
    }

}
