package views.html.board

import strategygames.format.{ FEN, Forsyth }
import strategygames.{ Player => PlayerIndex, P2, P1 }
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

  //Orientation P1,P2,Left,Right is the view of the boar as though you are sat down at that position
  //Orientation P1VFlip is for backgammon where for P2 they essentially view as P1 but flip the board vertically
  object Orientation {
    final case object P1      extends Orientation
    final case object P2      extends Orientation
    final case object Left    extends Orientation
    final case object Right   extends Orientation
    final case object P1VFlip extends Orientation
  }

  def playerIndexToOrientation(c: PlayerIndex, v: String): Orientation =
    (c, v) match {
      case (P1, _)            => Orientation.P1
      case (P2, "backgammon") => Orientation.P1VFlip
      case (P2, "nackgammon") => Orientation.P1VFlip
      case (P2, _)            => Orientation.P2
    }

  private val dataState = attr("data-state")

  private def boardOrientation(variant: Variant, c: PlayerIndex): Orientation =
    variant match {
      case Variant.Chess(strategygames.chess.variant.RacingKings) => Orientation.P1
      case Variant.Chess(strategygames.chess.variant.LinesOfAction) |
          Variant.Chess(strategygames.chess.variant.ScrambledEggs) =>
        c match {
          case P1 => Orientation.P1
          case P2 => Orientation.Right
        }
      case Variant.Backgammon(strategygames.backgammon.variant.Backgammon) |
          Variant.Backgammon(strategygames.backgammon.variant.Nackgammon) =>
        c match {
          case P1 => Orientation.P1
          case P2 => Orientation.P1VFlip
        }
      case _ => playerIndexToOrientation(c, variant.key)
    }

  private def boardOrientation(pov: Pov): Orientation = boardOrientation(pov.game.variant, pov.playerIndex)

  private def boardSize(pov: Pov): Option[Board.BoardSize] = pov.game.variant match {
    case Variant.Draughts(v) => Some(v.boardSize)
    case _                   => None
  }
  def mini(pov: Pov): Tag => Tag =
    miniWithOrientation(
      FEN(
        pov.game.variant.gameLogic,
        Forsyth.boardAndPlayer(pov.game.variant.gameLogic, pov.game.situation)
      ),
      boardOrientation(pov),
      ~pov.game.lastActionKeys,
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
      case FEN.Chess(_)        => GameLogic.Chess().name
      case FEN.Draughts(_)     => GameLogic.Draughts().name
      case FEN.FairySF(_)      => GameLogic.FairySF().name
      case FEN.Samurai(_)      => GameLogic.Samurai().name
      case FEN.Togyzkumalak(_) => GameLogic.Togyzkumalak().name
      case FEN.Go(_)           => GameLogic.Go().name
      case FEN.Backgammon(_)   => GameLogic.Backgammon().name
      case FEN.Abalone(_)      => GameLogic.Abalone().name
    }
    val orient    = orientation.toString().toLowerCase()
    val boardSize = boardSizeOpt.getOrElse(Board.D100)
    val data = if (libName == "Draughts") {
      s"${fen.value}|${boardSize.width}x${boardSize.height}|${orient}|$lastMove"
    } else {
      s"${fen.value}|${orient}|$lastMove"
    }
    val extra =
      if (libName == "Draughts") s"is${boardSize.key} ${libName.toLowerCase()}"
      else s"${libName.toLowerCase()}"
    tag(
      cls := s"mini-board mini-board--init cg-wrap is2d variant-${variantKey} ${extra}",
      dataState := data
    )(cgWrapContent)
  }

  def mini(fen: FEN, playerIndex: PlayerIndex = P1, variantKey: String, lastMove: String = "")(
      tag: Tag
  ): Tag =
    miniWithOrientation(fen, playerIndexToOrientation(playerIndex, variantKey), lastMove, None, variantKey)(
      tag
    )

  def miniForVariant(fen: FEN, variant: Variant, playerIndex: PlayerIndex = P1, lastMove: String = "")(
      tag: Tag
  ): Tag =
    miniWithOrientation(fen, boardOrientation(variant, playerIndex), lastMove, None, variant.key)(tag)

  def miniSpan(fen: FEN, playerIndex: PlayerIndex = P1, variantKey: String, lastMove: String = "") =
    mini(fen, playerIndex, variantKey, lastMove)(span)

  private def sitCanCastle(
      sit: Situation,
      playerIndex: PlayerIndex,
      side: strategygames.chess.Side
  ): Boolean =
    sit match {
      case Situation.Chess(sit) => sit canCastle playerIndex on side
      case _                    => false
    }

  def jsData(
      sit: Situation,
      fen: FEN
  )(implicit ctx: Context) =
    Json.obj(
      "fen"         -> fen.value.split(" ").take(4).mkString(" "),
      "baseUrl"     -> s"$netBaseUrl${routes.Editor.load("")}",
      "playerIndex" -> sit.player.letter.toString,
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
    trans.playerIndexPlays,
    trans.variant,
    trans.continueFromHere,
    trans.playWithTheMachine,
    trans.playWithAFriend,
    trans.analysis,
    trans.toStudy
  ).map(_.key)
}
