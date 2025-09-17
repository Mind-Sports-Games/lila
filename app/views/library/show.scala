package views.html.library

import play.api.libs.json.Json

import controllers.routes
import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.String.html.safeJsonValue
import lila.i18n.{ I18nKeys => trans, VariantKeys }
import lila.game.{ MonthlyGameData, WinRatePercentages }
import lila.rating.PerfType
import lila.user.User
import play.api.i18n.Lang

import strategygames.variant.Variant
import strategygames.Speed

object show {

  def apply(
      variant: Variant,
      monthlyGameData: List[MonthlyGameData],
      winRates: List[WinRatePercentages],
      leaderboard: List[User.LightPerf]
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
        leaderboard.nonEmpty option userTopPerf(leaderboard, PerfType(variant, Speed.Blitz)),
        div(id := "library_chart_area")(
          div(id := "library_chart")(spinner)
        ),
        div(cls := "library-stats-table")(
          h2(cls := "library-stats-title color-choice")("Game Info"),
          bits.statsRow("Date Released", bits.releaseDateDisplay(monthlyGameData, variant)),
          bits.statsRow("Total Games Played", bits.totalGamesForVariant(monthlyGameData, variant).toString()),
          bits.statsRow(
            "Games Played Last Month",
            bits.totalGamesLastFullMonthForVariant(monthlyGameData, variant).toString()
          ),
          bits.statsRow("Average Games/Day", bits.gamesPerDay(monthlyGameData, variant)),
          bits.statsRow("Player 1 wins", bits.winRatePlayer1(variant, winRates)),
          bits.statsRow("Player 2 wins", bits.winRatePlayer2(variant, winRates)),
          bits.statsRow("Draws", bits.winRateDraws(variant, winRates))
        )
      )
    )

  private def userTopPerf(users: List[User.LightPerf], perfType: PerfType)(implicit lang: Lang) =
    div(cls := "leaderboards")(
      div(cls := "color-choice title")(
        h2("Leaderboard"),
        a(href := routes.User.topNb(200, perfType.key))("More »")
      ),
      ol(users map { l =>
        li(
          lightUserLink(l.user),
          l.rating
        )
      })
    )

}
