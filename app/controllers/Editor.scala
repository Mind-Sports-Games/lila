package controllers

import strategygames.variant.Variant
import strategygames.format.{ FEN, Forsyth }
import strategygames.{ GameLogic, Situation }
import play.api.libs.json._
import views._
import strategygames.chess.format.{ Forsyth => ChessForsyth }

import lila.app._
import lila.common.Json._

final class Editor(env: Env) extends LilaController(env) {

  private lazy val positionsJson = lila.common.String.html.safeJsonValue {
    JsArray(strategygames.chess.StartingPosition.all map { p =>
      Json.obj(
        "eco"  -> p.eco,
        "name" -> p.name,
        "fen"  -> p.fen
      )
    })
  }

  def index = load("")

  def load(urlFen: String) =
    Open { implicit ctx =>
      val urlVariant: Option[String] = ctx.req.getQueryString("variant")
      val fenStr = lila.common.String
        .decodeUriPath(urlFen)
        .map(_.replace('_', ' ').trim)
        .filter(_.nonEmpty)
      val variant = Variant.orDefault(urlVariant.getOrElse(""))
      val orientation = ctx.req.getQueryString("orientation")
      fuccess {
        val situation = readFen(fenStr, variant)
        Ok(
          html.board.editor(
            sit = situation,
            fen = Forsyth.>>(situation.board.variant.gameLogic, situation),
            variant = variant,
            positionsJson,
            orientation
          )
        )
      }
    }

  // Study => Add a new chapter, "Editor" tab
  def data =
    Open { implicit ctx =>
      fuccess {
        val situation = readFen(
          get("fen"),
          Variant.orDefault(get("variant").getOrElse(""))
        )
        JsonOk(
          html.board.bits.jsData(
            sit = situation,
            fen = Forsyth.>>(situation.board.variant.gameLogic, situation),
            variant = situation.board.variant
          )
        )
      }
    }

  private def readFen(fen: Option[String], variant: strategygames.variant.Variant): Situation =
    fen
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(s => FEN.clean(variant.gameLogic, s))
      .flatMap(f => Forsyth.<<<@(variant.gameLogic, variant, f))
      .map(_.situation) | Situation(variant.gameLogic, variant)

  // If the game is not playable and the FEN is not passed, redirect to the editor based on the state after the last move.
  def game(id: String) =
    Open { implicit ctx =>
      OptionResult(env.game.gameRepo game id) { game =>
        Redirect(
          if (game.playable)
            routes.Round.watcher(game.id, game.variant.startPlayer.name).url
          else
            editorUrl(
              get("fen").fold(Forsyth.>>(game.variant.gameLogic, game.stratGame))(fen =>
                FEN(game.variant, fen)
              ),
              game.variant
            )
        )
      }
    }

  private[controllers] def editorUrl(fen: FEN, variant: Variant): String =
    if (fen.value == ChessForsyth.initial.value && variant.key == "standard") routes.Editor.index.url
    else routes.Editor.load(fen.value).url + s"?variant=${variant.key}"
}
