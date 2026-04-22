package controllers

import play.api.mvc.*

import lila.api.Context
import lila.app.*
import views.*
import lila.pref.PieceSet
import lila.pref.Theme
import lila.pref.JsonView.*

import play.api.libs.json.*

final class Pref(env: Env) extends LilaController(env) {

  private def api   = env.pref.api
  private def forms = lila.pref.PrefForm

  def apiGet =
    Scoped(_.Preference.Read) { _ => me =>
      env.pref.api.getPref(me) map { prefs =>
        JsonOk {
          import play.api.libs.json.*
          import lila.pref.JsonView.*
          Json.obj("prefs" -> prefs)
        }
      }
    }

  def form(categSlug: String) =
    Auth { implicit ctx => me =>
      lila.pref.PrefCateg(categSlug) match {
        case None        => notFound
        case Some(categ) =>
          Ok(html.account.pref(me, forms.prefOf(ctx.pref), categ)).fuccess
      }
    }

  def formApply =
    AuthBody { implicit ctx => _ =>
      def onSuccess(data: lila.pref.PrefForm.PrefData) = api.setPref(data(ctx.pref)) inject Ok("saved")
      implicit val req                                 = ctx.body
      forms.pref
        .bindFromRequest()
        .fold(
          _ =>
            forms.pref
              .bindFromRequest(lila.pref.FormCompatLayer(ctx.pref, ctx.body))
              .fold(
                err => BadRequest(err.toString).fuccess,
                onSuccess
              ),
          onSuccess
        )
    }

  def set(name: String) =
    OpenBody { implicit ctx =>
      if (name == "zoom") {
        Ok.withCookies(env.lilaCookie.session("zoom2", (getInt("v") | 185).toString)).fuccess
      } else {
        implicit val req = ctx.body
        (setters get name) so { case (form, fn) =>
          FormResult(form) { v =>
            fn(v, ctx) map { cookie =>
              Ok(()).withCookies(cookie)
            }
          }
        }
      }
    }

  def updatePieceSet(gameFamily: String) =
    OpenBody { implicit ctx =>
      implicit val req = ctx.body
      FormResult(forms.pieceSet) { v =>
        updatePieceSetForFamily(gameFamily, v, ctx) map { cookie =>
          Ok(()).withCookies(cookie)
        }
      }
    }

  def updateTheme(gameFamily: String) =
    OpenBody { implicit ctx =>
      implicit val req = ctx.body
      FormResult(forms.theme) { v =>
        updateThemeForFamily(gameFamily, v, ctx) map { cookie =>
          Ok(()).withCookies(cookie)
        }
      }
    }

  private lazy val setters = Map(
    "theme3d"    -> (forms.theme3d    -> save("theme3d")),
    "pieceSet3d" -> (forms.pieceSet3d -> save("pieceSet3d")),
    "soundSet"   -> (forms.soundSet   -> save("soundSet")),
    "bg"         -> (forms.bg         -> save("bg")),
    "bgImg"      -> (forms.bgImg      -> save("bgImg")),
    "color"      -> (forms.color      -> save("color")),
    "is3d"       -> (forms.is3d       -> save("is3d")),
    "zen"        -> (forms.zen        -> save("zen"))
  )

  private def updatePieceSetForFamily(gameFamily: String, value: String, ctx: Context): Fu[Cookie] =
    ctx.me match {
      case Some(u) =>
        api
          .updatePrefPieceSet(u, gameFamily, value)
          .map(j => env.lilaCookie.session("pieceSet", j)(using ctx.req))
      case _ => // get PieceSet pref from session and update the cookie
        val currentPS = ctx.req.session
          .get("pieceSet")
          .fold(PieceSet.defaults)(p =>
            Json.parse(p).validate(using pieceSetsRead).getOrElse(PieceSet.defaults)
          )
        val newPS = PieceSet.updatePieceSet(currentPS, value)
        val j     = Json.toJson(newPS).toString
        fuccess(env.lilaCookie.session("pieceSet", j)(using ctx.req))
    }

  private def updateThemeForFamily(gameFamily: String, value: String, ctx: Context): Fu[Cookie] =
    ctx.me match {
      case Some(u) =>
        api.updatePrefTheme(u, gameFamily, value).map(j => env.lilaCookie.session("theme", j)(using ctx.req))
      case _ => // get Theme pref from session and update the cookie
        val currentT = ctx.req.session
          .get("theme")
          .fold(Theme.defaults)(p => Json.parse(p).validate(using themesRead).getOrElse(Theme.defaults))
        val newT = Theme.updateBoardTheme(currentT, value, gameFamily)
        val j    = Json.toJson(newT).toString
        fuccess(env.lilaCookie.session("theme", j)(using ctx.req))
    }

  private def save(name: String)(value: String, ctx: Context): Fu[Cookie] =
    ctx.me so {
      api.setPrefString(_, name, value)
    } inject env.lilaCookie.session(name, value)(using ctx.req)
}
