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
      fuccess {
        val situation = readFen(urlFen, urlVariant)
        Ok(
          html.board.editor(
            sit = situation,
            fen = Forsyth.>>(situation.board.variant.gameLogic, situation),
            variant = situation.board.variant,
            positionsJson
          )
        )
      }
    }

  def data =
    Open { implicit ctx =>
      fuccess {
        val situation = readFen(
          get("fen").getOrElse(""),
          get("variant")
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

  private def readFen(urlFen: String, urlVariant: Option[String]): Situation = {
    // Several cases to handle:
    // 1. /editor : urlVariant is None, urlFen is empty
    //  - /editor/chess/rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR_w_KQkq_-_0_1
    // 2/ /editor/variant : urlVariant is None, urlFen is "variant"
    //  - /editor/variant/initial/fen/of/the/variant
    // 3/ /editor/notAnExistingVariant
    //  - /editor/chess/initial/fen/of/chess
    // 4. /editor/rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR_w_KQkq_-_0_1
    //  - rnbqkbnr is not a correct variant name, use /editor/chess/rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR_w_KQkq_-_0_1
    // 5. /editor/variant/some/fen
    //  - /editor/variant/some/fen
    // 6. /editor/variant/somethingThatIsNotAFen
    //  - /editor/variant/initial/fen/of/the/variant

      if (!urlVariant.isDefined) {
        if(urlFen.trim.isEmpty) { // 1
          return Situation(GameLogic.Chess(), Variant.default(GameLogic.Chess()))
        }

        Variant.apply(urlFen) match { // 2 & 3
          case Some(variant) =>
            return Situation(variant.gameLogic, variant)
          case None =>
            return Situation(GameLogic.Chess(), Variant.default(GameLogic.Chess()))
        }
      }

      // /editor/some/fen & /editor/notAVariantKey/some/fen
      // use chess/some/fen or chess/notAVariantKey/some/fen for backward compatibility
      var fenStr = lila.common.String
        .decodeUriPath(urlFen)
        .map(_.replace('_', ' ').trim)
        .filter(_.nonEmpty)

      val variant = urlVariant.map(v => if (v == "chess") "standard" else v).flatMap(Variant.apply) match {
        case Some(v) => v
        case None => { // 4
          fenStr = lila.common.String
            .decodeUriPath(urlVariant.getOrElse("") + "/" + urlFen)
            .map(_.replace('_', ' ').trim)
            .filter(_.nonEmpty)
          Variant.default(GameLogic.Chess())
        }
      }

    fenStr
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(s => FEN.clean(variant.gameLogic, s))
      .flatMap(f => Forsyth.<<<@(variant.gameLogic, variant, f))
      .map(_.situation) | Situation(variant.gameLogic, variant) // 5 & 6
  }

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
