package controllers

import play.api.mvc.Result
import strategygames.variant.Variant

import lila.api.Context
import lila.app.{ *, given }
import lila.memo.CacheApi.*
import lila.puzzle.Puzzle

final class Library(env: Env) extends LilaController(env) {

  def home =
    Open { implicit ctx =>
      {
        for {
          monthlyGameData <- env.game.cached.monthlyGames
          clockRates      <- env.game.libraryStats.gameClockRates
          // botOrHumanGames <- env.game.libraryStats.botOrHumanGames
        } yield Ok(views.html.library.home(monthlyGameData, clockRates))
      }
    }

  def variant(key: String) =
    Open { implicit ctx =>
      views.html.library.bits.canonicalVariantKey(key) match {
        case Some(canonical) => MovedPermanently(routes.Library.variant(canonical).url).fuccess
        case None            => showVariant(key)
      }
    }

  def langVariant(lang: String, key: String) =
    views.html.library.bits.canonicalVariantKey(key) match {
      case Some(canonical) =>
        Action(MovedPermanently(routes.Library.langVariant(lang, canonical).url))
      case None =>
        LangPage(routes.Library.variant(key).url)(ctx => showVariant(key)(using ctx))(lang)
    }

  private def showVariant(key: String)(implicit ctx: Context): Fu[Result] =
    Variant.all.find(_.key == key) match {
      case Some(variant) => {
        val tvChannel = lila.tv.Tv.Channel.byKey.get(variant.key)
        for {
          monthlyGameData <- env.game.cached.monthlyGames
          winRates        <- env.game.cached.gameWinRates
          leaderboards    <- env.user.cached.top10.get {}
          leaderboard = leaderboards.forVariant(variant)
          tours <- env.tournament.cached.onLibraryPage.getUnit.recoverDefault
          filteredTours = tours.filter(_.variant.key == variant.key)
          featuredGame <- tvChannel
            .map(env.tv.tv.getGame)
            .getOrElse(fuccess(none))
            .orElse(env.game.gameRepo.randomByVariant(variant))
          dailyPuzzle <- Puzzle.puzzleVariants
            .exists(_.key == variant.key)
            .so(env.puzzle.daily.getForVariant(variant))
          studies  <- env.study.notable.byVariant(variant, 6)
          gpArenas <- env.tournament.tournamentRepo
            .finishedSeriesOfTeam(views.html.library.bits.msoTeamId, lila.common.Freq.MSOGP, variant)
          gpSwisses <- env.swiss.api
            .finishedNamed(views.html.library.bits.msoTeamId, views.html.library.bits.msoGrandPrixName, variant)
        } yield Ok(
          views.html.library
            .show(
              variant,
              monthlyGameData,
              winRates,
              leaderboard,
              filteredTours,
              featuredGame,
              dailyPuzzle,
              studies,
              views.html.library.bits.grandPrixEditions(gpArenas, gpSwisses)
            )
        )
      }
      case None => NotFound("Variant not found").fuccess
    }

}
