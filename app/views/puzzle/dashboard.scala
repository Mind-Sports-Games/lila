package views
package html.puzzle

import controllers.routes
import play.api.i18n.Lang
import play.api.libs.json.Json

import strategygames.variant.Variant
import lila.i18n.{ VariantKeys }

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.String.html.safeJsonValue
import lila.puzzle.{ Puzzle, PuzzleDashboard, PuzzleTheme }
import lila.user.User

object dashboard {

  private val baseClass      = "puzzle-dashboard"
  private val metricClass    = s"${baseClass}__metric"
  private val themeClass     = s"${baseClass}__theme"
  private val dataWinPercent = attr("data-win-percent")

  def home(user: User, variant: Variant, dashOpt: Option[PuzzleDashboard], days: Int)(implicit ctx: Context) =
    dashboardLayout(
      user = user,
      days = days,
      path = "dashboard",
      variant = variant,
      title =
        if (ctx is user) "Puzzle dashboard"
        else s"${user.username} puzzle dashboard",
      subtitle = "Train, analyse, improve",
      dashOpt = dashOpt,
      moreJs = dashOpt ?? { dash =>
        val mostPlayedVariant = dash.mostPlayedVariant.sortBy { case (key, _) =>
          VariantKeys.variantName(key)
        }
        frag(
          jsModule("puzzle.dashboard"),
          embedJsUnsafeLoadThen(s"""PlayStrategyPuzzleDashboard(${safeJsonValue(
            Json
              .obj(
                "radar" -> Json.obj(
                  "labels" -> mostPlayedVariant.map { case (key, _) =>
                    VariantKeys.variantName(key)
                  },
                  "datasets" -> Json.arr(
                    Json.obj(
                      "label" -> "Performance",
                      "data" -> mostPlayedVariant.map { case (_, results) =>
                        results.performance
                      }
                    )
                  )
                )
              )
          )})""")
        )
      }
    ) { dash =>
      dash.mostPlayedThemes(variant).size > 0 option
        div(cls := s"${baseClass}__global")(
          dash.byVariant.get(variant).map { results =>
            metricsOf(days, variant, PuzzleTheme.mix.key, results)
          },
          dash.mostPlayedVariant.size > 2 option canvas(cls := s"${baseClass}__radar")
        )
    }

  def improvementAreas(user: User, variant: Variant, dashOpt: Option[PuzzleDashboard], days: Int)(implicit
      ctx: Context
  ) =
    dashboardLayout(
      user = user,
      days = days,
      "improvementAreas",
      variant = variant,
      title =
        if (ctx is user) trans.puzzle.improvementAreas.txt()
        else s"${user.username} improvement areas",
      subtitle = "Train these to optimize your progress!",
      dashOpt = dashOpt
    ) { dash =>
      {
        val weakThemes = dash.weakThemes(variant)
        weakThemes.nonEmpty option themeSelection(days, variant, weakThemes)
      }
    }

  def strengths(user: User, variant: Variant, dashOpt: Option[PuzzleDashboard], days: Int)(implicit
      ctx: Context
  ) =
    dashboardLayout(
      user = user,
      days = days,
      "strengths",
      variant = variant,
      title =
        if (ctx is user) trans.puzzle.strengths.txt()
        else s"${user.username} puzzle strengths",
      subtitle = "You perform the best in these themes",
      dashOpt = dashOpt
    ) { dash =>
      {
        val strongThemes = dash.strongThemes(variant)
        strongThemes.nonEmpty option themeSelection(days, variant, strongThemes)
      }
    }

  private def dashboardLayout(
      user: User,
      days: Int,
      path: String,
      variant: Variant,
      title: String,
      subtitle: String,
      dashOpt: Option[PuzzleDashboard],
      moreJs: Frag = emptyFrag
  )(
      body: PuzzleDashboard => Option[Frag]
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = title,
      moreCss = cssTag("puzzle.dashboard"),
      moreJs = moreJs
    )(
      main(cls := "page-menu")(
        bits.pageMenu(path, variant),
        div(cls := s"page-menu__content box box-pad $baseClass")(
          div(cls := "box__top")(
            // iconTag('-'),
            h1(
              title,
              strong(subtitle)
            ),
            views.html.base.bits.mselect(
              s"${baseClass}__day-select box__top__actions",
              span(trans.nbDays.pluralSame(days)),
              PuzzleDashboard.dayChoices map { d =>
                a(
                  cls := (d == days).option("current"),
                  href := s"${routes.Puzzle
                    .dashboard(variant.key, d, path)}${!(ctx is user) ?? s"?u=${user.username}"}"
                )(trans.nbDays.pluralSame(d))
              }
            )
          ),
          div(cls := s"${baseClass}__variant_select variant_group")(
            Puzzle.puzzleVariants.map { v =>
              button(cls := s"variant ${if (v.key == variant.key) "selected" else ""}")(
                a(
                  href := s"${routes.Puzzle
                    .dashboard(v.key, days, path)}${!(ctx is user) ?? s"?u=${user.username}"}",
                  dataIcon := v.perfIcon
                )(VariantKeys.variantName(v))
              )
            }
          ),
          dashOpt.flatMap(body) |
            div(cls := s"${baseClass}__empty")(
              a(href := routes.Puzzle.home(variant.key))("Nothing to show, go play some puzzles first!")
            )
        )
      )
    )

  private def themeSelection(
      days: Int,
      variant: Variant,
      themes: List[(PuzzleTheme.Key, PuzzleDashboard.Results)]
  )(implicit
      lang: Lang
  ) =
    themes.map { case (key, results) =>
      div(cls := themeClass)(
        div(cls := s"${themeClass}__meta")(
          h3(cls := s"${themeClass}__name")(
            a(href := routes.Puzzle.show(variant.key, key.value))(PuzzleTheme(key).name())
          ),
          p(cls := s"${themeClass}__description")(PuzzleTheme(key).description())
        ),
        metricsOf(days, variant, key, results)
      )
    }

  private def metricsOf(
      days: Int,
      variant: Variant,
      theme: PuzzleTheme.Key,
      results: PuzzleDashboard.Results
  )(implicit
      lang: Lang
  ) =
    div(cls := s"${baseClass}__metrics")(
      div(cls := s"$metricClass $metricClass--played")(
        strong(results.nb.localize),
        span("played")
      ),
      div(cls := s"$metricClass $metricClass--perf")(
        strong(results.performance, results.unclear ?? "?"),
        span("performance")
      ),
      div(
        cls := s"$metricClass $metricClass--win",
        style := s"--first:${results.firstWinPercent}%;--win:${results.winPercent}%"
      )(
        strong(s"${results.winPercent}%"),
        span("solved")
      ),
      a(
        cls := s"$metricClass $metricClass--fix",
        href := results.canReplay.option(routes.Puzzle.replay(variant.key, days, theme.value).url)
      )(
        results.canReplay option span(cls := s"$metricClass--fix__text")(
          strong(results.unfixed),
          span("to replay")
        ),
        iconTag(if (results.canReplay) 'G' else 'E')
      )
    )
}
