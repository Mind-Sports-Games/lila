package lila.socket

import cats.data.Validated
import strategygames.format.{ FEN, Forsyth, Uci, UciCharPair }
import strategygames.variant.Variant
import strategygames.{ Game, GameLogic, Pos }
import play.api.libs.json.JsObject

import lila.tree.Branch

case class AnaLift(
    pos: Pos,
    variant: Variant,
    fen: FEN,
    path: String,
    chapterId: Option[String]
) extends AnaAny {

  private lazy val lib = variant.gameLogic

  def branch: Validated[String, Branch] =
    Game(lib, variant.some, fen.some)
      .lift(pos, strategygames.MoveMetrics())
      .flatMap { case (game, lift) =>
        game.actionStrs.flatten.lastOption toValid "Lifted but no last action!" map { lastAction =>
          val gameRecordNotation = strategygames.format.sgf.Dumper(variant, Vector(Vector(lastAction)))
          val uci                = Uci(lib, lift)
          val sit                = game.situation
          val movable            = sit playable false
          val newFen             = Forsyth.>>(lib, game)
          Branch(
            id = UciCharPair(lib, uci),
            ply = game.plies,
            turnCount = game.turnCount,
            playedPlayerIndex = if (game.board.history.currentTurn.nonEmpty) game.player else !game.player,
            variant = variant,
            move = Uci.WithSan(lib, uci, gameRecordNotation),
            fen = newFen,
            check = sit.check,
            dests = Some(movable ?? sit.destinations),
            drops = if (movable) sit.drops else Some(Nil),
            dropsByRole = sit.dropsByRole,
            lifts = if (movable) Some(sit.lifts.map(_.pos)) else Some(Nil),
            pocketData = sit.board.pocketData
          )
        }
      }
}

object AnaLift {
  private def dataGameLogic(d: JsObject): GameLogic =
    GameLogic(d int "lib" getOrElse 0)

  def parse(o: JsObject): Option[AnaLift] =
    for {
      d <- o obj "d"
      gl = dataGameLogic(d)
      pos <- d str "pos" flatMap { p => Pos.fromKey(gl, p) }
      fen <- d str "fen" map { fen => FEN.apply(gl, fen) }
      path <- d str "path"
      v = Variant.orDefault(gl, ~d.str("variant"))
    } yield AnaLift(
      pos = pos,
      variant = v,
      fen = fen,
      path = path,
      chapterId = d str "ch"
    )
}
