package views.html
package puzzle

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import strategygames.variant.Variant

import controllers.routes

object faq {

  import trans.puzzle._

  def apply(variant: Variant)(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.puzzle.puzzleFAQ().toString,
      moreCss = cssTag("page")
    ) {
      main(cls := "page-menu page")(
        bits.pageMenu("faq", variant),
        div(cls := "page-menu__content box box-pad body")(
          questionsAndAnswers
        )
      )
    }

  def questionsAndAnswers(implicit ctx: Context) =
    frag(
      h1(trans.puzzle.puzzleFAQ()),
      h2(trans.puzzle.whatPuzzlesAreAvailable()),
      p(trans.puzzle.whatPuzzlesAreAvailableANS()),
      h2(trans.puzzle.whatArePuzzleThemes()),
      p(trans.puzzle.whatArePuzzleThemesANS()),
      h2(trans.puzzle.whatIsPuzzleRating()),
      p(trans.puzzle.whatIsPuzzleRatingANS()),
      h2(trans.puzzle.howCalculateNextPuzzle()),
      p(trans.puzzle.howCalculateNextPuzzleANS()),
      h2(trans.puzzle.whyLoopingPuzzles()),
      p(trans.puzzle.whyLoopingPuzzlesANS()),
      h2(trans.puzzle.differenceBetweenRatingAndPerformance()),
      p(trans.puzzle.differenceBetweenRatingAndPerformanceANS()),
      h2(trans.puzzle.howDailyPuzzleChosen()),
      p(trans.puzzle.howDailyPuzzleChosenANS()),
      h2(trans.puzzle.whyMySolutionIsIncorrect()),
      p(trans.puzzle.whyMySolutionIsIncorrectANS()),
      h2(trans.puzzle.futurePuzzlePlans()),
      p(trans.puzzle.futurePuzzlePlansANS())
    )
}
