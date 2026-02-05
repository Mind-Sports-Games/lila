package controllers

import strategygames.variant.Variant

import lila.app._

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
          for {
            monthlyGameData <- env.game.cached.monthlyGames
            winRates        <- env.game.cached.gameWinRates
            leaderboards    <- env.user.cached.top10.get {}
            leaderboard = leaderboards.forVariant(variant)
          } yield Ok(views.html.library.show(variant, monthlyGameData, winRates, leaderboard))
        }
        case None => NotFound("Variant not found").fuccess
      }
    }

}
