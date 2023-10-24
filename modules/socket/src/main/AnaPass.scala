package lila.socket

import cats.data.Validated
import strategygames.format.{ FEN, Forsyth }
import strategygames.go.format.{ Uci, UciCharPair }
import strategygames.opening.FullOpeningDB
import strategygames.{ Game, GameLogic, Pos, Role }
import strategygames.variant.Variant
import play.api.libs.json.JsObject

import lila.tree.Branch

//We don't think AnaPass is used - think this has been ported to lila-ws

case class AnaPass(
    variant: Variant,
    fen: FEN,
    path: String,
    chapterId: Option[String]
) extends AnaAny {

  def branch: Validated[String, Branch] =
    (Game(variant.gameLogic, variant.some, fen.some)) match {
      case (Game.Go(game)) =>
        game.pass() flatMap { case (game, pass) =>
          game.actions.flatten.lastOption toValid "Passed but no last move!" map { san =>
            val uci     = Uci(pass)
            val movable = !game.situation.end
            val fen     = Forsyth.>>(variant.gameLogic, Game.Go(game))
            Branch(
              id = UciCharPair(uci),
              ply = game.plies,
              move = strategygames.format.Uci.GoWithSan(Uci.WithSan(uci, san)),
              fen = fen,
              check = false,
              dests = Some(movable ?? Game.Go(game).situation.destinations),
              opening = Variant.openingSensibleVariants(variant.gameLogic)(variant) ?? FullOpeningDB
                .findByFen(variant.gameLogic, fen),
              drops = if (movable) Game.Go(game).situation.drops else Some(Nil),
              pocketData = Game.Go(game).situation.board.pocketData
            )
          }
        }
      case _ => sys.error("Pass not implemented for games except Go")
    }

}

object AnaPass {

  def parse(o: JsObject) =
    for {
      d <- o obj "d"
      variant = Variant.orDefault(GameLogic.Go(), ~d.str("variant"))
      fen  <- d str "fen" map { fen => FEN.apply(GameLogic.Go(), fen) }
      path <- d str "path"
    } yield AnaPass(
      variant = variant,
      fen = fen,
      path = path,
      chapterId = d str "ch"
    )
}
