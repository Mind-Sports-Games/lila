package lila.plan

import play.api.data._
import play.api.data.Forms._

case class PlanCheckout(
    email: Option[String],
    amount: Cents,
    freq: Freq
) {

  def cents = amount

  def toFormData =
    Some(
      (email, amount.usd.value, freq.toString.toLowerCase)
    )
}

object PlanCheckout {

  def amountField = bigDecimal(precision = 10, scale = 2)
    .verifying(_ >= 1)
    .verifying(_ <= 10000)

  def make(
      email: Option[String],
      amount: BigDecimal,
      freq: String
  ) =
    PlanCheckout(
      email,
      Usd(amount).cents,
      if (freq == "monthly") Freq.Monthly else Freq.Onetime
    )

  val form = Form[PlanCheckout](
    mapping(
      "email"  -> optional(email),
      "amount" -> PlanCheckout.amountField,
      "freq"   -> nonEmptyText
    )(PlanCheckout.make)(_.toFormData)
  )
}

case class Switch(amount: BigDecimal) {

  def cents = Usd(amount).cents
}

object Switch {

  val form = Form(
    mapping(
      "amount" -> bigDecimal(precision = 10, scale = 2)
        .verifying(_ >= 1)
        .verifying(_ <= 10000)
    )(Switch.apply)(Switch.unapply)
  )
}
