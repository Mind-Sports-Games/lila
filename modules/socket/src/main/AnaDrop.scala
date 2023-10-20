package lila.socket

import cats.data.Validated
import strategygames.format.{ FEN, Forsyth }
import strategygames.chess.format.{ Uci, UciCharPair }
import strategygames.opening.FullOpeningDB
import strategygames.{ Game, GameLogic, Pos, Role }
import strategygames.variant.Variant
import play.api.libs.json.JsObject

import lila.tree.Branch

//We don't think AnaMove is used - think this has been ported to lila-ws

case class AnaDrop(
    role: Role,
    pos: Pos,
    variant: Variant,
    fen: FEN,
    path: String,
    chapterId: Option[String]
) extends AnaAny {

  def branch: Validated[String, Branch] =
    (Game(variant.gameLogic, variant.some, fen.some), role, pos) match {
      case (Game.Chess(game), Role.ChessRole(role), Pos.Chess(pos)) =>
        game.drop(role, pos) flatMap { case (game, drop) =>
          game.actions.flatten.lastOption toValid "Dropped but no last move!" map { san =>
            val uci     = Uci(drop)
            val movable = !game.situation.end
            val fen     = Forsyth.>>(variant.gameLogic, Game.Chess(game))
            Branch(
              id = UciCharPair(uci),
              ply = game.plies,
              move = strategygames.format.Uci.ChessWithSan(Uci.WithSan(uci, san)),
              fen = fen,
              check = game.situation.check,
              dests = Some(movable ?? Game.Chess(game).situation.destinations),
              opening = Variant.openingSensibleVariants(variant.gameLogic)(variant) ?? FullOpeningDB
                .findByFen(variant.gameLogic, fen),
              drops = if (movable) Game.Chess(game).situation.drops else Some(Nil),
              pocketData = Game.Chess(game).situation.board.pocketData
            )
          }
        }
      case _ => sys.error("Drop not implemented for games except chess")
    }

}

object AnaDrop {

  def parse(o: JsObject) =
    for {
      d <- o obj "d"
      variant = Variant.orDefault(GameLogic.Chess(), ~d.str("variant"))
      role <- d str "role" flatMap Role.allByName(GameLogic.Chess(), variant.gameFamily).get
      pos  <- d str "pos" flatMap { pos => Pos.fromKey(GameLogic.Chess(), pos) }
      fen  <- d str "fen" map { fen => FEN.apply(GameLogic.Chess(), fen) }
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
