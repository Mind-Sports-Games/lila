package lila.mod

import play.api.data.*
import play.api.data.Forms.*

import lila.common.Form.cleanNonEmptyText
import lila.rating.{ Glicko, PerfType }

object SetRatingForm {

  // above this Glicko.sanityCheck rejects the rating
  val maxRating = 3999

  case class Data(perf: String, rating: Int, deviation: Option[Int], reason: String) {
    def perfType = PerfType(perf) | PerfType.default
  }

  val form = Form(
    mapping(
      "perf"      -> text.verifying(PerfType.nonPuzzle.map(_.key).contains),
      "rating"    -> number(min = Glicko.minRating, max = maxRating),
      "deviation" -> optional(number(min = Glicko.minDeviation, max = Glicko.maxDeviation.toInt)),
      "reason"    -> cleanNonEmptyText(maxLength = 200)
    )(Data.apply)(unapply)
  )
}
