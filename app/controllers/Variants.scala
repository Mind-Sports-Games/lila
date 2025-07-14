package controllers

import strategygames.variant.Variant

import lila.app._

final class Variants(env: Env) extends LilaController(env) {

  def home =
    Open { implicit ctx =>
      {
        env.game.cached.monthlyGames.flatMap { data =>
          Ok(views.html.site.variants.home(data.filter(_._2 == "0_1"))).fuccess
        }
      }
    }

  def variant(key: String) =
    Open { implicit ctx =>
      Variant.all.find(_.key == key) match {
        case Some(variant) => Ok(views.html.site.variants.show(variant)).fuccess
        case None          => NotFound("Variant not found").fuccess
      }
    }

}
