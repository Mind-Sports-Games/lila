package lila.socket

import cats.data.Validated
import strategygames.chess.format.{ FEN, Uci, UciCharPair }
import strategygames.chess.opening._
import strategygames.chess.variant.Variant
import play.api.libs.json.JsObject

import lila.tree.Branch

case class AnaDrop(
    role: strategygames.chess.Role,
    pos: strategygames.chess.Pos,
    variant: Variant,
    fen: FEN,
    path: String,
    chapterId: Option[String]
) extends AnaAny {

  def branch: Validated[String, Branch] =
    strategygames.chess.Game(variant.some, fen.some).drop(role, pos) flatMap { case (game, drop) =>
      game.pgnMoves.lastOption toValid "Dropped but no last move!" map { san =>
        val uci     = Uci(drop)
        val movable = !game.situation.end
        val fen     = strategygames.chess.format.Forsyth >> game
        Branch(
          id = UciCharPair(uci),
          ply = game.turns,
          move = Uci.WithSan(uci, san),
          fen = fen,
          check = game.situation.check,
          dests = Some(movable ?? game.situation.destinations),
          opening = Variant.openingSensibleVariants(variant) ?? {
            FullOpeningDB findByFen fen
          },
          drops = if (movable) game.situation.drops else Some(Nil),
          crazyData = game.situation.board.crazyData
        )
      }
    }
}

object AnaDrop {

  def parse(o: JsObject) =
    for {
      d    <- o obj "d"
      role <- d str "role" flatMap strategygames.chess.Role.allByName.get
      pos  <- d str "pos" flatMap strategygames.chess.Pos.fromKey
      variant = strategygames.chess.variant.Variant orDefault ~d.str("variant")
      fen  <- d str "fen" map FEN.apply
      path <- d str "path"
    } yield AnaDrop(
      role = role,
      pos = pos,
      variant = variant,
      fen = fen,
      path = path,
      chapterId = d str "ch"
    )
}
