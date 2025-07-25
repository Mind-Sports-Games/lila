package controllers

import strategygames.variant.Variant

import lila.app._

final class Library(env: Env) extends LilaController(env) {

  def home =
    Open { implicit ctx =>
      {
        env.game.cached.monthlyGames.flatMap { data =>
          Ok(views.html.library.home(data)).fuccess
        }
      }
    }

  def variant(key: String) =
    Open { implicit ctx =>
      Variant.all.find(_.key == key) match {
        case Some(variant) => {
          env.game.cached.monthlyGames flatMap { libraryStats =>
            Ok(views.html.library.show(variant, libraryStats)).fuccess
          }
        }
        case None => NotFound("Variant not found").fuccess
      }
    }

}
