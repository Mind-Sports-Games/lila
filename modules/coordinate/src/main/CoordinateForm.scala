package lila.coordinate

import play.api.data._
import play.api.data.Forms._

object CoordinateForm {

  val sgPlayer = Form(
    single(
      "sgPlayer" -> number(min = 1, max = 3)
    )
  )

  val score = Form(
    mapping(
      "sgPlayer" -> text.verifying(Set("p1", "p2") contains _),
      "score" -> number(min = 0, max = 100)
    )(ScoreData.apply)(ScoreData.unapply)
  )

  case class ScoreData(sgPlayer: String, score: Int) {

    def isP1 = sgPlayer == "p1"
  }
}
