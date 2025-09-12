package views.html.library

import play.api.libs.json.Json

import controllers.routes
import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.String.html.safeJsonValue
import lila.i18n.{ I18nKeys => trans, VariantKeys }
import play.api.i18n.Lang

import strategygames.variant.Variant

object show {

  def apply(
      variant: Variant,
      monthlyGameData: List[(String, String, Long)],
      winRates: List[(String, Int, Int, Int)]
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = s"${VariantKeys.variantName(variant)} • ${VariantKeys.variantTitle(variant)}",
      moreCss = cssTag("library"),
      moreJs = frag(
        jsModule("libraryVariant"),
        jsTag("chart/library.js"),
        embedJsUnsafeLoadThen(s"""playstrategy.libraryChart(${safeJsonValue(
          Json.obj(
            "freq" -> bits
              .transformData(monthlyGameData)
              .filter(_._2 == s"${variant.gameFamily.id}_${variant.id}"),
            "i18n" -> i18nJsObject(bits.i18nKeys),
            "variantNames" -> Json.obj(
              Variant.all.map(v =>
                s"${v.gameFamily.id}_${v.id}" -> Json.toJsFieldJsValueWrapper(VariantKeys.variantName(v))
              ): _*
            )
          )
        )})""")
      ),
      openGraph = lila.app.ui
        .OpenGraph(
          title = "Library of Games",
          url = s"$netBaseUrl${routes.Library.home.url}",
          description = s"Play ${VariantKeys.variantTitle(variant)} on PlayStrategy."
        )
        .some,
      zoomable = true
    )(
      main(
        id := "library-section",
        cls := "library-all"
      )(
        div(cls := "library-header color-choice")(
          h1(cls := "library-title")(
            a(href := routes.Library.home.url, cls := "library-back", title := "back", dataIcon := "I")(),
            span(s"${VariantKeys.variantName(variant)}"),
            span(dataIcon := variant.perfIcon)()
          ),
          div(cls := "library-links")(
            a(cls := "library-rules", href := s"${routes.Page.variant(variant.key)}")(
              "Rules"
            ),
            bits.studyLink(variant).map { studyId =>
              a(cls := "library-tutorial", href := s"${routes.Study.show(studyId)}")(
                "Tutorial"
              )
            },
            a(cls := "library-editor", href := s"${routes.Editor.index}?variant=${variant.key}")(
              "Editor"
            ),
            variant.hasAnalysisBoard option a(
              cls := "library-analysis",
              href := routes.UserAnalysis.parseArg(variant.key)
            )(
              "Analysis"
            ),
            ctx.userId.map(user =>
              a(
                cls := "library-mystats",
                href := routes.User.perfStat(user, variant.key.replace("standard", "blitz"))
              )(
                "My Stats"
              )
            )
          )
        ),
        div(cls := "start")(
          a(
            href := s"/?variant=${variant.key}#game",
            cls := List(
              "button button-color-choice config_game" -> true
              //"disabled"                               -> currentGame.isDefined
            ),
            trans.createAGame()
          )
        ),
        div(id := "library_chart_area")(
          div(id := "library_chart")(spinner)
        ),
        div(cls := "library-stats-table")(
          h2(cls := "library-stats-title color-choice")("Stats"),
          div(cls := "library-stats-row")(
            div(cls := "library-stats-term")("Date Released"),
            div(cls := "library-stats-value")(bits.releaseDateDisplay(monthlyGameData, variant))
          ),
          div(cls := "library-stats-row")(
            div(cls := "library-stats-term")("Total Games Played"),
            div(cls := "library-stats-value")(bits.totalGamesForVariant(monthlyGameData, variant))
          ),
          div(cls := "library-stats-row")(
            div(cls := "library-stats-term")(s"Total Games Played (${bits.lastFullMonth})"),
            div(cls := "library-stats-value")(
              bits.totalGamesLastFullMonthForVariant(monthlyGameData, variant)
            )
          ),
          div(cls := "library-stats-row")(
            div(cls := "library-stats-term")("Average Games/Day"),
            div(cls := "library-stats-value")(bits.gamesPerDay(monthlyGameData, variant))
          ),
          div(cls := "library-stats-row")(
            div(cls := "library-stats-term")("Player 1 wins"),
            div(cls := "library-stats-value")(bits.winRatePlayer1(variant, winRates))
          ),
          div(cls := "library-stats-row")(
            div(cls := "library-stats-term")("Player 2 wins"),
            div(cls := "library-stats-value")(bits.winRatePlayer2(variant, winRates))
          ),
          div(cls := "library-stats-row")(
            div(cls := "library-stats-term")("Draws"),
            div(cls := "library-stats-value")(bits.winRateDraws(variant, winRates))
          )
        )
      )
    )

}
