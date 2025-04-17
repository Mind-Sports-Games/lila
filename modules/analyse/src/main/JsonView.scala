package lila.analyse

import play.api.libs.json._

import lila.game.Game
import lila.tree.Eval.JsonHandlers._

import strategygames.Player.{ P1, P2 }

object JsonView {

  def moves(analysis: Analysis, withGlyph: Boolean = true) =
    JsArray(analysis.infoAdvices map { case (info, adviceOption) =>
      Json
        .obj()
        .add("eval" -> info.cp)
        .add("mate" -> info.mate)
        .add("best" -> info.best.map(_.uci))
        .add("variation" -> info.variation.nonEmpty.option(info.variation mkString " "))
        .add("judgment" -> adviceOption.map { a =>
          Json
            .obj(
              "name"    -> a.judgment.name,
              "comment" -> a.makeComment(withEval = false, withBestMove = true)
            )
            .add(
              "glyph" -> withGlyph.option(
                Json.obj(
                  "name"   -> a.judgment.glyph.name,
                  "symbol" -> a.judgment.glyph.symbol
                )
              )
            )
        })
    })

  import Accuracy.povToPovLike

  def player(pov: Accuracy.PovLike)(analysis: Analysis) =
    analysis.summary
      .find(_._1 == pov.playerIndex)
      .map(_._2)
      .map(s =>
        JsObject(s map { case (nag, nb) =>
          nag.toString.toLowerCase -> JsNumber(nb)
        }).add("acpl" -> lila.analyse.Accuracy.mean(pov, analysis))
      )

  def bothPlayers(game: Game, analysis: Analysis) =
    Json.obj(
      "id" -> analysis.id,
      "p1" -> player(game.p1Pov)(analysis),
      "p2" -> player(game.p2Pov)(analysis)
    )

  def bothPlayers(pov: Accuracy.PovLike, analysis: Analysis) =
    Json.obj(
      "id" -> analysis.id,
      "p1" -> player(pov.copy(playerIndex = P1))(analysis),
      "p2" -> player(pov.copy(playerIndex = P2))(analysis)
    )

  def mobile(game: Game, analysis: Analysis) =
    Json.obj(
      "summary" -> bothPlayers(game, analysis),
      "moves"   -> moves(analysis)
    )
}
