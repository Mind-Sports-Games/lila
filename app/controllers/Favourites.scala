package controllers

import play.api.libs.json.Json

import lila.api.Context
import lila.app.*
import lila.user.User as UserModel
import views.html.favourites.{ groupByKey, variantByKey }

final class Favourites(env: Env) extends LilaController(env) {

  private val ToggleLimitPerUser = new lila.memo.RateLimit[UserModel.ID](
    credits = 100,
    duration = 10.minutes,
    key = "favourites.toggle"
  )

  def index =
    Auth { implicit ctx => me =>
      Ok(views.html.favourites(me.favVariants)).fuccess
    }

  def variant(key: String) =
    Auth { implicit ctx => me =>
      XhrOnly {
        variantByKey(key).fold(notFoundJson()) { v => toggle(me, List(v.key)) }
      }
    }

  def group(key: String) =
    Auth { implicit ctx => me =>
      XhrOnly {
        groupByKey(key).fold(notFoundJson()) { case (_, variants) => toggle(me, variants.map(_.key)) }
      }
    }

  private def toggle(me: UserModel, keys: List[String])(implicit ctx: Context) =
    ToggleLimitPerUser(me.id) {
      env.user.repo.setFavVariants(me.id, keys, getBool("v")) map { favs =>
        JsonOk(Json.obj("favourites" -> favs.toList.sorted))
      }
    }(rateLimitedFu)
}
