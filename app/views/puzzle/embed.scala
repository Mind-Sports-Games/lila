package views.html.puzzle

import play.api.i18n.Lang

import lila.app.templating.Environment.*
import lila.app.ui.EmbedConfig
import lila.app.ui.ScalatagsTemplate.*
import lila.puzzle.DailyPuzzle
import lila.i18n.VariantKeys

object embed {

  def apply(daily: DailyPuzzle.WithHtml)(implicit config: EmbedConfig) =
    views.html.base.embed(
      title = "playstrategy.org chess puzzle",
      cssModule = "tv.embed"
    )(
      dailyLink(daily)(using config.lang)(
        targetBlank,
        id  := "daily-puzzle",
        cls := "embedded"
      ),
      depsTag("javascripts/vendor/cash.min.js"),
      jsModule("puzzleEmbed")
    )

  def dailyLink(daily: DailyPuzzle.WithHtml)(implicit lang: Lang) = a(
    href  := routes.Puzzle.daily,
    title := trans.puzzle.clickToSolve.txt()
  )(
    span(cls := "text")(
      trans.puzzle.puzzleOfTheDay(),
      " - ",
      VariantKeys.variantName(daily.puzzle.variant)
    ),
    raw(daily.html),
    span(cls := "text")(trans.playerIndexPlays(daily.puzzle.playerTrans))
  )
}
