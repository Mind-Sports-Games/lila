package views.html.library

import play.api.libs.json.Json

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.common.String.html.safeJsonValue
import lila.i18n.{ I18nKeys as trans, VariantKeys }
import lila.game.{ Game, MonthlyGameData, Pov, WinRatePercentages }
import lila.rating.PerfType
import lila.user.User
import lila.tournament.Tournament
import lila.puzzle.{ DailyPuzzle, Puzzle }
import play.api.i18n.Lang

import strategygames.variant.Variant
import strategygames.Speed

import lila.tournament.Schedule.Freq

object show {

  def apply(
      variant: Variant,
      monthlyGameData: List[MonthlyGameData],
      winRates: List[WinRatePercentages],
      leaderboard: List[User.LightPerf],
      tours: List[Tournament],
      featuredGame: Option[Game] = None,
      dailyPuzzle: Option[DailyPuzzle.WithHtml] = None
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = bits.pageTitle(variant),
      moreCss = cssTag("library"),
      moreJs = frag(
        jsModule("libraryVariant"),
        jsModule("chart.library"),
        embedJsUnsafeLoadThen(s"""playstrategy.libraryChart(${safeJsonValue(
            Json.obj(
              "freq" -> bits
                .transformData(monthlyGameData)
                .filter(_._2 == s"${variant.gameFamily.id}_${variant.id}"),
              "i18n"         -> i18nJsObject(bits.i18nKeys),
              "variantNames" -> Json.obj(
                Variant.all.map(v =>
                  s"${v.gameFamily.id}_${v.id}" -> Json.toJsFieldJsValueWrapper(VariantKeys.variantName(v))
                )*
              )
            )
          )})""")
      ),
      openGraph = lila.app.ui
        .OpenGraph(
          title = bits.pageTitle(variant),
          url = s"$netBaseUrl${routes.Library.variant(variant.key).url}",
          description = bits.pageDescription(variant)
        )
        .some,
      zoomable = true,
      canonicalPath = routes.Library.variant(variant.key).url.some
    )(
      main(
        id  := "library-section",
        cls := "library-all library-all--variant"
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
            variant.hasAnalysisBoard.option(
              a(
                cls  := "library-analysis",
                href := routes.UserAnalysis.parseArg(variant.key)
              )(
                "Analysis"
              )
            ),
            Puzzle.puzzleVariants
              .exists(_.key == variant.key)
              .option(
                a(
                  cls  := "library-puzzles",
                  href := routes.Puzzle.home(variant.key)
                )(
                  "Puzzles"
                )
              ),
            ctx.userId.map(user =>
              a(
                cls  := "library-mystats",
                href := routes.User.perfStat(user, variant.key.replace("standard", "blitz"))
              )(
                "My Stats"
              )
            )
          )
        ),
        p(cls := "library-intro")(
          trans.playVariantOnlineFree(bits.nameWithAlias(variant)),
          " ",
          VariantKeys.variantTitle(variant),
          "."
        ),
        div(cls := "start")(
          a(
            href := s"/?variant=${variant.key}#game",
            cls  := List(
              "button button-color-choice config_game" -> true
              // "disabled"                               -> currentGame.isDefined
            ),
            trans.createAGame()
          )
        ),
        dailyPuzzle map { p =>
          div(cls := "library__puzzle")(
            div(cls := "color-choice title")(
              div(dataIcon := "-"),
              h2(trans.dailyPuzzle()),
              div(" ")
            ),
            views.html.puzzle.embed.dailyLink(p)(using ctx.lang)
          )
        },
        featuredGame map { g =>
          div(cls := List("library__tv" -> true, "library__tv--centered" -> dailyPuzzle.isEmpty))(
            div(cls := "color-choice title")(
              div(dataIcon := "1"),
              h2(trans.featuredGame()),
              div(" ")
            ),
            views.html.game.mini(Pov.naturalOrientation(g), tv = false)
          )
        },
        tours.nonEmpty.option(tournamentList(tours)),
        recurringSeries(variant),
        bits.msoEvent(variant).map(msoEvent(variant, _)),
        leaderboard.nonEmpty.option(userTopPerf(leaderboard, PerfType(variant, Speed.Blitz))),
        div(cls := "library-stats-table")(
          div(cls := "library-stats-title color-choice")(
            div(dataIcon := "^"),
            h2(trans.gameInfo()),
            div(" ") // place holder to keep title centered
          ),
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
        ),
        div(id := "library_chart_area")(
          div(id := "library_chart")(canvas)
        )
      )
    )

  private def tournamentList(tours: List[Tournament])(implicit ctx: Context) =
    div(cls := "tournaments")(
      div(cls := "color-choice title")(
        div(dataIcon := "g"),
        h2(trans.openTournaments()),
        a(href := routes.Tournament.home.url, cls := "more")(trans.more(), " »")
      ),
      div(cls := "enterable_list lobby__box__content")(
        views.html.tournament.bits.enterable(tours)
      )
    )

  // the stable, linkable page of each recurring series, since every edition has its own URL
  private def recurringSeries(variant: Variant)(implicit ctx: Context) =
    div(cls := "library__series")(
      div(cls := "color-choice title")(
        div(dataIcon := "g"),
        h2(trans.recurringTournaments()),
        div(" ")
      ),
      p(
        (List(Freq.Yearly, Freq.Weekly).map { freq =>
          a(href := routes.Tournament.history(freq.name, 1, variant.key.some))(
            s"${freq.display} ${VariantKeys.variantName(variant)}"
          )
        } ::: lila.tournament.TournamentShield.Category.byKey(variant.key).toList.map { categ =>
          a(href := routes.Tournament.categShields(categ.key))(s"${categ.name} Shield")
        }).reduce[Frag]((a, b) => frag(a, " · ", b))
      )
    )

  private def msoEvent(variant: Variant, mso: bits.MsoEvent)(implicit ctx: Context) = {
    val name = VariantKeys.variantName(variant)
    div(cls := "library__wc")(
      div(cls := "color-choice title")(
        div(dataIcon := "g"),
        h2(if (mso.worldChampionship) trans.msoWorldChampionship(name) else trans.msoEvent(name)),
        div(" ")
      ),
      p(
        if (mso.worldChampionship) trans.msoWorldChampionshipVenue(name)
        else trans.msoEventVenue(name)
      ),
      p(
        a(href := mso.resultsUrl)(trans.msoResultsAndMedallists()),
        mso.studyId.map { id =>
          frag(" · ", a(href := routes.Study.show(id))(trans.msoEventGames()))
        },
        " · ",
        a(href := routes.Team.tournaments(bits.msoTeamId))(trans.msoGrandPrix())
      )
    )
  }

  @annotation.nowarn("msg=unused")
  private def userTopPerf(users: List[User.LightPerf], perfType: PerfType)(implicit lang: Lang) =
    div(cls := "leaderboards")(
      div(cls := "color-choice title")(
        div(dataIcon := "U"),
        h2(trans.leaderboard()),
        div(" ") // place holder to keep title centered
        //a(href := routes.User.topNb(200, perfType.key))("More »")
      ),
      ol(users map { l =>
        li(
          lightUserLink(l.user),
          l.rating
        )
      })
    )

}
