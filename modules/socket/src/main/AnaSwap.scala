package lila.socket

import cats.data.Validated
import strategygames.format.{ FEN, Forsyth, Uci, UciCharPair }
import strategygames.variant.Variant
import strategygames.{ Game, GameLogic, MoveMetrics }
import play.api.libs.json.JsObject

import lila.tree.Branch

// five in a row's swap and swap2, which change no stone on the board
case class AnaSwap(
    variant: Variant,
    fen: FEN,
    path: String,
    chapterId: Option[String],
    swap2: Boolean
) extends AnaAny {

  private lazy val lib = variant.gameLogic

  private def play(game: Game): Validated[String, (Game, Uci)] =
    if (swap2) game.swap2(MoveMetrics()).map { case (g, s) => (g, s.toUci) }
    else game.swap(MoveMetrics()).map { case (g, s) => (g, s.toUci) }

  def branch: Validated[String, Branch] =
    play(Game(lib, variant.some, fen.some)).map { case (game, uci) =>
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

object AnaSwap {

  def parse(o: JsObject, swap2: Boolean): Option[AnaSwap] =
    for {
      d <- o.obj("d")
      gl = GameLogic(d.int("lib").getOrElse(GameLogic.FiveInARow().id))
      v  = Variant.orDefault(gl, ~d.str("variant"))
      fen  <- d.str("fen").map { fen => FEN.apply(gl, fen) }
      path <- d.str("path")
    } yield AnaSwap(
      variant = v,
      fen = fen,
      path = path,
      chapterId = d.str("ch"),
      swap2 = swap2
    )
}
