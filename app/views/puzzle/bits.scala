package views
package html.puzzle

import strategygames.format.FEN

import controllers.routes
import play.api.i18n.Lang
import play.api.libs.json.{ JsString, Json }
import strategygames.variant.Variant
import lila.puzzle.Puzzle

import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.i18n.{ MessageKey, VariantKeys }
import lila.puzzle.{ PuzzleDifficulty, PuzzleTheme }

object bits {

  private val dataLastmove = attr("data-lastmove")

  def daily(p: lila.puzzle.Puzzle, fen: FEN, lastMove: String) =
    views.html.board.bits.miniForVariant(fen, p.variant, p.playerIndex, lastMove)(span)

  def jsI18n(streak: Boolean)(implicit lang: Lang) =
    if (streak) i18nJsObject(streakI18nKeys)
    else
      i18nJsObject(trainingI18nKeys) + (PuzzleTheme.enPassant.key.value -> JsString(
        PuzzleTheme.enPassant.name.txt()(lila.i18n.defaultLang)
      ))

  def jsonThemes(variant: Variant) = PuzzleTheme
    .allByVariant(variant)
    .collect {
      case t if t != PuzzleTheme.mix => t.key
    }
    .partition(PuzzleTheme.staticThemes.contains) match {
    case (static, dynamic) =>
      Json.obj(
        "dynamic" -> dynamic.map(_.value).sorted.mkString(" "),
        "static"  -> static.map(_.value).mkString(" ")
      )
  }

  def variantSelector(variant: Variant, link: Variant => String)(implicit lang: Lang) =
    div(cls := s"variant_group")(
      Puzzle.puzzleVariants.map { v =>
        button(cls := s"variant ${if (v.key == variant.key) "selected" else ""}")(
          a(
            href := link(v),
            dataIcon := v.perfIcon
          )(VariantKeys.variantName(v))
        )
      }
    )

  def pageMenu(active: String, variant: Variant, days: Int = 30)(implicit lang: Lang) =
    st.nav(cls := "page-menu__menu subnav")(
      a(href := routes.Puzzle.home(variant.key))(
        trans.puzzles()
      ),
      a(cls := active.active("themes"), href := routes.Puzzle.themes(variant.key))(
        trans.puzzle.puzzleThemes()
      ),
      a(cls := active.active("dashboard"), href := routes.Puzzle.dashboard(variant.key, days, "dashboard"))(
        trans.puzzle.puzzleDashboard()
      ),
      //TODO we can put this back once we have more themes for our puzzles
      // a(
      //   cls := active.active("improvementAreas"),
      //   href := routes.Puzzle.dashboard(variant.key, days, "improvementAreas")
      // )(
      //   trans.puzzle.improvementAreas()
      // ),
      // a(cls := active.active("strengths"), href := routes.Puzzle.dashboard(variant.key, days, "strengths"))(
      //   trans.puzzle.strengths()
      // ),
      a(cls := active.active("history"), href := routes.Puzzle.history(variant.key, 1))(
        trans.puzzle.history()
      ),
      a(cls := active.active("player"), href := routes.Puzzle.ofPlayer(variant.key))(
        "From my games"
      ),
      a(cls := active.active("faq"), href := routes.Puzzle.faq(variant.key.some))(
        trans.puzzle.puzzleFAQ()
      )
    )

  private val baseI18nKeys: List[MessageKey] =
    List(
      trans.puzzle.bestMove,
      trans.puzzle.keepGoing,
      trans.puzzle.notTheMove,
      trans.puzzle.trySomethingElse,
      trans.yourTurn,
      trans.variant,
      trans.puzzle.findTheBestMoveForPlayerIndex,
      trans.viewTheSolution,
      trans.puzzle.puzzleSuccess,
      trans.puzzle.puzzleComplete,
      trans.puzzle.hidden,
      trans.puzzle.jumpToNextPuzzleImmediately,
      trans.puzzle.fromGameLink,
      trans.puzzle.puzzleId,
      trans.puzzle.ratingX,
      trans.puzzle.playedXTimes,
      trans.puzzle.continueTraining,
      trans.puzzle.didYouLikeThisPuzzle,
      trans.puzzle.voteToLoadNextOne,
      trans.analysis,
      trans.playWithTheMachine,
      trans.preferences.zenMode,
      // ceval
      trans.depthX,
      trans.usingServerAnalysis,
      trans.loadingEngine,
      trans.cloudAnalysis,
      trans.goDeeper,
      trans.showThreat,
      trans.gameOver,
      trans.inLocalBrowser,
      trans.toggleLocalEvaluation
    ).map(_.key)

  private val trainingI18nKeys: List[MessageKey] =
    baseI18nKeys ::: List(
      trans.puzzle.example,
      trans.puzzle.addAnotherTheme,
      trans.puzzle.yourPuzzleRatingX,
      trans.puzzle.difficultyLevel,
      trans.signUp,
      trans.puzzle.toGetPersonalizedPuzzles,
      trans.puzzle.nbPointsBelowYourPuzzleRating,
      trans.puzzle.nbPointsAboveYourPuzzleRating
    ).map(_.key) :::
      PuzzleTheme.all.map(_.name.key) :::
      PuzzleTheme.all.map(_.description.key) :::
      PuzzleDifficulty.all.map(_.name.key)

  private val streakI18nKeys: List[MessageKey] =
    baseI18nKeys ::: List(
      trans.storm.skip,
      trans.puzzle.streakDescription,
      trans.puzzle.yourStreakX,
      trans.puzzle.streakSkipExplanation,
      trans.puzzle.continueTheStreak,
      trans.puzzle.newStreak
    ).map(_.key)
}
