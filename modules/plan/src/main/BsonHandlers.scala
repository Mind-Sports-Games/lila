package lila.plan

import lila.db.dsl._
import reactivemongo.api.bson._

private[plan] object BsonHandlers {

  implicit val CentsBSONHandler: BSONHandler[Cents] = intAnyValHandler[Cents](_.value, Cents.apply)

  implicit val StripeChargeIdBSONHandler: BSONHandler[StripeChargeId] =
    stringAnyValHandler[StripeChargeId](_.value, StripeChargeId.apply)
  implicit val StripeCustomerIdBSONHandler: BSONHandler[StripeCustomerId] =
    stringAnyValHandler[StripeCustomerId](_.value, StripeCustomerId.apply)

  implicit val PayPalOrderIdBSONHandler: BSONHandler[PayPalOrderId] =
    stringAnyValHandler[PayPalOrderId](_.value, PayPalOrderId.apply)
  implicit val PayPalPayerIdBSONHandler: BSONHandler[PayPalPayerId] =
    stringAnyValHandler[PayPalPayerId](_.value, PayPalPayerId.apply)
  implicit val PayPalSubIdBSONHandler: BSONHandler[PayPalSubscriptionId] =
    stringAnyValHandler[PayPalSubscriptionId](_.value, PayPalSubscriptionId.apply)

  object PatronHandlers {
    import Patron._
    implicit val PayPalEmailBSONHandler: BSONHandler[PayPalLegacy.Email] =
      stringAnyValHandler[PayPalLegacy.Email](_.value, PayPalLegacy.Email.apply)
    implicit val PayPalLegacySubIdBSONHandler: BSONHandler[PayPalLegacy.SubId] =
      stringAnyValHandler[PayPalLegacy.SubId](_.value, PayPalLegacy.SubId.apply)
    implicit val PayPalLegacyBSONHandler: BSONDocumentHandler[PayPalLegacy] = Macros.handler[PayPalLegacy]
    implicit val PayPalCheckoutBSONHandler: BSONDocumentHandler[PayPalCheckout] =
      Macros.handler[PayPalCheckout]
    implicit val StripeBSONHandler: BSONDocumentHandler[Stripe] = Macros.handler[Stripe]
    implicit val FreeBSONHandler: BSONDocumentHandler[Free]     = Macros.handler[Free]
    implicit val UserIdBSONHandler: BSONHandler[UserId]         = stringAnyValHandler[UserId](_.value, UserId.apply)
    implicit val PatronBSONHandler: BSONDocumentHandler[Patron] = Macros.handler[Patron]
  }

  object ChargeHandlers {
    import Charge._
    import PatronHandlers.PayPalCheckoutBSONHandler
    implicit val StripeBSONHandler: BSONDocumentHandler[Stripe]             = Macros.handler[Stripe]
    implicit val PayPalLegacyBSONHandler: BSONDocumentHandler[PayPalLegacy] = Macros.handler[PayPalLegacy]
    implicit val ChargeBSONHandler: BSONDocumentHandler[Charge]             = Macros.handler[Charge]
  }
}
