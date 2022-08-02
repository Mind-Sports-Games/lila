package lila.round

import strategygames.{ Replay, Situation }
import strategygames.format.{ FEN, Forsyth, Uci }
import strategygames.variant.Variant
import play.api.libs.json._

import lila.socket.Step

object StepBuilder {

  private val logger = lila.round.logger.branch("StepBuilder")

  def apply(
      id: String,
      pgnMoves: Vector[String],
      variant: Variant,
      initialFen: FEN
  ): JsArray = {
    Replay.gameMoveWhileValid(variant.gameLogic, pgnMoves, initialFen, variant) match {
      case (init, games, error) =>
        error foreach logChessError(id)
        JsArray {
          val initStep = Step(
            ply = init.turns,
            move = none,
            fen = Forsyth.>>(variant.gameLogic, init),
            check = init.situation.check,
            dests = None,
            drops = None,
            pocketData = init.situation.board.pocketData,
            captLen = init.situation match {
              case Situation.Draughts(situation) => situation.allMovesCaptureLength.some
              case _                             => None
            }
          )
          val moveSteps = games.map { case (g, m) =>
            Step(
              ply = g.turns,
              move = Step.Move(m.uci, m.san).some,
              fen = Forsyth.>>(g.situation.board.variant.gameLogic, g),
              check = g.situation.check,
              dests = None,
              drops = None,
              pocketData = g.situation.board.pocketData,
              captLen = (g.situation, m) match {
                case (Situation.Draughts(situation), Uci.DraughtsWithSan(m)) =>
                  if (situation.ghosts > 0)
                    situation.captureLengthFrom(m.uci.origDest._2)
                  else
                    situation.allMovesCaptureLength.some
                case _ => None
              }
            )
          }
          (initStep :: moveSteps).map(_.toJson)
        }
    }
  }

  private val logChessError = (id: String) =>
    (err: String) => {
      val path = if (id == "synthetic") "analysis" else id
      logger.info(s"https://playstrategy.org/$path ${err.linesIterator.toList.headOption | "?"}")
    }
}
