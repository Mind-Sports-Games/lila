package lila.bot

import com.softwaremill.macwire.*
import lila.socket.IsOnline

@Module
final class Env(
    chatApi: lila.chat.ChatApi,
    gameRepo: lila.game.GameRepo,
    userRepo: lila.user.UserRepo,
    lightUserApi: lila.user.LightUserApi,
    challengeApi: lila.challenge.ChallengeApi,
    rematches: lila.game.Rematches,
    isOfferingRematch: lila.round.IsOfferingRematch,
    spam: lila.security.Spam,
    isOnline: IsOnline,
    appConfig: play.api.Configuration
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem,
    scheduler: akka.actor.Scheduler,
    mode: play.api.Mode
) {

  lazy val jsonView = wire[BotJsonView]

  lazy val gameStateStream = wire[GameStateStream]

  lazy val player = wire[BotPlayer]

  lazy val onlineApiUsers: OnlineApiUsers = wire[OnlineApiUsers]

  lazy val coordinator: BotVsBotCoordinator = wire[BotVsBotCoordinator]

  val form = BotForm

  coordinator.init()
}
