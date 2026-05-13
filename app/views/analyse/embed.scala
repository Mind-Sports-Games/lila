package views.html.analyse

import play.api.libs.json.{ JsObject, Json }

import lila.app.templating.Environment.*
import lila.app.ui.EmbedConfig
import lila.app.ui.ScalatagsTemplate.*
import lila.common.String.html.safeJsonValue

object embed {

  def apply(pov: lila.game.Pov, data: JsObject)(config: EmbedConfig) =
    views.html.base.embed(
      title = replay.titleOf(pov)(using config.lang),
      cssModule = "analyse.embed"
    )(
      div(cls := "is2d")(
        main(cls := "analyse")
      ),
      footer {
        val url = routes.Round.watcher(pov.gameId, pov.playerIndex.name)
        frag(
          div(cls := "left")(
            a(targetBlank, href := url)(h1(titleGame(pov.game))),
            " ",
            em("brought to you by ", a(targetBlank, href := netBaseUrl)(netConfig.domain))
          ),
          a(targetBlank, cls := "open", href := url)("Open")
        )
      },
      views.html.base.layout.playstrategyJsObject(config.nonce)(using config.lang),
      depsTag("javascripts/vendor/cash.min.js"),
      depsTag("javascripts/vendor/powertip.min.js"),
      depsTag("javascripts/vendor/howler.min.js"),
      depsTag("javascripts/vendor/mousetrap.min.js"),
      jsModule("analysisBoard.embed"),
      analyseTag,
      embedJsUnsafeLoadThen(
        s"""PlayStrategyAnalyseEmbed(${safeJsonValue(
            Json.obj(
              "data"  -> data,
              "embed" -> true,
              "i18n"  -> views.html.board.userAnalysisI18n(withCeval = false, withExplorer = false)(using
                config.lang
              )
            )
          )})""",
        config.nonce
      )
    )(using config)

  def notFound(config: EmbedConfig) =
    views.html.base.embed(
      title = "404 - Game not found",
      cssModule = "analyse.embed"
    )(
      div(cls := "not-found")(
        h1("Game not found")
      )
    )(using config)
}
