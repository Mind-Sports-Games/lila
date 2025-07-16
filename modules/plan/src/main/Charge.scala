package lila.plan

import org.joda.time.DateTime

case class Charge(
    _id: String, // random
    userId: Option[String],
    stripe: Option[Charge.Stripe] = none,
    payPal: Option[Charge.PayPalLegacy] = none,
    payPalCheckout: Option[Patron.PayPalCheckout] = none,
    cents: Cents,
    date: DateTime
) {

  def id = _id

  def isPayPalLegacy   = payPal.nonEmpty
  def isPayPalCheckout = payPalCheckout.nonEmpty
  def isStripe         = stripe.nonEmpty

  def serviceName =
    if (isStripe) "stripe"
    else if (isPayPalLegacy) "paypal legacy"
    else if (isPayPalCheckout) "paypal checkout"
    else "???"

  def lifetimeWorthy = cents >= Cents.lifetime

  def copyAsNew = copy(_id = Charge.makeId, date = DateTime.now)
}

object Charge {

  private def makeId = lila.common.ThreadLocalRandom nextString 8

  def make(
      userId: Option[String],
      stripe: Option[Charge.Stripe] = none,
      payPal: Option[Charge.PayPalLegacy] = none,
      payPalCheckout: Option[Patron.PayPalCheckout] = none,
      cents: Cents
  ) =
    Charge(
      _id = makeId,
      userId = userId,
      stripe = stripe,
      payPal = payPal,
      payPalCheckout = payPalCheckout,
      cents = cents,
      date = DateTime.now
    )

  case class Stripe(
      chargeId: StripeChargeId,
      customerId: StripeCustomerId
  )

  case class PayPalLegacy(
      ip: Option[String],
      name: Option[String],
      email: Option[String],
      txnId: Option[String],
      subId: Option[String]
  )
}
