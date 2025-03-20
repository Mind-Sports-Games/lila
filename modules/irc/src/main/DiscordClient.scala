package lila.irc

import play.api.libs.json._
import play.api.libs.ws.JsonBodyWritables._
import play.api.libs.ws.StandaloneWSClient
import scala.concurrent.duration._

import lila.common.config.Secret
import lila.memo.RateLimit

final private class DiscordClient(ws: StandaloneWSClient, urlMatchMaking: Secret, urlTournaments: Secret)(
    implicit ec: scala.concurrent.ExecutionContext
) {

  private val limiter = new RateLimit[DiscordMessage](
    credits = 1,
    duration = 15 minutes,
    key = "discord.client"
  )

  private def urlForMsg(msg: DiscordMessage) = msg.channel match {
    case MatchMaking => urlMatchMaking.value
    case Tournaments => urlTournaments.value
  }

  def apply(msg: DiscordMessage): Funit =
    limiter(msg) {
      if (urlForMsg(msg).isEmpty) fuccess(lila.log("discord").info(msg.toString))
      else
        ws.url(urlForMsg(msg))
          .post(
            Json.obj("content" -> msg.text).noNull
          )
          .flatMap {
            case res if res.status == 200 => funit
            case res                      => fufail(s"[discord] ${urlForMsg(msg)} $msg ${res.status} ${res.body}")
          }
          .nevermind
    }(funit)
}
