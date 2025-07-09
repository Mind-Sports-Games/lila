package controllers

import strategygames.variant.Variant

import lila.app._

final class Variants(env: Env) extends LilaController(env) {

  def home =
    Open { implicit ctx =>
      Ok(views.html.site.variants.home).fuccess
    }

  def variant(key: String) =
    Open { implicit ctx =>
      Variant.all.find(_.key == key) match {
        case Some(variant) => Ok(views.html.site.variants.show(variant)).fuccess
        case None          => NotFound("Variant not found").fuccess
      }
    }

}
