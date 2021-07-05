package views.html.board

import strategygames.chess.format.{ FEN, Forsyth }
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

  def colorToOrientation(c: strategygames.chess.Color): Orientation =
    c match {
        case strategygames.chess.White => Orientation.White
        case strategygames.chess.Black => Orientation.Black
    }

  private val dataState = attr("data-state")

  private def boardOrientation(variant: strategygames.chess.variant.Variant, c: strategygames.chess.Color): Orientation =
    variant match {
      case strategygames.chess.variant.RacingKings => Orientation.White
      case strategygames.chess.variant.LinesOfAction => c match {
          case strategygames.chess.White => Orientation.White
          case strategygames.chess.Black => Orientation.Right
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

  def miniWithOrientation(fen: strategygames.chess.format.FEN, orientation: Orientation = Orientation.White, lastMove: String = "")(tag: Tag): Tag =
    tag(
      cls := "mini-board mini-board--init cg-wrap is2d",
      dataState := s"${fen.value},${orientation.toString().toLowerCase()},$lastMove"
    )(cgWrapContent)

  def mini(fen: strategygames.chess.format.FEN, color: strategygames.chess.Color = strategygames.chess.White, lastMove: String = "")(tag: Tag): Tag =
    miniWithOrientation(fen, colorToOrientation(color), lastMove)(tag)

  def miniForVariant(fen: strategygames.chess.format.FEN, variant: strategygames.chess.variant.Variant, color: strategygames.chess.Color = strategygames.chess.White, lastMove: String = "")(tag: Tag): Tag =
    miniWithOrientation(fen, boardOrientation(variant, color), lastMove)(tag)


  def miniSpan(fen: strategygames.chess.format.FEN, color: strategygames.chess.Color = strategygames.chess.White, lastMove: String = "") =
    mini(fen, color, lastMove)(span)

  def jsData(
      sit: strategygames.chess.Situation,
      fen: FEN
  )(implicit ctx: Context) =
    Json.obj(
      "fen"     -> fen.value.split(" ").take(4).mkString(" "),
      "baseUrl" -> s"$netBaseUrl${routes.Editor.load("")}",
      "color"   -> sit.color.letter.toString,
      "castles" -> Json.obj(
        "K" -> (sit canCastle strategygames.chess.White on strategygames.chess.KingSide),
        "Q" -> (sit canCastle strategygames.chess.White on strategygames.chess.QueenSide),
        "k" -> (sit canCastle strategygames.chess.Black on strategygames.chess.KingSide),
        "q" -> (sit canCastle strategygames.chess.Black on strategygames.chess.QueenSide)
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
