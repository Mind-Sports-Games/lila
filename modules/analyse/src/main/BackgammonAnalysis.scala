package lila.analyse

import org.joda.time.DateTime
import play.api.libs.json.{ JsObject, Json, OWrites, Writes }
import reactivemongo.api.bson.*
import reactivemongo.api.bson.Macros.Annotations.Key

import strategygames.Player as PlayerIndex

import lila.db.dsl.*

// The gnubg-backed mindcube worker runs `analyse match` on the whole
// game and posts the entire result at once, plus, for every decision, every
// candidate play gnubg evaluated.

case class BgCandidate(
    @Key("p") play:            String,
    @Key("e") evaluator:       Int,
    @Key("q") equity:          Int,
    @Key("d") equityDelta:     Option[Int],
    @Key("w") win:             Int,
    @Key("wg") winGammon:      Int,
    @Key("wb") winBackgammon:  Int,
    @Key("lg") loseGammon:     Int,
    @Key("lb") loseBackgammon: Int
)

case class BgMove(
    @Key("u") player:       Int, // 1 | 2
    @Key("k") kind:         Int, // ChequerPlay 0, Dance 1, CubeOffer 2, CubeResponse 3
    @Key("i") dice:         Option[String],
    @Key("l") rollLuck:     Option[Int],
    @Key("a") action:       Option[String], // only when no candidate supplies it
    @Key("ca") cubeAdvice:  Option[String],
    @Key("y") playedIndex:  Option[Int],
    @Key("c") candidates:   List[BgCandidate]
)

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
    _id:        String, // game id or study chapter id
    studyId:    Option[String],
    player1:    String,
    player2:    String,
    evaluators: List[String],
    games:      List[BgGame],
    date:       DateTime,
    fk:         Option[String]
) {
  def id = _id

  private def statsFor(playerIndex: PlayerIndex): List[BgPlayerStats] = {
    val name = playerIndex.fold(player1, player2)
    games.flatMap(_.stats.find(_.player == name))
  }

  /** gnubg's overall error rate (mEMG per decision), averaged over the games of
    * the match when it holds more than one. */
  def errorRateFor(playerIndex: PlayerIndex): Option[Double] = {
    val rates = statsFor(playerIndex).flatMap(_.overallErrorRate)
    rates.nonEmpty.option(rates.sum / rates.size)
  }

  def prFor(playerIndex: PlayerIndex): Option[Double] = errorRateFor(playerIndex).map(er => (er / 2).abs)

  def ratingFor(playerIndex: PlayerIndex): Option[String] =
    errorRateFor(playerIndex).map(BackgammonAnalysis.skillLabel)
}

object BackgammonAnalysis {

  type ID = String

  def skillLabel(errorRate: Double): String = {
    val er = errorRate.abs
    if (er < 5) "Super Grandmaster"
    else if (er < 10) "World Class"
    else if (er < 15) "Expert"
    else if (er < 25) "Advanced"
    else if (er < 35) "Intermediate"
    else if (er < 45) "Casual"
    else "Beginner"
  }

  implicit val candidateHandler: BSONDocumentHandler[BgCandidate]         = Macros.handler
  implicit val moveHandler: BSONDocumentHandler[BgMove]                   = Macros.handler
  implicit val statsHandler: BSONDocumentHandler[BgPlayerStats]           = Macros.handler
  implicit val winnerHandler: BSONDocumentHandler[BgWinner]               = Macros.handler
  implicit val gameHandler: BSONDocumentHandler[BgGame]                   = Macros.handler
  implicit val analysisHandler: BSONDocumentHandler[BackgammonAnalysis]   = Macros.handler

  private def decimal(milli: Int): Double = milli / 1000d

  private def kindName(code: Int): String = code match {
    case 0 => "ChequerPlay"
    case 1 => "Dance"
    case 2 => "CubeOffer"
    case _ => "CubeResponse"
  }

  private def candidateJson(evaluators: List[String], playedIndex: Option[Int])(
      c: BgCandidate,
      i: Int
  ): JsObject =
    Json
      .obj(
        "rank"      -> (i + 1),
        "evaluator" -> evaluators.lift(c.evaluator).getOrElse(""),
        "play"      -> c.play,
        "equity"    -> decimal(c.equity),
        "probabilities" -> Json.obj(
          "win"            -> decimal(c.win),
          "winGammon"      -> decimal(c.winGammon),
          "winBackgammon"  -> decimal(c.winBackgammon),
          "lose"           -> decimal(1000 - c.win),
          "loseGammon"     -> decimal(c.loseGammon),
          "loseBackgammon" -> decimal(c.loseBackgammon)
        ),
        "played" -> playedIndex.contains(i)
      )
      .add("equityDelta" -> c.equityDelta.map(decimal))

  private def moveJson(evaluators: List[String], p1: String, p2: String)(
      m: BgMove,
      i: Int
  ): JsObject = {
    val cands  = m.candidates.zipWithIndex.map(candidateJson(evaluators, m.playedIndex).tupled)
    val played = m.playedIndex.flatMap(m.candidates.lift)
    Json
      .obj(
        "number"     -> (i + 1),
        "player"     -> (if (m.player == 1) p1 else p2),
        "kind"       -> kindName(m.kind),
        "action"     -> (played.map(_.play) orElse m.action getOrElse ""),
        "candidates" -> cands
      )
      .add("dice" -> m.dice)
      .add("bestAction" -> m.candidates.headOption.map(_.play))
      .add("playedEquity" -> played.map(c => decimal(c.equity)))
      .add("bestEquity" -> m.candidates.headOption.map(c => decimal(c.equity)))
      .add("rollLuck" -> m.rollLuck.map(decimal))
      .add("cubeAdvice" -> m.cubeAdvice)
  }
  private val statsBaseWrites: OWrites[BgPlayerStats] = Json.writes[BgPlayerStats]
  implicit val statsWrites: OWrites[BgPlayerStats] = OWrites { s =>
    statsBaseWrites.writes(s) ++ Json.obj("skill" -> s.overallErrorRate.map(skillLabel))
  }
  implicit val winnerWrites: Writes[BgWinner]               = Json.writes[BgWinner]
  private def gameJson(evaluators: List[String], p1: String, p2: String)(g: BgGame): JsObject =
    Json
      .obj(
        "number" -> g.number,
        "stats"  -> g.stats,
        "moves"  -> g.moves.zipWithIndex.map(moveJson(evaluators, p1, p2).tupled)
      )
      .add("winner" -> g.winner)

  implicit val matchWrites: OWrites[BackgammonAnalysis] = OWrites { a =>
    Json.obj(
      "id"      -> a._id,
      "player1" -> a.player1,
      "player2" -> a.player2,
      "games"   -> a.games.map(gameJson(a.evaluators, a.player1, a.player2))
    ) ++ a.studyId.fold(Json.obj())(s => Json.obj("studyId" -> s))
  }
}
