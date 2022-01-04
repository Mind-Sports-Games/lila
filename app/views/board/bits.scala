package views.html.board

import strategygames.format.{ FEN, Forsyth }
import strategygames.{ Player => SGPlayer, P2, P1 }
import strategygames.variant.Variant
import strategygames.{ GameLogic, Situation }
import strategygames.draughts.Board

import controllers.routes
import play.api.libs.json.Json

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.game.Pov

object bits {

  sealed abstract class Orientation extends Product

  object Orientation {
    final case object P1 extends Orientation
    final case object P2 extends Orientation
    final case object Left extends Orientation
    final case object Right extends Orientation
  }

  def sgPlayerToOrientation(c: SGPlayer): Orientation =
    c match {
        case P1 => Orientation.P1
        case P2 => Orientation.P2
    }

  private val dataState = attr("data-state")

  private def boardOrientation(variant: Variant, c: SGPlayer): Orientation =
    variant match {
      case Variant.Chess(strategygames.chess.variant.RacingKings)   => Orientation.P1
      case Variant.Chess(strategygames.chess.variant.LinesOfAction) => c match {
          case P1 => Orientation.P1
          case P2 => Orientation.Right
        }
      case _ => sgPlayerToOrientation(c)
    }

  private def boardOrientation(pov: Pov): Orientation = boardOrientation(pov.game.variant, pov.sgPlayer)

  private def boardSize(pov: Pov): Option[Board.BoardSize] = pov.game.variant match {
    case Variant.Draughts(v) => Some(v.boardSize)
    case _ => None
  }
  def mini(pov: Pov): Tag => Tag =
    miniWithOrientation(
      FEN(pov.game.variant.gameLogic, Forsyth.boardAndPlayer(pov.game.variant.gameLogic, pov.game.situation)),
      boardOrientation(pov),
      ~pov.game.lastMoveKeys,
      boardSize(pov),
      pov.game.variant.key
    ) _

  def miniWithOrientation(
    fen: FEN,
    orientation: Orientation = Orientation.P1,
    lastMove: String = "",
    boardSizeOpt: Option[Board.BoardSize],
    variantKey: String = "standard"
  )(tag: Tag): Tag = {
    // TODO: this is an excellent candidate for refactoring.
    val libName = fen match {
      case FEN.Chess(_) => GameLogic.Chess().name
      case FEN.Draughts(_) => GameLogic.Draughts().name
      case FEN.FairySF(_) => GameLogic.FairySF().name
    }
    val orient = orientation.toString().toLowerCase()
    val boardSize = boardSizeOpt.getOrElse(Board.D100)
    val data = if (libName == "Draughts") {
      s"${fen.value}|${boardSize.width}x${boardSize.height}|${orient}|$lastMove"
    } else {
      s"${fen.value},${orient},$lastMove"
    }
    val extra = if (libName == "Draughts") s"is${boardSize.key}" else ""
    tag(
      cls := s"mini-board mini-board--init cg-wrap is2d ${libName.toLowerCase()} variant-${variantKey} ${extra}",
      dataState := data
    )(cgWrapContent)
  }

  def mini(fen: FEN, sgPlayer: SGPlayer = P1, variantKey: String, lastMove: String = "")(tag: Tag): Tag =
    miniWithOrientation(fen, sgPlayerToOrientation(sgPlayer), lastMove, None, variantKey)(tag)

  def miniForVariant(fen: FEN, variant: Variant, sgPlayer: SGPlayer = P1, lastMove: String = "")(tag: Tag): Tag =
    miniWithOrientation(fen, boardOrientation(variant, sgPlayer), lastMove, None, variant.key)(tag)


  def miniSpan(fen: FEN, sgPlayer: SGPlayer = P1, variantKey: String, lastMove: String = "") =
    mini(fen, sgPlayer, variantKey, lastMove)(span)

  private def sitCanCastle(sit: Situation, sgPlayer: SGPlayer, side: strategygames.chess.Side): Boolean =
    sit match {
      case Situation.Chess(sit) => sit canCastle sgPlayer on side
      case _ => false
    }

  def jsData(
      sit: Situation,
      fen: FEN
  )(implicit ctx: Context) =
    Json.obj(
      "fen"     -> fen.value.split(" ").take(4).mkString(" "),
      "baseUrl" -> s"$netBaseUrl${routes.Editor.load("")}",
      "sgPlayer"   -> sit.player.letter.toString,
      "castles" -> Json.obj(
        "K" -> sitCanCastle(sit, P1, strategygames.chess.KingSide),
        "Q" -> sitCanCastle(sit, P1, strategygames.chess.QueenSide),
        "k" -> sitCanCastle(sit, P2, strategygames.chess.KingSide),
        "q" -> sitCanCastle(sit, P2, strategygames.chess.QueenSide)
      ),
      "animation" -> Json.obj("duration" -> ctx.pref.animationMillis),
      "is3d"      -> ctx.pref.is3d,
      "i18n"      -> i18nJsObject(i18nKeyes)
    )

  private val i18nKeyes = List(
    trans.setTheBoard,
    trans.boardEditor,
    trans.startPosition,
    trans.clearBoard,
    trans.flipBoard,
    trans.loadPosition,
    trans.popularOpenings,
    trans.castling,
    trans.whiteCastlingKingside,
    trans.blackCastlingKingside,
    trans.sgPlayerPlays,
    trans.variant,
    trans.continueFromHere,
    trans.playWithTheMachine,
    trans.playWithAFriend,
    trans.analysis,
    trans.toStudy
  ).map(_.key)
}
