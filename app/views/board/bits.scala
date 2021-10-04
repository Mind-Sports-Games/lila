package views.html.board

import strategygames.format.{ FEN, Forsyth }
import strategygames.{ Color, Black, White }
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
    final case object White extends Orientation
    final case object Black extends Orientation
    final case object Left extends Orientation
    final case object Right extends Orientation
  }

  def colorToOrientation(c: Color): Orientation =
    c match {
        case White => Orientation.White
        case Black => Orientation.Black
    }

  private val dataState = attr("data-state")

  private def boardOrientation(variant: Variant, c: Color): Orientation =
    variant match {
      case Variant.Chess(strategygames.chess.variant.RacingKings)   => Orientation.White
      case Variant.Chess(strategygames.chess.variant.LinesOfAction) => c match {
          case White => Orientation.White
          case Black => Orientation.Right
        }
      case _ => colorToOrientation(c)
    }

  private def boardOrientation(pov: Pov): Orientation = boardOrientation(pov.game.variant, pov.color)

  private def boardSize(pov: Pov): Option[Board.BoardSize] = pov.game.variant match {
    case Variant.Draughts(v) => Some(v.boardSize)
    case _ => None
  }
  def mini(pov: Pov): Tag => Tag =
    miniWithOrientation(
      FEN(pov.game.variant.gameLogic, Forsyth.boardAndColor(pov.game.variant.gameLogic, pov.game.situation)),
      boardOrientation(pov),
      ~pov.game.lastMoveKeys,
      boardSize(pov)
    ) _

  def miniWithOrientation(
    fen: FEN,
    orientation: Orientation = Orientation.White,
    lastMove: String = "",
    boardSizeOpt: Option[Board.BoardSize]
  )(tag: Tag): Tag = {
    // TODO: this is an excellent candidate for refactoring.
    val libName = fen match {
      case FEN.Chess(_) => GameLogic.Chess().name
      case FEN.Draughts(_) => GameLogic.Draughts().name
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
      cls := s"mini-board mini-board--init cg-wrap is2d ${libName.toLowerCase()} ${extra}",
      dataState := data
    )(cgWrapContent)
  }

  def mini(fen: FEN, color: Color = White, lastMove: String = "")(tag: Tag): Tag =
    miniWithOrientation(fen, colorToOrientation(color), lastMove, None)(tag)

  def miniForVariant(fen: FEN, variant: Variant, color: Color = White, lastMove: String = "")(tag: Tag): Tag =
    miniWithOrientation(fen, boardOrientation(variant, color), lastMove, None)(tag)


  def miniSpan(fen: FEN, color: Color = White, lastMove: String = "") =
    mini(fen, color, lastMove)(span)

  private def sitCanCastle(sit: Situation, color: Color, side: strategygames.chess.Side): Boolean =
    sit match {
      case Situation.Chess(sit) => sit canCastle color on side
      case _ => false
    }

  def jsData(
      sit: Situation,
      fen: FEN
  )(implicit ctx: Context) =
    Json.obj(
      "fen"     -> fen.value.split(" ").take(4).mkString(" "),
      "baseUrl" -> s"$netBaseUrl${routes.Editor.load("")}",
      "color"   -> sit.color.letter.toString,
      "castles" -> Json.obj(
        "K" -> sitCanCastle(sit, White, strategygames.chess.KingSide),
        "Q" -> sitCanCastle(sit, White, strategygames.chess.QueenSide),
        "k" -> sitCanCastle(sit, Black, strategygames.chess.KingSide),
        "q" -> sitCanCastle(sit, Black, strategygames.chess.QueenSide)
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
    trans.whitePlays,
    trans.blackPlays,
    trans.variant,
    trans.continueFromHere,
    trans.playWithTheMachine,
    trans.playWithAFriend,
    trans.analysis,
    trans.toStudy
  ).map(_.key)
}
