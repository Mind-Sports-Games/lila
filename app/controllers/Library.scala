package controllers

import strategygames.variant.Variant

import lila.app._
import lila.memo.CacheApi._
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
      Variant.all.find(_.key == key) match {
        case Some(variant) => {
          val tvChannel = lila.tv.Tv.Channel.byKey.get(variant.key)
          for {
            monthlyGameData <- env.game.cached.monthlyGames
            winRates        <- env.game.cached.gameWinRates
            leaderboards    <- env.user.cached.top10.get {}
            leaderboard = leaderboards.forVariant(variant)
            tours        <- env.tournament.cached.onLibraryPage.getUnit.nevermind
            filteredTours = tours.filter(_.variant.key == variant.key)
            featuredGame <- tvChannel.fold(fuccess(none[lila.game.Game]))(env.tv.tv.getGame)
            dailyPuzzle <- Puzzle.puzzleVariants
              .exists(_.key == variant.key)
              .??(env.puzzle.daily.getForVariant(variant))
          } yield Ok(views.html.library.show(variant, monthlyGameData, winRates, leaderboard, filteredTours, featuredGame, dailyPuzzle))
        }
        case None => NotFound("Variant not found").fuccess
      }
    }

}
