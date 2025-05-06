package lila.plan

import play.api.libs.json._
import play.api.libs.functional.syntax._

private[plan] object JsonHandlers {

  implicit val StripeCents: Reads[Cents]    = Reads.of[Int].map(Cents.apply)
  implicit val CountryReads: Reads[Country] = Reads.of[String].map(Country)

  object stripe {
    implicit val SubscriptionIdReads: Reads[StripeSubscriptionId] = Reads.of[String].map(StripeSubscriptionId)
    implicit val SessionIdReads: Reads[StripeSessionId]           = Reads.of[String].map(StripeSessionId)
    implicit val CustomerIdReads: Reads[StripeCustomerId]         = Reads.of[String].map(StripeCustomerId)
    implicit val ChargeIdReads: Reads[StripeChargeId]             = Reads.of[String].map(StripeChargeId)
    implicit val PriceReads: Reads[StripePrice]                   = Json.reads[StripePrice]
    implicit val ItemReads: Reads[StripeItem]                     = Json.reads[StripeItem]
    // require that the items array is not empty.
    implicit val SubscriptionReads: Reads[StripeSubscription] = (
      (__ \ "id").read[String] and
        (__ \ "items" \ "data" \ 0).read[StripeItem] and
        (__ \ "customer").read[StripeCustomerId] and
        (__ \ "cancel_at_period_end").read[Boolean] and
        (__ \ "status").read[String] and
        (__ \ "default_payment_method").readNullable[String]
    )(StripeSubscription.apply _)
    implicit val SubscriptionsReads: Reads[StripeSubscriptions]         = Json.reads[StripeSubscriptions]
    implicit val CustomerReads: Reads[StripeCustomer]                   = Json.reads[StripeCustomer]
    implicit val AddressReads: Reads[StripeCharge.Address]              = Json.reads[StripeCharge.Address]
    implicit val BillingReads: Reads[StripeCharge.BillingDetails]       = Json.reads[StripeCharge.BillingDetails]
    implicit val ChargeReads: Reads[StripeCharge]                       = Json.reads[StripeCharge]
    implicit val InvoiceReads: Reads[StripeInvoice]                     = Json.reads[StripeInvoice]
    implicit val SessionReads: Reads[StripeSession]                     = Json.reads[StripeSession]
    implicit val SessionCompletedReads: Reads[StripeCompletedSession]   = Json.reads[StripeCompletedSession]
    implicit val CardReads: Reads[StripeCard]                           = Json.reads[StripeCard]
    implicit val PaymentMethodReads: Reads[StripePaymentMethod]         = Json.reads[StripePaymentMethod]
    implicit val SetupIntentReads: Reads[StripeSetupIntent]             = Json.reads[StripeSetupIntent]
    implicit val SessionWithIntentReads: Reads[StripeSessionWithIntent] = Json.reads[StripeSessionWithIntent]
  }

  object payPal {
    import play.api.libs.json.JodaReads._
    implicit val PayerIdReads: Reads[PayPalPayerId]               = Reads.of[String].map(PayPalPayerId)
    implicit val OrderIdReads: Reads[PayPalOrderId]               = Reads.of[String].map(PayPalOrderId)
    implicit val SubscriptionIdReads: Reads[PayPalSubscriptionId] = Reads.of[String].map(PayPalSubscriptionId)
    implicit val EventIdReads: Reads[PayPalEventId]               = Reads.of[String].map(PayPalEventId)
    implicit val PlanIdReads: Reads[PayPalPlanId]                 = Reads.of[String].map(PayPalPlanId)
    implicit val OrderCreatedReads: Reads[PayPalOrderCreated]     = Json.reads[PayPalOrderCreated]
    implicit val SubscriptionCreatedReads: Reads[PayPalSubscriptionCreated] =
      Json.reads[PayPalSubscriptionCreated]
    implicit val AmountReads: Reads[PayPalPrice]              = Json.reads[PayPalPrice]
    implicit val PurchaseUnitReads: Reads[PayPalPurchaseUnit] = Json.reads[PayPalPurchaseUnit]
    implicit val AddressReads: Reads[PayPalAddress]           = Json.reads[PayPalAddress]
    implicit val PayerReads: Reads[PayPalPayer]               = Json.reads[PayPalPayer]
    implicit val OrderReads: Reads[PayPalOrder]               = Json.reads[PayPalOrder]
    implicit val PaymentReads: Reads[PayPalPayment]           = Json.reads[PayPalPayment]
    implicit val BillingInfoReads: Reads[PayPalBillingInfo]   = Json.reads[PayPalBillingInfo]
    implicit val SubscriptionReads: Reads[PayPalSubscription] = Json.reads[PayPalSubscription]
    implicit val EventReads: Reads[PayPalEvent]               = Json.reads[PayPalEvent]
    implicit val PlanReads: Reads[PayPalPlan]                 = Json.reads[PayPalPlan]
  }
}
