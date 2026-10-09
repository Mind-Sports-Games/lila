package views.html.library

import play.api.i18n.Lang
import play.api.libs.json.Json

import strategygames.variant.Variant
import strategygames.Speed

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

object show {

  def apply(
      variant: Variant,
      monthlyGameData: List[MonthlyGameData],
      winRates: List[WinRatePercentages],
      leaderboard: List[User.LightPerf],
      tours: List[Tournament],
      featuredGame: Option[Game] = None,
      dailyPuzzle: Option[DailyPuzzle.WithHtml] = None,
      studies: List[lila.study.Study.WithChaptersAndLiked] = Nil,
      grandPrix: List[bits.GrandPrixEdition] = Nil
  )(implicit ctx: Context) = {
    val mso = msoSection(variant, bits.msoEvent(variant), grandPrix)
    // the blocks go two to a row: the studies share theirs with the leaderboard when an even number of
    // blocks come before it
    val besideLeaderboard = leaderboard.nonEmpty && ((if (tours.nonEmpty) 1 else 0) + mso.size) % 2 == 0
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
        // whichever of these a game has, two per row, a lone last one full width. The short blocks come
        // first and the tall ones (leaderboard, studies) after, so the two of a row are about as high
        div(cls := "library__blocks")(
          tours.nonEmpty.option(tournamentList(tours)),
          mso,
          leaderboard.nonEmpty.option(userTopPerf(leaderboard, PerfType(variant, Speed.Blitz))),
          studyList(variant, studies, besideLeaderboard)
        ),
        div(cls := "library-stats-table")(
          div(cls := "library-stats-title color-choice")(
            div(dataIcon := "^"),
            h2(trans.gameInfo()),
            div(" ") // place holder to keep title centered
          ),
          p(cls := "library-info__about")(
            trans.playVariantOnlineFree(bits.nameWithAlias(variant)),
            " ",
            VariantKeys.variantTitle(variant),
            "."
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
        relatedSection(variant)
      )
    )
  }

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

  // the game's studies, pinned first, then by likes (NotableStudies), as on the studies page, minus the
  // tutorial, which the Tutorial link above already offers; six at most
  private def studyList(
      variant: Variant,
      studies: List[lila.study.Study.WithChaptersAndLiked],
      besideLeaderboard: Boolean
  )(implicit ctx: Context) = {
    val tutorial = bits.studyLink(variant)
    val others   = studies.filterNot(s => tutorial.has(s.study.id.value)).take(6)
    others.nonEmpty.option(
      // beside the leaderboard the block is no higher than it, and its tiles scroll
      div(cls := List("library__studies" -> true, "library__studies--beside-leaderboard" -> besideLeaderboard))(
        div(cls := "color-choice title")(
          div(dataIcon := "4"),
          h2(trans.libraryStudies()),
          a(href := routes.Study.byVariantDefault(variant.key), cls := "more")(trans.more(), " »")
        ),
        div(cls := "studies")(
          others.map { s =>
            div(cls := "study")(
              views.html.study.bits.widget(s, h3),
              // a study an admin pinned here says so with a pin, in the tile's corner
              s.study.library.has(true).option(
                span(cls := "library__pin", dataIcon := "\ue93a", title := trans.libraryStudyPinned.txt())
              )
            )
          }
        )
      )
    )
  }

  // the game it derives from, the games derived from it, then the ones merely alike
  private def relatedSection(variant: Variant)(implicit ctx: Context) = {
    val related =
      (bits.parentVariant(variant).toList ::: bits.childVariants(variant) ::: bits.relatedVariants(variant)).distinct
    related.nonEmpty.option {
      div(cls := "library-related")(
        h2(trans.relatedGames()),
        div(related.map { v =>
          // the plain name: searchName adds the family word ("Three-check Chess"), which a chip on a
          // page of that family does not need
          a(href := routes.Library.variant(v.key), dataIcon := v.perfIcon, cls := "text")(
            VariantKeys.variantName(v)
          )
        })
      )
    }
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
        p {
          // the sentence carries the links: the Mind Sports Olympiad's page, the event's games and the
          // online Grand Prix, each where the sentence names it. Raw HTML, as a string argument is escaped.
          val msoLink =
            raw(a(href := routes.Page.lonePage("mind-sports-olympiad"))("Mind Sports Olympiad").render)
          def gamesLink(id: String) = raw(a(href := routes.Study.show(id))(trans.msoGamesLink()).render)
          val grandPrixLink =
            if (bits.hasMsoOnline(variant))
              raw(a(href := routes.Tournament.msoHistory)(trans.msoGrandPrix()).render)
            else trans.msoGrandPrix()
          mso match {
            // the games of an event are in a study only once somebody has recorded them
            case Some(m) =>
              m.studyId match {
                case None if m.worldChampionship => trans.msoWorldChampionshipResultsOnly(name, msoLink)
                case None                        => trans.msoEventResultsOnly(name, msoLink)
                case Some(id) if m.worldChampionship =>
                  trans.msoWorldChampionshipVenue(name, msoLink, gamesLink(id), grandPrixLink)
                case Some(id) => trans.msoEventVenue(name, msoLink, gamesLink(id), grandPrixLink)
              }
            case None => trans.msoGrandPrixVenue(name, msoLink)
          }
        },
        mso.map { m =>
          frag(
            p(
              m.resultsUrl.map(url => a(href := url)(trans.msoResultsAndMedallists())),
              " · ",
              a(href := m.allEditionsUrl)(trans.msoAllEditions())
            ),
            m.others.nonEmpty.option(
              p(
                m.others
                  .map { case (code, name) => a(href := bits.resultsUrlOf(code))(name): Frag }
                  .reduce[Frag]((a, b) => frag(a, " · ", b))
              )
            )
          )
        },
        // the online editions are all on the MSO history page, which a list here would only repeat
        bits.hasMsoOnline(variant).option(
          p(a(href := routes.Tournament.msoHistory)(trans.msoAllTournaments()))
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
