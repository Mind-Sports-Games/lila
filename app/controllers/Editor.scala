package controllers

import strategygames.variant.Variant
import strategygames.format.{ FEN, Forsyth }
import strategygames.{ GameLogic, Situation }
import play.api.libs.json._
import views._

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

  def loadFenWithVariant(urlFen: String, urlVariant: String) = load(urlFen, Some(urlVariant))

  def load(urlFen: String, urlVariant: Option[String] = None) =
    Open { implicit ctx =>
      val fenStr = lila.common.String
        .decodeUriPath(urlFen)
        .map(_.replace('_', ' ').trim)
        .filter(_.nonEmpty)
        .orElse(get("fen"))
      val variant = Variant.orDefault(urlVariant.getOrElse(""))
      fuccess {
        val situation = readFen(fenStr, variant)
        Ok(
          html.board.editor(
            sit = situation,
            fen = Forsyth.>>(situation.board.variant.gameLogic, situation),
            variant = variant,
            positionsJson
          )
        )
      }
    }

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

  def game(id: String) =
    Open { implicit ctx =>
      OptionResult(env.game.gameRepo game id) { game =>
        Redirect {
          if (game.playable) routes.Round.watcher(game.id, game.variant.startPlayer.name)
          else
            routes.Editor.loadFenWithVariant(
              get("fen") | (Forsyth.>>(game.variant.gameLogic, game.stratGame)).value,
              game.variant.key
            )
        }
      }
    }
}
