package views
package html.puzzle

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.puzzle.{ Puzzle, PuzzleTheme }
import strategygames.variant.Variant
import lila.i18n.{ VariantKeys }

object theme {

  def list(variant: Variant, themes: List[(lila.i18n.I18nKey, List[PuzzleTheme.WithCount])])(implicit
      ctx: Context
  ) =
    views.html.base.layout(
      title = "Puzzle themes",
      moreCss = cssTag("puzzle.page")
    )(
      main(cls := "page-menu")(
        bits.pageMenu("themes", variant),
        div(cls := "page-menu__content box")(
          h1(trans.puzzle.puzzleThemes()),
          div(cls := "puzzle-themes__variant_select")(
            div(cls := "variant_group")(
              Puzzle.puzzleVariants.map { v =>
                button(cls := s"variant ${if (v.key == variant.key) "selected" else ""}")(
                  a(
                    href := routes.Puzzle.themes(v.key),
                    dataIcon := v.perfIcon
                  )(VariantKeys.variantName(v))
                )
              }
            )
          ),
          div(cls := "puzzle-themes")(
            themes map { case (cat, themes) =>
              frag(
                h2(cat()),
                div(
                  cls := List(
                    "puzzle-themes__list"     -> true,
                    cat.key.replace(":", "-") -> true
                  )
                )(
                  themes.map { pt =>
                    val url =
                      if (pt.theme == PuzzleTheme.mix) routes.Puzzle.home(variant.key)
                      else routes.Puzzle.show(variant.key, pt.theme.key.value)
                    a(cls := "puzzle-themes__link", href := (pt.count > 0).option(url.url))(
                      span(
                        h3(
                          pt.theme.name(),
                          em(pt.count.localize)
                        ),
                        span(pt.theme.description())
                      )
                    )
                  },
                  cat.key == "puzzle:origin" option
                    a(cls := "puzzle-themes__link", href := routes.Puzzle.ofPlayer(variant.key))(
                      span(
                        h3("Player games"),
                        span("Lookup puzzles generated from your games, or from another player's games.")
                      )
                    )
                )
              )
            }
            // p(cls := "puzzle-themes__db text", dataIcon := "")(
            //   "These puzzles are in the public domain, and can be downloaded from ",
            //   a(href := "https://database.playstrategy.org/")("database.playstrategy.org"),
            //   "."
            // )
          )
        )
      )
    )
}
