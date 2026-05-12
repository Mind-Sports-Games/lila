package lila.socket

import cats.data.Validated
import strategygames.format.{ FEN, Forsyth, Uci, UciCharPair }
import strategygames.variant.Variant
import strategygames.{ Game, GameLogic, MoveMetrics }
import play.api.libs.json.JsObject

import lila.tree.Branch

case class AnaEndTurn(
    variant: Variant,
    fen: FEN,
    path: String,
    chapterId: Option[String]
) extends AnaAny {

  private lazy val lib = variant.gameLogic

  def branch: Validated[String, Branch] =
    Game(lib, variant.some, fen.some)
      .endTurn(MoveMetrics())
      .andThen { case (game, endTurn) =>
        game.actionStrs.flatten.lastOption toValid "EndTurn but no last action!" map { lastAction =>
          val gameRecordNotation =
            strategygames.format.sgf.Dumper(variant, Vector(Vector(lastAction)))
          val uci     = Uci(lib, endTurn)
          val sit     = game.situation
          val movable = sit.playable(false)
          val newFen  = Forsyth.>>(lib, game)
          Branch(
            id = UciCharPair(lib, uci),
            ply = game.plies,
            turnCount = game.turnCount,
            playedPlayerIndex = if (game.board.history.currentTurn.nonEmpty) game.player else !game.player,
            variant = variant,
            move = Uci.WithSan(lib, uci, gameRecordNotation),
            fen = newFen,
            check = sit.check,
            dests = Some(movable so sit.destinations),
            drops = if (movable) sit.drops else Some(Nil),
            dropsByRole = sit.dropsByRole,
            lifts = if (movable) Some(sit.lifts.map(_.pos)) else Some(Nil),
            pocketData = sit.board.pocketData
          )
        }
      }
}

object AnaEndTurn {

  private def dataGameLogic(d: JsObject): GameLogic =
    GameLogic(d.int("lib").getOrElse(0))

  def parse(o: JsObject): Option[AnaEndTurn] =
    for {
      d <- o.obj("d")
      gl = dataGameLogic(d)
      v  = Variant.orDefault(gl, ~d.str("variant"))
      fen  <- d.str("fen").map { fen => FEN.apply(gl, fen) }
      path <- d.str("path")
    } yield AnaEndTurn(
      variant = v,
      fen = fen,
      path = path,
      chapterId = d.str("ch")
    )
}
