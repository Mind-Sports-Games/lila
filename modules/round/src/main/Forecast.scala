package lila.round

import org.joda.time.DateTime
import play.api.libs.json._

import strategygames.format.Uci
import strategygames.{ GameFamily, GameLogic, Move }
import lila.common.Json.jodaWrites
import lila.game.Game

case class Forecast(
    _id: String, // player full id
    steps: Forecast.Steps,
    date: DateTime
) {

  def apply(g: Game, lastMove: Move): Option[(Forecast, Uci.Move)] =
    nextMove(g, lastMove) map { move =>
      copy(
        steps = steps.collect {
          case fst :: snd :: rest
              if rest.nonEmpty && g.plies == fst.ply && fst.is(lastMove) && snd.is(move) =>
            rest
        },
        date = DateTime.now
      ) -> move
    }

  // accept up to 30 lines of 30 moves each
  def truncate = copy(steps = steps.take(30).map(_ take 30))

  private def nextMove(g: Game, last: Move) =
    steps.foldLeft(none[Uci.Move]) {
      case (None, fst :: snd :: _) if g.plies == fst.ply && fst.is(last) => snd.uciMove
      case (move, _)                                                     => move
    }

  def moveOpponent(g: Game, lastMove: Move): Option[(Forecast, Uci.Move)] =
    nextMoveOpponent(g, lastMove) map { move =>
      copy(
        steps = steps.collect {
          case (fst :: snd :: rest)
              if rest.nonEmpty && g.plies == fst.ply && fst.is(lastMove.toShortUci) && snd.is(move) =>
            snd :: rest
        },
        date = DateTime.now
      ) -> lastMove.toShortUci
    }

  private def nextMoveOpponent(g: Game, last: Move) =
    steps.foldLeft(none[Uci.Move]) {
      case (None, fst :: snd :: _) if g.plies == fst.ply && fst.is(last.toShortUci) => snd.uciMove
      case (move, _)                                                                => move
    }

}

object Forecast {

  type Steps = List[List[Step]]

  def maxPlies(steps: Steps): Int = steps.foldLeft(0)(_ max _.size)

  case class Step(
      gf: Int,
      ply: Int,
      uci: String,
      san: String,
      fen: String,
      check: Option[Boolean]
  ) {

    def is(move: Move)     = move.toUci.uci == uci
    def is(move: Uci.Move) = move.uci == uci

    val gameFamily = GameFamily(gf)

    def uciMove = Uci.Move(gameFamily.gameLogic, gameFamily, uci)
  }

  implicit val forecastStepJsonFormat: OFormat[Step] = Json.format[Step]

  implicit val forecastJsonWriter: OWrites[Forecast] = Json.writes[Forecast]

  case object OutOfSync extends lila.base.LilaException {
    val message = "Forecast out of sync"
  }
}
