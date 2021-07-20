package lila.socket

import cats.data.Validated
import strategygames.format.{ FEN, Forsyth }
import strategygames.chess.format.{ Uci, UciCharPair }
import strategygames.chess.opening._
import strategygames.{ Game, GameLib, Pos, Role }
import strategygames.variant.Variant
import play.api.libs.json.JsObject

import lila.tree.Branch

case class AnaDrop(
    role: Role,
    pos: Pos,
    variant: Variant,
    fen: FEN,
    path: String,
    chapterId: Option[String]
) extends AnaAny {

  def branch: Validated[String, Branch] =
    (Game(GameLib.Chess(), variant.some, fen.some), role, pos) match {
      case (Game.Chess(game), Role.ChessRole(role), Pos.Chess(pos))
        => game.drop(role, pos) flatMap {
          case (game, drop)
            => game.pgnMoves.lastOption toValid "Dropped but no last move!" map { san =>
              val uci     = Uci(drop)
              val movable = !game.situation.end
              val fen     = Forsyth.>>(GameLib.Chess(), Game.Chess(game))
              Branch(
                id = UciCharPair(uci),
                ply = game.turns,
                move = strategygames.format.Uci.ChessWithSan(Uci.WithSan(uci, san)),
                fen = fen,
                check = game.situation.check,
                dests = Some(movable ?? Game.Chess(game).situation.destinations),
                opening = Variant.openingSensibleVariants(GameLib.Chess())(variant) ?? {
                  fen match {
                    case FEN.Chess(fen) => FullOpeningDB findByFen fen
                    case _ => sys.error("Invalid fen lib")
                  }
                },
                drops = if (movable) Game.Chess(game).situation.drops else Some(Nil),
                crazyData = game.situation.board.crazyData
              )
            }
        }
      case _ => sys.error("Drop not implemented for games except chess")
    }

}

object AnaDrop {

  def parse(o: JsObject) =
    for {
      d    <- o obj "d"
      role <- d str "role" flatMap Role.allByName(GameLib.Chess()).get
      pos  <- d str "pos" flatMap {pos => Pos.fromKey(GameLib.Chess(), pos)}
      variant = Variant.orDefault(GameLib.Chess(), ~d.str("variant"))
      fen  <- d str "fen" map {fen => FEN.apply(GameLib.Chess(), fen)}
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
