package lila.analyse

import org.joda.time.DateTime
import play.api.libs.json.{ Json, OWrites, Writes }
import reactivemongo.api.bson.*

import lila.db.dsl.*

// Whole-game backgammon analysis, stored alongside (not inside) the chess
// `Analysis`. The gnubg-backed mindcube worker runs `analyse match` on the whole
// game and posts the entire result at once: gnubg's OWN per-player error rate,
// luck and ratings (no client-side computation), plus, for every decision, every
// candidate play gnubg evaluated. Mirrors mindcube's MatchAnalysis 1:1.

case class BgProbabilities(
    win:            Double,
    winGammon:      Double,
    winBackgammon:  Double,
    lose:           Double,
    loseGammon:     Double,
    loseBackgammon: Double
)

/** One ranked candidate play gnubg evaluated for a decision. `played` marks the
  * play actually chosen (gnubg prefixes its rank line with `*`). */
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

/** One decision (chequer play, dance, cube offer/response). `action` is what was
  * played, `bestAction` gnubg's best; the equities and `candidates` are gnubg's.
  */
case class BgMove(
    number:       Int,
    player:       String,
    kind:         String,          // ChequerPlay | Dance | CubeOffer | CubeResponse
    dice:         Option[String],
    action:       String,
    bestAction:   Option[String],
    playedEquity: Option[Double],
    bestEquity:   Option[Double],
    rollLuck:     Option[Double],
    cubeAdvice:   Option[String], // gnubg's "Proper cube action" text (cube decisions only)
    candidates:   List[BgCandidate]
)

/** Per-player statistics — every value a DIRECT gnubg output (error rates in
  * mEMG, luck in EMG, and gnubg's rating words). */
case class BgPlayerStats(
    player:           String,
    chequerErrorRate: Option[Double],
    cubeErrorRate:    Option[Double],
    overallErrorRate: Option[Double],
    snowieErrorRate:  Option[Double],
    luckTotalEmg:     Option[Double],
    luckRateEmg:      Option[Double],
    chequerRating:    Option[String],
    cubeRating:       Option[String],
    overallRating:    Option[String],
    luckRating:       Option[String]
)

case class BgWinner(player: String, points: Int, winType: String)

case class BgGame(
    number: Int,
    winner: Option[BgWinner],
    stats:  List[BgPlayerStats],
    moves:  List[BgMove]
)

case class BackgammonAnalysis(
    _id:     String, // game id, or study chapter id when studyId is set
    studyId: Option[String],
    player1: String,
    player2: String,
    games:   List[BgGame],
    date:    DateTime,
    fk:      Option[String]
) {
  def id = _id
}

object BackgammonAnalysis {

  type ID = String

  // ── persistence (BSON) ────────────────────────────────────────────────────
  implicit val probabilitiesHandler: BSONDocumentHandler[BgProbabilities] = Macros.handler
  implicit val candidateHandler: BSONDocumentHandler[BgCandidate]         = Macros.handler
  implicit val moveHandler: BSONDocumentHandler[BgMove]                   = Macros.handler
  implicit val statsHandler: BSONDocumentHandler[BgPlayerStats]           = Macros.handler
  implicit val winnerHandler: BSONDocumentHandler[BgWinner]               = Macros.handler
  implicit val gameHandler: BSONDocumentHandler[BgGame]                   = Macros.handler
  implicit val analysisHandler: BSONDocumentHandler[BackgammonAnalysis]   = Macros.handler

  // ── read endpoint (play-json) ─────────────────────────────────────────────
  implicit val probabilitiesWrites: Writes[BgProbabilities] = Json.writes[BgProbabilities]
  implicit val candidateWrites: Writes[BgCandidate]         = Json.writes[BgCandidate]
  implicit val moveWrites: Writes[BgMove]                   = Json.writes[BgMove]
  implicit val statsWrites: Writes[BgPlayerStats]           = Json.writes[BgPlayerStats]
  implicit val winnerWrites: Writes[BgWinner]               = Json.writes[BgWinner]
  implicit val gameWrites: OWrites[BgGame]                  = Json.writes[BgGame]
  implicit val matchWrites: OWrites[BackgammonAnalysis] = OWrites { a =>
    Json.obj("id" -> a._id, "player1" -> a.player1, "player2" -> a.player2, "games" -> a.games) ++
      a.studyId.fold(Json.obj())(s => Json.obj("studyId" -> s))
  }
}
