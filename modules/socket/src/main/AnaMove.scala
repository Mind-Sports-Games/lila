package lila.socket

import cats.data.Validated
import strategygames.chess.format.{ FEN, Uci, UciCharPair }
import strategygames.chess.opening._
import strategygames.chess.variant.Variant
import play.api.libs.json._

import lila.tree.Branch

trait AnaAny {

  def branch: Validated[String, Branch]
  def chapterId: Option[String]
  def path: String
}

case class AnaMove(
    orig: strategygames.chess.Pos,
    dest: strategygames.chess.Pos,
    variant: Variant,
    fen: FEN,
    path: String,
    chapterId: Option[String],
    promotion: Option[strategygames.chess.PromotableRole]
) extends AnaAny {

  def branch: Validated[String, Branch] =
    strategygames.chess.Game(variant.some, fen.some)(orig, dest, promotion) flatMap { case (game, move) =>
      game.pgnMoves.lastOption toValid "Moved but no last move!" map { san =>
        val uci     = Uci(move)
        val movable = game.situation playable false
        val fen     = strategygames.chess.format.Forsyth >> game
        Branch(
          id = UciCharPair(uci),
          ply = game.turns,
          move = Uci.WithSan(uci, san),
          fen = fen,
          check = game.situation.check,
          dests = Some(movable ?? game.situation.destinations),
          opening = (game.turns <= 30 && Variant.openingSensibleVariants(variant)) ?? {
            FullOpeningDB findByFen fen
          },
          drops = if (movable) game.situation.drops else Some(Nil),
          crazyData = game.situation.board.crazyData
        )
      }
    }
}

object AnaMove {

  def parse(o: JsObject) =
    for {
      d    <- o obj "d"
      orig <- d str "orig" flatMap strategygames.chess.Pos.fromKey
      dest <- d str "dest" flatMap strategygames.chess.Pos.fromKey
      fen  <- d str "fen" map FEN.apply
      path <- d str "path"
    } yield AnaMove(
      orig = orig,
      dest = dest,
      variant = strategygames.chess.variant.Variant orDefault ~d.str("variant"),
      fen = fen,
      path = path,
      chapterId = d str "ch",
      promotion = d str "promotion" flatMap strategygames.chess.Role.promotable
    )
}
