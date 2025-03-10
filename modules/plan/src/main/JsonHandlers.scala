package lila.plan

import play.api.libs.json._
import play.api.libs.functional.syntax._

private[plan] object JsonHandlers {

  implicit val StripeSubscriptionId: Reads[SubscriptionId] = Reads.of[String].map(SubscriptionId.apply)
  implicit val StripeClientId: Reads[ClientId]             = Reads.of[String].map(ClientId.apply)
  implicit val StripeSessionId: Reads[SessionId]           = Reads.of[String].map(SessionId.apply)
  implicit val StripeCustomerId: Reads[CustomerId]         = Reads.of[String].map(CustomerId.apply)
  implicit val StripeChargeId: Reads[ChargeId]             = Reads.of[String].map(ChargeId.apply)
  implicit val StripeCents: Reads[Cents]                   = Reads.of[Int].map(Cents.apply)
  implicit val StripePriceReads: Reads[StripePrice]        = Json.reads[StripePrice]
  implicit val StripeItemReads: Reads[StripeItem]          = Json.reads[StripeItem]
  // require that the items array is not empty.
  implicit val StripeSubscriptionReads: Reads[StripeSubscription] = (
    (__ \ "id").read[String] and
      (__ \ "items" \ "data" \ 0).read[StripeItem] and
      (__ \ "customer").read[CustomerId] and
      (__ \ "cancel_at_period_end").read[Boolean] and
      (__ \ "status").read[String] and
      (__ \ "default_payment_method").readNullable[String]
  )(StripeSubscription.apply _)
  implicit val StripeSubscriptionsReads: Reads[StripeSubscriptions] = Json.reads[StripeSubscriptions]
  implicit val StripeCustomerReads: Reads[StripeCustomer]           = Json.reads[StripeCustomer]
  implicit val StripeAddressReads: Reads[StripeCharge.Address]      = Json.reads[StripeCharge.Address]
  implicit val StripeBillingReads: Reads[StripeCharge.BillingDetails] =
    Json.reads[StripeCharge.BillingDetails]
  implicit val StripeChargeReads: Reads[StripeCharge]                     = Json.reads[StripeCharge]
  implicit val StripeInvoiceReads: Reads[StripeInvoice]                   = Json.reads[StripeInvoice]
  implicit val StripeSessionReads: Reads[StripeSession]                   = Json.reads[StripeSession]
  implicit val StripeSessionCompletedReads: Reads[StripeCompletedSession] = Json.reads[StripeCompletedSession]
  implicit val StripeCardReads: Reads[StripeCard]                         = Json.reads[StripeCard]
  implicit val StripePaymentMethodReads: Reads[StripePaymentMethod]       = Json.reads[StripePaymentMethod]
  implicit val StripeSetupIntentReads: Reads[StripeSetupIntent]           = Json.reads[StripeSetupIntent]
  implicit val StripeSessionWithIntentReads: Reads[StripeSessionWithIntent] =
    Json.reads[StripeSessionWithIntent]
}
