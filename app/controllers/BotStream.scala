package controllers

import lila.app.*
import lila.user.Holder
import views.*

final class BotStream(env: Env) extends LilaController(env) {

  private def api         = env.bot.streamApi
  private def coordinator = env.bot.coordinator

  def manager =
    Secure(_.ManageBotStreams) { implicit ctx => _ =>
      api.list flatMap { streams =>
        val status = coordinator.liveStatus
        env.game.gameRepo.gamesFromSecondary(status.values.flatMap(_.currentGame).toSeq) map { games =>
          html.botStream.manager(streams, coordinator, status, games)
        }
      }
    }

  def form =
    Secure(_.ManageBotStreams) { implicit ctx => _ =>
      Ok(html.botStream.create(api.createForm)).fuccess
    }

  def create =
    SecureBody(_.ManageBotStreams) { implicit ctx => me =>
      implicit val req = ctx.body
      api.withUniqueName(api.createForm.bindFromRequest(), none) flatMap {
        _.fold(
          err => BadRequest(html.botStream.create(err)).fuccess,
          data =>
            api.create(data, me.id) flatMap { stream =>
              modLog(me, "create", stream) inject Redirect(routes.BotStream.edit(stream.id)).flashSuccess
            }
        )
      }
    }

  def edit(id: String) =
    Secure(_.ManageBotStreams) { implicit ctx => _ =>
      OptionOk(api.one(id)) { stream =>
        html.botStream.edit(stream, api.editForm(stream))
      }
    }

  def update(id: String) =
    SecureBody(_.ManageBotStreams) { implicit ctx => me =>
      OptionFuResult(api.one(id)) { stream =>
        implicit val req = ctx.body
        api.withUniqueName(api.editForm(stream).bindFromRequest(), stream.id.some) flatMap {
          _.fold(
            err => BadRequest(html.botStream.edit(stream, err)).fuccess,
            data =>
              api.update(stream, data) >> modLog(me, "update", stream) inject
                Redirect(routes.BotStream.edit(id)).flashSuccess
          )
        }
      }
    }

  def cloneS(id: String) =
    Secure(_.ManageBotStreams) { implicit ctx => _ =>
      OptionOk(api.one(id)) { old =>
        html.botStream.create(api.editForm(api.clone(old)))
      }
    }

  def start(id: String)   = command(id, "start")(coordinator.start)
  def stop(id: String)    = command(id, "stop")(coordinator.stop)
  def restart(id: String) = command(id, "restart")(coordinator.restart)

  def delete(id: String) =
    command(id, "delete")(stream => api.delete(stream) inject s"Bot stream '${stream.name}' deleted")

  private def command(id: String, action: String)(f: lila.bot.BotStream => Fu[String]) =
    SecureBody(_.ManageBotStreams) { implicit ctx => me =>
      OptionFuResult(api.one(id)) { stream =>
        f(stream) flatMap { message =>
          modLog(me, action, stream) inject Redirect(routes.BotStream.manager).flashSuccess(message)
        }
      }
    }

  private def modLog(me: Holder, action: String, stream: lila.bot.BotStream) =
    env.mod.logApi.botStream(me.id, action, stream.id, stream.name)
}
