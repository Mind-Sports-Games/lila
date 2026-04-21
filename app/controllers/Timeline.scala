package controllers

import play.api.libs.json.*

import lila.app.*
import lila.common.config.Max
import lila.common.extensions.*
import lila.common.HTTPRequest
import lila.timeline.Entry.entryWrites
import views.*

final class Timeline(env: Env) extends LilaController(env) {

  def home =
    Auth { implicit ctx => me =>
      negotiate(
        html =
          if HTTPRequest.isXhr(ctx.req) then
            env.timeline.entryApi
              .userEntries(me.id)
              .logTimeIfGt(s"timeline site entries for ${me.id}", 10.seconds)
              .map { html.timeline.entries(_) }
          else
            env.timeline.entryApi
              .moreUserEntries(me.id, Max(30))
              .map { html.timeline.more(_) }
        ,
        _ =>
          env.timeline.entryApi
            .moreUserEntries(me.id, Max(getInt("nb") | 10).atMost(env.apiTimelineSetting.get()))
            .map { es =>
              Ok(Json.obj("entries" -> es))
            }
      )
    }

  def unsub(channel: String) =
    Auth { implicit ctx => me =>
      env.timeline.unsubApi.set(channel, me.id, ~get("unsub") == "on")
    }
}
