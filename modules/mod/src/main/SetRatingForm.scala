package lila.mod

import play.api.data.*
import play.api.data.Forms.*

import lila.common.Form.cleanNonEmptyText
import lila.rating.{ Glicko, PerfType }

object SetRatingForm {

  case class Data(perfType: PerfType, rating: Int, deviation: Option[Int], reason: String)

  val form = Form(
    mapping(
      "perf" -> text
        .verifying(PerfType.nonPuzzle.map(_.key).contains)
        .transform[PerfType](k => PerfType(k).get, _.key),
      "rating"    -> number(min = Glicko.minRating, max = Glicko.maxRating),
      "deviation" -> optional(number(min = Glicko.minDeviation, max = Glicko.maxDeviation.toInt)),
      "reason"    -> cleanNonEmptyText(maxLength = 200)
    )(Data.apply)(unapply)
  )
}
