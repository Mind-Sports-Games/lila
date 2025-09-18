package views.html.library

import play.api.libs.json.Json

import controllers.routes
import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.String.html.safeJsonValue
import lila.i18n.{ I18nKeys => trans, VariantKeys }
import lila.game.{ MonthlyGameData }
import play.api.i18n.Lang

import strategygames.variant.Variant
import strategygames.GameLogic

object home {

  def apply(
      monthlyGameData: List[MonthlyGameData],
      clockRates: (Int, Int)
      // botOrHumanGames: (Int, Int)
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = "Library of Games",
      moreCss = cssTag("library"),
      moreJs = frag(
        jsModule("library"),
        jsTag("chart/library.js"),
        embedJsUnsafeLoadThen(s"""window.libraryChartData = ${safeJsonValue(
          Json.obj(
            "freq" -> bits.transformData(monthlyGameData),
            "i18n" -> i18nJsObject(bits.i18nKeys),
            "variantNames" -> Json.obj(
              Variant.all.map(v =>
                s"${v.gameFamily.id}_${v.id}" -> Json.toJsFieldJsValueWrapper(VariantKeys.variantName(v))
              ): _*
            )
          )
        )};"""),
        embedJsUnsafeLoadThen(s"""playstrategy.libraryChart(window.libraryChartData)""")
      ),
      openGraph = lila.app.ui
        .OpenGraph(
          title = "Library of Games",
          url = s"$netBaseUrl${routes.Library.home.url}",
          description = "Games you can play on PlayStrategy."
        )
        .some,
      zoomable = true
    )(
      main(
        id := "library-section",
        cls := "library-all"
      )(
        h1(cls := "library-title color-choice")(trans.gameLibrary()),
        div(cls := "gamegroup-choice")(
          div(cls := "section-title")(trans.gameGroup()),
          div(cls := "gamegroup-icons")(translatedGameGroupIconChoices map { case (id, icon, hint) =>
            (button(cls := "gamegroup", value := id, dataIcon := icon)(hint))
          })
        ),
        div(cls := "variants-choice hidden")(
          div(cls := "section-title")(trans.variant()),
          div(cls := "variants-icons")(translatedVariantIconChoices.filter { case (id, _, _) =>
            id != "0_3" //from position
          } map { case (id, icon, name) =>
            (button(
              cls := "variant",
              dataIcon := icon,
              value := id,
              href := routes.Library.variant(variantKey(id))
            )(name))
          })
        ),
        div(id := "library_chart_area")(
          div(id := "library_chart")(spinner)
        ),
        div(cls := "library-stats-table")(
          h2(cls := "library-stats-title color-choice")("Overall Game Stats"),
          bits
            .statsRow("Total Game Variants", bits.totalVariants(monthlyGameData).toString, "total-variants"),
          bits.statsRow("Total Games Played", bits.totalGames(monthlyGameData).toString, "total-games"),
          bits.statsRow(
            "Games Played Last Month",
            bits.totalGamesLastFullMonth(monthlyGameData).toString,
            "games-last-month"
          ),
          bits.statsRow("Live Games Played", clockRates._1.toString + "%", "live-games"),
          bits.statsRow("Correspondence Games Played", clockRates._2.toString + "%", "correspondence-games")
          // bits.statsRow("Human Games Played", botOrHumanGames._2.toString + "%"),
          // bits.statsRow("Bot Games Played", botOrHumanGames._1.toString + "%")
        )
      )
    )

}
