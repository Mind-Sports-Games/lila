package lila.analyse

import org.joda.time.DateTime
import reactivemongo.api.bson.*

import lila.db.dsl.*

// Backgammon analysis, stored alongside (not inside) the chess `Analysis`. The
// chess model packs cp/mate into a compact string; backgammon needs equities and
// win/gammon/backgammon probabilities plus the ranked candidate plays, so it gets
// its own structured documents. Mirrors mindcube's PositionAnalysis / MoveEval /
// Probabilities so the worker's results map across 1:1.

case class BgProbabilities(
    win:            Double,
    winGammon:      Double,
    winBackgammon:  Double,
    lose:           Double,
    loseGammon:     Double,
    loseBackgammon: Double
)

/** One ranked candidate play gnubg evaluated for a decision. */
case class BgCandidate(
    rank:          Int,
    evaluator:     String,         // e.g. "Cubeful 2-ply"
    play:          String,         // e.g. "24/20 8/7*"
    equity:        Double,         // EMG
    equityDelta:   Option[Double], // loss vs. rank 1 (None on rank 1)
    probabilities: BgProbabilities,
    evalClass:     Option[String], // e.g. "world class"
    played:        Boolean
)

/** Analysis of one decision (keyed by `index`, matching the work item's
  * decision index). Overall `equity`/`probabilities` are gnubg's best play. */
case class BackgammonInfo(
    index:         Int,
    kind:          String, // chequer | dance | cube-offer | cube-response
    equity:        Double,
    probabilities: BgProbabilities,
    candidates:    List[BgCandidate]
)

case class BackgammonAnalysis(
    _id:     String, // game id, or study chapter id when studyId is set
    studyId: Option[String],
    infos:   List[BackgammonInfo],
    date:    DateTime,
    fk:      Option[String]
) {

  def id = _id

  /** Merge freshly-posted decisions in, keyed by `index` (latest wins), so the
    * worker can post results progressively. */
  def merge(more: List[BackgammonInfo]): BackgammonAnalysis =
    copy(infos = (infos ++ more).groupBy(_.index).view.mapValues(_.last).values.toList.sortBy(_.index))
}

object BackgammonAnalysis {

  type ID = String

  implicit val probabilitiesHandler: BSONDocumentHandler[BgProbabilities] = Macros.handler
  implicit val candidateHandler: BSONDocumentHandler[BgCandidate]         = Macros.handler
  implicit val infoHandler: BSONDocumentHandler[BackgammonInfo]           = Macros.handler
  implicit val analysisHandler: BSONDocumentHandler[BackgammonAnalysis]   = Macros.handler

  def empty(id: ID, studyId: Option[String], date: DateTime): BackgammonAnalysis =
    BackgammonAnalysis(id, studyId, Nil, date, None)
}
