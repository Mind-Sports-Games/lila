package controllers

import strategygames.variant.Variant

import lila.app._

final class Library(env: Env) extends LilaController(env) {

  def home =
    Open { implicit ctx =>
      {
        env.game.cached.monthlyGames.flatMap { monthlyGameData =>
          env.game.cached.gameClockRates flatMap { clockRates =>
            Ok(views.html.library.home(monthlyGameData, clockRates)).fuccess
          }
        }
      }
    }

  def variant(key: String) =
    Open { implicit ctx =>
      Variant.all.find(_.key == key) match {
        case Some(variant) => {
          env.game.cached.monthlyGames flatMap { monthlyGameData =>
            env.game.cached.gameWinRates flatMap { winRates =>
              Ok(views.html.library.show(variant, monthlyGameData, winRates)).fuccess
            }
          }
        }
        case None => NotFound("Variant not found").fuccess
      }
    }

}
