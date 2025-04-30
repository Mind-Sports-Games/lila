package lila.coordinate

import play.api.data._
import play.api.data.Forms._

object CoordinateForm {

  val playerIndex = Form(
    single(
      "playerIndex" -> number(min = 1, max = 3)
    )
  )

  val score = Form(
    mapping(
      "playerIndex" -> text.verifying(Set("p1", "p2") contains _),
      "score"       -> number(min = 0, max = 100)
    )(ScoreData.apply)(ScoreData.unapply)
  )

  case class ScoreData(playerIndex: String, score: Int) {

    def isP1 = playerIndex == "p1"
  }
}
