package lila.socket

import cats.data.Validated
import strategygames.format.{ FEN, Forsyth, Uci, UciCharPair }
import strategygames.variant.Variant
import strategygames.{ Game, GameLogic, MoveMetrics, Pos }
import play.api.libs.json.JsObject

import lila.tree.Branch

case class AnaSelectSquares(
    variant: Variant,
    fen: FEN,
    path: String,
    chapterId: Option[String],
    squares: List[Pos]
) extends AnaAny {

  private lazy val lib = variant.gameLogic

  def branch: Validated[String, Branch] =
    Game(lib, variant.some, fen.some).selectSquares(squares, MoveMetrics()).map { case (game, ss) =>
      val uci     = Uci(lib, ss)
      val sit     = game.situation
      val movable = sit.playable(false)
      val newFen  = Forsyth.>>(lib, game)
      Branch(
        id = UciCharPair(lib, uci),
        ply = game.plies,
        turnCount = game.turnCount,
        playedPlayerIndex = if (game.board.history.currentTurn.nonEmpty) game.player else !game.player,
        variant = variant,
        move = Uci.WithSan(lib, uci, uci.uci),
        fen = newFen,
        check = sit.check,
        dests = Some(movable so sit.destinations),
        drops = if (movable) sit.drops else Some(Nil),
        dropsByRole = sit.dropsByRole,
        pocketData = sit.board.pocketData
      )
    }
}

object AnaSelectSquares {

  def parse(o: JsObject) =
    for {
      d <- o.obj("d")
      gl = GameLogic(d.int("lib").getOrElse(GameLogic.FiveInARow().id))
      v  = Variant.orDefault(gl, ~d.str("variant"))
      fen     <- d.str("fen") map { fen => FEN.apply(gl, fen) }
      path    <- d.str("path")
      squares <- d.str("sq") map { _.split(",").toList.flatMap(Pos.fromKey(gl, _)) }
    } yield AnaSelectSquares(
      variant = v,
      fen = fen,
      path = path,
      chapterId = d.str("ch"),
      squares = squares
    )
}
