package controllers

import play.api.mvc._
import play.api.libs.json.Json
import scalatags.Text.all.Frag

import lila.api.Context
import lila.app._
import lila.memo.CacheApi._
import views._

final class KeyPages(env: Env)(implicit ec: scala.concurrent.ExecutionContext) {

  def home(status: Results.Status)(implicit ctx: Context): Fu[Result] =
    homeHtml
      .map { html =>
        env.lilaCookie.ensure(ctx.req)(status(html))
      }

  def homeHtml(implicit ctx: Context): Fu[Frag] =
    env
      .preloader(
        posts = env.forum.recent(ctx.me, env.team.cached.teamIdsList).nevermind,
        tours = env.tournament.cached.onHomepage.getUnit.nevermind,
        events = env.event.api.promoteTo(ctx.req).nevermind,
        simuls = env.simul.allCreatedFeaturable.get {}.nevermind,
        streamerSpots = env.streamer.homepageMaxSetting.get(),
        weeklyChallenge = env.lobby.weeklyChallenge,
        chatOption = ctx.noKid ?? env.chat.api.userChat.cached
          .findMine(lila.chat.Chat.Id("lobbyhome"), ctx.me)
          .map(some),
        chatVersion = ctx.noKid ?? env.lobby.version("lobbyhome").dmap(some)
      )
      .mon(_.lobby segment "preloader.total")
      .map { h =>
        lila.mon.chronoSync(_.lobby segment "renderSync") {
          html.lobby.home(h)
        }
      }

  def notFound(ctx: Context): Result = {
    Results.NotFound(html.base.notFound()(ctx))
  }

  def p2listed(implicit ctx: Context): Result =
    if (lila.api.Mobile.Api requested ctx.req)
      Results.Unauthorized(
        Json.obj(
          "error" -> html.site.message.p2listedMessage
        )
      )
    else Results.Unauthorized(html.site.message.p2listedMessage)
}
