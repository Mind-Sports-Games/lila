package lila.challenge

import play.api.data.*
import play.api.data.Forms.*

final class ChallengeForm {

  val decline = Form(
    mapping(
      "reason" -> optional(nonEmptyText)
    )(DeclineData.apply)(d => Some(d.reason))
  )

  case class DeclineData(reason: Option[String]) {

    def realReason = reason.fold(Challenge.DeclineReason.default)(Challenge.DeclineReason.apply)
  }
}
