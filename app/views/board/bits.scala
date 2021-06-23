package views.html.board

import chess.format.{ FEN, Forsyth }
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

  def colorToOrientation(c: chess.Color): Orientation =
    c match {
        case chess.White => Orientation.White
        case chess.Black => Orientation.Black
    }

  private val dataState = attr("data-state")

  private def boardOrientation(variant: chess.variant.Variant, c: chess.Color): Orientation =
    variant match {
      case chess.variant.RacingKings => Orientation.White
      case chess.variant.LinesOfAction => c match {
          case chess.White => Orientation.White
          case chess.Black => Orientation.Right
        }
      case _ => colorToOrientation(c)
    }

  private def boardOrientation(pov: Pov): Orientation = boardOrientation(pov.game.variant, pov.color)

  def mini(pov: Pov): Tag => Tag =
    miniWithOrientation(
      FEN(Forsyth.boardAndColor(pov.game.situation)),
      boardOrientation(pov),
      ~pov.game.lastMoveKeys
    ) _

  def miniWithOrientation(fen: chess.format.FEN, orientation: Orientation = Orientation.White, lastMove: String = "")(tag: Tag): Tag =
    tag(
      cls := "mini-board mini-board--init cg-wrap is2d",
      dataState := s"${fen.value},${orientation.toString().toLowerCase()},$lastMove"
    )(cgWrapContent)

  def mini(fen: chess.format.FEN, color: chess.Color = chess.White, lastMove: String = "")(tag: Tag): Tag =
    miniWithOrientation(fen, colorToOrientation(color).pp("colorToOrientation"), lastMove)(tag)

  def miniForVariant(fen: chess.format.FEN, variant: chess.variant.Variant, color: chess.Color = chess.White, lastMove: String = "")(tag: Tag): Tag =
    miniWithOrientation(fen, boardOrientation(variant, color), lastMove)(tag)


  def miniSpan(fen: chess.format.FEN, color: chess.Color = chess.White, lastMove: String = "") =
    mini(fen, color, lastMove)(span)

  def jsData(
      sit: chess.Situation,
      fen: FEN
  )(implicit ctx: Context) =
    Json.obj(
      "fen"     -> fen.value.split(" ").take(4).mkString(" "),
      "baseUrl" -> s"$netBaseUrl${routes.Editor.load("")}",
      "color"   -> sit.color.letter.toString,
      "castles" -> Json.obj(
        "K" -> (sit canCastle chess.White on chess.KingSide),
        "Q" -> (sit canCastle chess.White on chess.QueenSide),
        "k" -> (sit canCastle chess.Black on chess.KingSide),
        "q" -> (sit canCastle chess.Black on chess.QueenSide)
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
