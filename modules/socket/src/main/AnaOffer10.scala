package lila.socket

import cats.data.Validated
import strategygames.format.{ FEN, Forsyth, Uci, UciCharPair }
import strategygames.variant.Variant
import strategygames.{ Game, GameLogic, MoveMetrics }
import play.api.libs.json.JsObject

import lila.tree.Branch

// renju's offer of ten stones for the opponent to pick from, which changes no stone on the board
case class AnaOffer10(
    variant: Variant,
    fen: FEN,
    path: String,
    chapterId: Option[String]
) extends AnaAny {

  private lazy val lib = variant.gameLogic

  def branch: Validated[String, Branch] =
    Game(lib, variant.some, fen.some).offer10(MoveMetrics()).map { case (game, offer10) =>
      val uci     = offer10.toUci
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

object AnaOffer10 {

  def parse(o: JsObject) =
    for {
      d <- o.obj("d")
      gl = GameLogic(d.int("lib").getOrElse(GameLogic.FiveInARow().id))
      v  = Variant.orDefault(gl, ~d.str("variant"))
      fen  <- d.str("fen") map { fen => FEN.apply(gl, fen) }
      path <- d.str("path")
    } yield AnaOffer10(
      variant = v,
      fen = fen,
      path = path,
      chapterId = d.str("ch")
    )
}
