package lila.socket

import cats.data.Validated
import strategygames.format.{ FEN, Forsyth, Uci, UciCharPair }
import strategygames.chess.opening._
import strategygames.variant.Variant
import strategygames.{ Game, GameLib, Pos, PromotableRole, Role }
import play.api.libs.json._

import lila.tree.Branch

trait AnaAny {

  def branch: Validated[String, Branch]
  def chapterId: Option[String]
  def path: String
}

case class AnaMove(
    orig: Pos,
    dest: Pos,
    variant: Variant,
    fen: FEN,
    path: String,
    chapterId: Option[String],
    promotion: Option[PromotableRole]
) extends AnaAny {

  def branch: Validated[String, Branch] =
    Game(variant.gameLib, variant.some, fen.some)(orig, dest, promotion) flatMap { case (game, move) =>
      game.pgnMoves.lastOption toValid "Moved but no last move!" map { san =>
        val uci     = Uci(variant.gameLib, move)
        val movable = game.situation playable false
        val fen     = Forsyth.>>(variant.gameLib, game)
        Branch(
          id = UciCharPair(variant.gameLib, uci),
          ply = game.turns,
          move = Uci.WithSan(variant.gameLib, uci, san),
          fen = fen,
          check = game.situation.check,
          dests = Some(movable ?? game.situation.destinations),
          opening = (game.turns <= 30 && Variant.openingSensibleVariants(variant.gameLib)(variant)) ?? {
            fen match {
              case FEN.Chess(fen) => FullOpeningDB findByFen fen
              case _ => sys.error("Invalid fen lib")
            }
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
      orig <- d str "orig" flatMap {pos => Pos.fromKey(GameLib.Chess(), pos)}
      dest <- d str "dest" flatMap {pos => Pos.fromKey(GameLib.Chess(), pos)}
      fen  <- d str "fen" map {fen => FEN.apply(GameLib.Chess(), fen)}
      path <- d str "path"
    } yield AnaMove(
      orig = orig,
      dest = dest,
      variant = Variant.orDefault(GameLib.Chess(), ~d.str("variant")),
      fen = fen,
      path = path,
      chapterId = d str "ch",
      promotion = d str "promotion" flatMap {p => Role.promotable(GameLib.Chess(), p)}
    )
}
