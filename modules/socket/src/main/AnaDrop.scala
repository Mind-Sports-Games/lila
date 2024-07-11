package lila.socket

import cats.data.Validated
import strategygames.format.{ FEN, Forsyth, Uci, UciCharPair }
import strategygames.opening.FullOpeningDB
import strategygames.{ Game, GameLogic, Pos, Role, Situation }
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

  private lazy val lib = variant.gameLogic

  private lazy val newGame = Game(lib, variant.some, fen.some).drop(role, pos)

  def branch: Validated[String, Branch] =
    newGame.flatMap { case (game, drop) =>
      game.actionStrs.flatten.lastOption toValid "Dropped but no last move!" map { san =>
        val uci     = Uci(lib, drop)
        val movable = !game.situation.end
        val fen     = Forsyth.>>(variant.gameLogic, game)
        Branch(
          id = UciCharPair(lib, uci),
          ply = game.plies,
          turnCount = game.turnCount,
          variant = variant,
          move = Uci.WithSan(lib, uci, san),
          fen = fen,
          check = game.situation.check,
          dests = Some(movable ?? game.situation.destinations),
          opening = Variant.openingSensibleVariants(variant.gameLogic)(variant) ?? FullOpeningDB
            .findByFen(variant.gameLogic, fen),
          dropsByRole = game.situation.dropsByRole,
          pocketData = game.situation.board.pocketData
        )
      }
    }

}

object AnaDrop {

  private def dataGameLogic(d: JsObject): GameLogic =
    GameLogic(d int "lib" getOrElse 0)

  def parse(o: JsObject) =
    for {
      d <- o obj "d"
      gl      = dataGameLogic(d)
      variant = Variant.orDefault(gl, ~d.str("variant"))
      role <- d.str("role").flatMap(Role.allByGroundName(gl, variant.gameFamily).get)
      pos  <- d.str("pos").flatMap(pos => Pos.fromKey(gl, pos))
      fen  <- d.str("fen").map(fen => FEN.apply(gl, fen))
      path <- d.str("path")
    } yield AnaDrop(
      role = role,
      pos = pos,
      variant = variant,
      fen = fen,
      path = path,
      chapterId = d str "ch"
    )
}
