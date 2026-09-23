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


object show {

  def apply(
      variant: Variant,
      monthlyGameData: List[MonthlyGameData],
      winRates: List[WinRatePercentages],
      leaderboard: List[User.LightPerf],
      tours: List[Tournament],
      featuredGame: Option[Game] = None,
      dailyPuzzle: Option[DailyPuzzle.WithHtml] = None,
      studies: List[lila.study.Study.Notable] = Nil,
      grandPrix: List[bits.GrandPrixEdition] = Nil
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
      canonicalPath = routes.Library.variant(variant.key).url.some,
      alternates = true
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
        // the live row keeps its two slots: puzzle left, featured game right (centred when alone)
        (dailyPuzzle.isDefined || featuredGame.isDefined).option(
          div(cls := "library__live")(
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
            }
          )
        ),
        // whichever of these a game has, two per row, a lone last one full width
        div(cls := "library__blocks")(
          tours.nonEmpty.option(tournamentList(tours)),
          leaderboard.nonEmpty.option(userTopPerf(leaderboard, PerfType(variant, Speed.Blitz))),
          msoSection(variant, bits.msoEvent(variant), grandPrix),
          studyList(variant, studies)
        ),
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
        ),
        p(cls := "library-outro")(
          trans.playVariantOnlineFree(bits.nameWithAlias(variant)),
          " ",
          VariantKeys.variantTitle(variant),
          ".",
          bits.parentVariant(variant).map { parent =>
            frag(
              " ",
              trans.variantOf(
                bits.searchName(variant),
                a(href := routes.Library.variant(parent.key))(bits.searchName(parent))
              )
            )
          },
          bits.childVariants(variant) match {
            case Nil      => emptyFrag
            case children =>
              frag(
                " ",
                trans.alsoOnPlayStrategy(
                  children
                    .map(v => a(href := routes.Library.variant(v.key))(bits.searchName(v)): Frag)
                    .reduce[Frag]((a, b) => frag(a, ", ", b))
                )
              )
          }
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

  // the game's notable studies, the site's own first, then by rank (NotableStudies),
  // minus the tutorial and the MSO event games, which the page links on their own
  private def studyList(variant: Variant, studies: List[lila.study.Study.Notable])(implicit ctx: Context) = {
    val linked = bits.studyLink(variant).toSet ++ bits.msoEvent(variant).flatMap(_.studyId)
    val others = studies.filterNot(s => linked(s.id.value))
    others.nonEmpty.option(
      div(cls := "library__studies")(
        div(cls := "color-choice title")(
          div(dataIcon := "4"),
          h2(trans.variantStudies(bits.searchName(variant))),
          a(href := routes.Study.byVariantDefault(variant.key), cls := "more")(trans.more(), " »")
        ),
        ul(others.map { s =>
          li(a(href := routes.Study.show(s.id.value))(s.name.value))
        })
      )
    )
  }

  // the game at the Mind Sports Olympiad: its in-person event when it has one,
  // and its online Grand Prix editions when there are any
  private def msoSection(
      variant: Variant,
      mso: Option[bits.MsoEvent],
      grandPrix: List[bits.GrandPrixEdition]
  )(implicit ctx: Context) =
    (mso.isDefined || grandPrix.nonEmpty).option {
      val name = VariantKeys.variantName(variant)
      div(cls := "library__wc")(
        div(cls := "color-choice title")(
          div(dataIcon := "g"),
          h2(mso match {
            case Some(m) if m.worldChampionship => trans.msoWorldChampionship(name)
            case Some(_)                        => trans.msoEvent(name)
            case None                           => trans.msoGrandPrixEvent(name)
          }),
          div(" ")
        ),
        p(mso match {
          case Some(m) if m.worldChampionship => trans.msoWorldChampionshipVenue(name)
          case Some(_)                        => trans.msoEventVenue(name)
          case None                           => trans.msoGrandPrixVenue(name)
        }),
        mso.map { m =>
          p(
            a(href := m.resultsUrl)(trans.msoResultsAndMedallists()),
            m.studyId.map { id =>
              frag(" · ", a(href := routes.Study.show(id))(trans.msoEventGames()))
            }
          )
        },
        p(
          // the game's own editions, latest first; the MSO team's tournaments only when it has none
          if (grandPrix.isEmpty) a(href := routes.Team.tournaments(bits.msoTeamId))(trans.msoGrandPrix())
          else
            grandPrix
              .take(5)
              .map(e => a(href := e.url)(e.name): Frag)
              .reduce[Frag]((a, b) => frag(a, " · ", b))
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
