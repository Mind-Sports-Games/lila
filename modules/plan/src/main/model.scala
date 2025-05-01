package lila.plan

import org.joda.time.DateTime
import play.api.libs.json.{ JsArray, JsObject }

import lila.user.User

case class Source(value: String) extends AnyVal

sealed abstract class Freq(val renew: Boolean)
object Freq {
  case object Monthly extends Freq(renew = true)
  case object Onetime extends Freq(renew = false)
}

case class Usd(value: BigDecimal) extends AnyVal with Ordered[Usd] {
  def compare(other: Usd) = value compare other.value
  def cents               = Cents((value * 100).toInt)
  override def toString   = s"$$$value"
}
object Usd {
  def apply(value: Int): Usd = Usd(BigDecimal(value))
}
case class Cents(value: Int) extends AnyVal with Ordered[Cents] {
  def compare(other: Cents) = Integer.compare(value, other.value)
  def usd                   = Usd(BigDecimal(value, 2))
  override def toString     = usd.toString
}

object Cents {
  val lifetime = Cents(25000)
}

case class Country(code: String) extends AnyVal

case class StripeChargeId(value: String)       extends AnyVal
case class StripeCustomerId(value: String)     extends AnyVal
case class StripeSessionId(value: String)      extends AnyVal
case class StripeSubscriptionId(value: String) extends AnyVal

case class StripeSubscriptions(data: List[StripeSubscription])

case class StripeProducts(monthly: String, onetime: String)

case class StripeItem(id: String, price: StripePrice)

case class StripePrice(product: String, unit_amount: Cents) {
  def cents = unit_amount
  def usd   = cents.usd
}
object StripePrice {
  val defaultAmounts = List(5, 10, 20, 50).map(Usd.apply).map(_.cents)
}

case class NextUrls(cancel: String, success: String)

case class ProductIds(monthly: String, onetime: String)

case class StripeSession(id: StripeSessionId)
case class CreateStripeSession(
    customerId: StripeCustomerId,
    checkout: PlanCheckout,
    urls: NextUrls,
    isLifetime: Boolean
)

case class StripeSubscription(
    id: String,
    item: StripeItem,
    customer: StripeCustomerId,
    cancel_at_period_end: Boolean,
    status: String,
    default_payment_method: Option[String]
) {
  def renew    = !cancel_at_period_end
  def isActive = status == "active"
}

case class StripeCustomer(
    id: StripeCustomerId,
    email: Option[String],
    subscriptions: StripeSubscriptions
) {

  def firstSubscription = subscriptions.data.headOption
  def renew             = firstSubscription ?? (_.renew)
}

case class StripeCharge(
    id: StripeChargeId,
    amount: Cents,
    customer: StripeCustomerId,
    billing_details: Option[StripeCharge.BillingDetails]
) {
  def lifetimeWorthy = amount >= Cents.lifetime
  def country        = billing_details.flatMap(_.address).flatMap(_.country).map(Country)
}

object StripeCharge {
  case class Address(country: Option[String])
  case class BillingDetails(address: Option[Address])
}

case class StripeInvoice(
    id: Option[String],
    amount_due: Int,
    created: Long,
    paid: Boolean
) {
  def cents    = Cents(amount_due)
  def usd      = cents.usd
  def dateTime = new DateTime(created * 1000)
}

case class StripePaymentMethod(card: Option[StripeCard])

case class StripeCard(brand: String, last4: String, exp_year: Int, exp_month: Int)

case class StripeCompletedSession(customer: StripeCustomerId, mode: String) {
  def freq = if (mode == "subscription") Freq.Monthly else Freq.Onetime
}

case class StripeSetupIntent(payment_method: String)

case class StripeSessionWithIntent(setup_intent: StripeSetupIntent)

// payPal model

case class PayPalPrice(product: String, unit_amount: Cents) {
  def cents = unit_amount
  def usd   = cents.usd
}
object PayPalPrice {
  val defaultAmounts = List(5, 10, 20, 50).map(Usd.apply).map(_.cents)
}

case class PayPalOrderId(value: String)        extends AnyVal with StringValue
case class PayPalSubscriptionId(value: String) extends AnyVal with StringValue
case class PayPalOrder(
    id: PayPalOrderId,
    intent: String,
    status: String,
    purchase_units: List[PayPalPurchaseUnit],
    payer: PayPalPayer
) {
  val userId = purchase_units.headOption.flatMap(_.custom_id).??(_.trim) match {
    case s"$userId" => userId.some
    case _          => none
  }
  def isApproved        = status == "APPROVED"
  def isApprovedCapture = isApproved && intent == "CAPTURE"
  def capturedMoney     = isApprovedCapture ?? purchase_units.headOption.map(_.amount.money)
  def country           = payer.address.flatMap(_.country_code)
}
case class PayPalPayment(amount: PayPalPrice)
case class PayPalBillingInfo(last_payment: PayPalPayment, next_billing_time: DateTime)
case class PayPalSubscription(
    id: PayPalSubscriptionId,
    status: String,
    subscriber: PayPalPayer,
    billing_info: PayPalBillingInfo
) {
  def country       = subscriber.address.flatMap(_.country_code)
  def capturedMoney = billing_info.last_payment.amount.money
  def nextChargeAt  = billing_info.next_billing_time
  def isActive      = status == "ACTIVE"
}
case class CreatePayPalOrder(
    checkout: PlanCheckout,
    user: User,
    isLifetime: Boolean
) {
  def makeCustomId = user.id
}
case class PayPalOrderCreated(id: PayPalOrderId)
case class PayPalSubscriptionCreated(id: PayPalSubscriptionId)
case class PayPalPurchaseUnit(amount: PayPalPrice, custom_id: Option[String])
case class PayPalPayerId(value: String) extends AnyVal with StringValue
case class PayPalPayer(payer_id: PayPalPayerId, address: Option[PayPalAddress]) {
  def id = payer_id
}
case class PayPalAddress(country_code: Option[Country])

case class PayPalEventId(value: String) extends AnyVal with StringValue
case class PayPalEvent(id: PayPalEventId, event_type: String, resource_type: String, resource: JsObject) {
  def tpe         = event_type
  def resourceTpe = resource_type
  def resourceId  = resource str "id"
}

case class PayPalPlanId(value: String) extends AnyVal with StringValue
case class PayPalPlan(id: PayPalPlanId, name: String, status: String, billing_cycles: JsArray) {
  def active = status == "ACTIVE"
}
