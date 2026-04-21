package lila.learn

import play.api.libs.json.*

import lila.common.Json.*

object JSONHandlers {

  implicit private val StageProgressScoreWriter: Writes[StageProgress.Score] =
    intAnyValWriter[StageProgress.Score](_.value)
  implicit val StageProgressWriter: OWrites[StageProgress] = OWrites[StageProgress] { sp =>
    Json.obj("scores" -> sp.scores)
  }

  implicit private val LearnProgressIdWriter: Writes[LearnProgress.Id] =
    stringAnyValWriter[LearnProgress.Id](_.value)
  implicit val LearnProgressWriter: OWrites[LearnProgress] = Json.writes[LearnProgress]
}
