package lila.socket

import cats.data.Validated
import strategygames.format.{ FEN, Forsyth, Uci, UciCharPair }
import strategygames.variant.Variant
import strategygames.opening.FullOpeningDB
import strategygames.{ Game, GameLogic, Move, Pos, PromotableRole, Role, Situation }
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
    promotion: Option[PromotableRole],
    uci: Option[String],
    fullCapture: Option[Boolean] = None
) extends AnaAny {

  private lazy val lib = variant.gameLogic

  private lazy val fullCaptureFields =
    uci.flatMap(m => Uci.Move.apply(lib, variant.gameFamily, m)).flatMap(_.capture)

  private lazy val newGame = Game(lib, variant.some, fen.some)(
    orig = orig,
    dest = dest,
    promotion = promotion,
    finalSquare = fullCaptureFields.isDefined,
    captures = fullCaptureFields,
    partialCaptures = ~fullCapture
  )

  def branch: Validated[String, Branch] =
    newGame.flatMap { case (game, move) =>
      game.pgnMoves.lastOption.toValid("Moved but no last move!").map { san =>
        val uci = Uci(lib, move, lib match {
          case GameLogic.Draughts() => fullCaptureFields.isDefined
          case _                    => false
        })
        val sit     = game.situation
        val movable = sit.playable(false)
        val fen     = Forsyth.>>(lib, game)
        val captLen = (sit, dest) match {
          case (Situation.Draughts(sit), Pos.Draughts(dest)) =>
            if (sit.ghosts > 0) sit.captureLengthFrom(dest)
            else sit.allMovesCaptureLength.some
          case _ => None
        }
        val validMoves: Map[strategygames.draughts.Pos, List[strategygames.draughts.Move]] =
          (sit, dest) match {
            case (Situation.Draughts(sit), Pos.Draughts(dest)) =>
              AnaDests.validMoves(
                Situation.Draughts(sit),
                sit.ghosts > 0 option dest,
                ~fullCapture
              )
            case _ => Map.empty[strategygames.draughts.Pos, List[strategygames.draughts.Move]]
          }
        val truncatedMoves =
          (~fullCapture && ~captLen > 1) option AnaDests.truncateMoves(validMoves)
        Branch(
          id = UciCharPair(lib, uci),
          ply = game.turns,
          plysPerTurn = variant.plysPerTurn,
          move = Uci.WithSan(lib, uci, san),
          fen = fen,
          check = game.situation.check,
          dests = variant match {
            case Variant.Chess(_) => Some(movable ?? game.situation.destinations)
            case Variant.Draughts(variant) => {
              val truncatedDests = truncatedMoves.map {
                _ mapValues { _ flatMap (uci => variant.boardSize.pos.posAt(uci.takeRight(2))) }
              }
              val draughtsDests: Map[strategygames.Pos, List[strategygames.Pos]] =
                truncatedDests
                  .getOrElse(validMoves.view.mapValues { _ map (_.dest) })
                  .to(Map)
                  .map { case (p, m) => (Pos.Draughts(p), m.map(Pos.Draughts)) }
              movable option draughtsDests
            }
          },
          destsUci = lib match {
            case GameLogic.Draughts() => movable ?? truncatedMoves.map(_.values.toList.flatten)
            case _                    => None
          },
          captureLength = movable ?? captLen,
          opening = (game.turns <= 30 && Variant.openingSensibleVariants(lib)(variant)) ?? {
            FullOpeningDB.findByFen(lib, fen)
          },
          drops = if (movable) game.situation.drops else Some(Nil),
          pocketData = game.situation.board.pocketData
        )
      }
    }
}

object AnaMove {

  def parse(o: JsObject): Option[AnaMove] =
    for {
      d   <- o obj "d"
      lib <- d int "lib"
      gl = GameLogic(lib)
      orig <- d.str("orig").flatMap(pos => Pos.fromKey(gl, pos))
      dest <- d.str("dest").flatMap(pos => Pos.fromKey(gl, pos))
      fen  <- d.str("fen").map(fen => FEN.apply(gl, fen))
      path <- d.str("path")
      v = Variant.orDefault(gl, ~d.str("variant"))
    } yield AnaMove(
      orig = orig,
      dest = dest,
      variant = v,
      fen = fen,
      path = path,
      chapterId = d.str("ch"),
      promotion = d.str("promotion").flatMap(p => Role.promotable(gl, v.gameFamily, p)),
      uci = d.str("uci"),
      fullCapture = d.boolean("fullCapture")
    )
}
