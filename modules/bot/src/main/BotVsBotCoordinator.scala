package lila.bot

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger, AtomicReference }
import scala.concurrent.ExecutionContext
import akka.actor.Scheduler
import play.api.Configuration

import lila.challenge.{ Challenge, ChallengeApi }
import lila.common.Bus
import lila.game.{ Game, actorApi }
import lila.user.UserRepo

final class BotVsBotCoordinator(
    userRepo: UserRepo,
    challengeApi: ChallengeApi,
    onlineApiUsers: OnlineApiUsers,
    appConfig: Configuration
)(implicit ec: ExecutionContext, scheduler: Scheduler) {

  private val enabled     = new AtomicBoolean(appConfig.getOptional[Boolean]("autoBotvsBot.enabled") | false)
  private val running     = new AtomicBoolean(false)
  private val currentGame = new AtomicReference[Option[Game.ID]](none)
  private val allGames    = BotVsBotConfig.allGames
  private val cycleIdx    = new AtomicInteger(scala.util.Random.nextInt(allGames.length))

  Bus.subscribeFun("finishGame") {
    case actorApi.FinishGame(game, _, _)
        if currentGame.get().contains(game.id) && running.get() =>
      currentGame.set(none)
      scheduler.scheduleOnce(2.seconds)(startNextGame().discard)
  }

  Bus.subscribeFun("botVsBot") {
    case lila.hub.actorApi.bot.BotVsBotStart => start().discard
    case lila.hub.actorApi.bot.BotVsBotStop  => stop()
    case lila.hub.actorApi.bot.BotVsBotStatus(promise) =>
      val msg =
        if (running.get())
          currentGame.get().fold("Running — no game in progress")(id => s"Running — current game: $id")
        else "Stopped"
      promise.success(msg)
  }

  def start(): Funit =
    if (running.get()) fuccess(logger.info("Bot vs bot already running"))
    else {
      logger.info(s"Bot vs bot starting — ${allGames.length} matchups in cycle")
      running.set(true)
      startNextGame()
    }

  def stop(): Unit = {
    running.set(false)
    logger.info("Bot vs bot stopped")
  }

  private[bot] def init(): Unit =
    if (enabled.get()) {
      logger.info("Bot vs bot auto-start enabled, starting on init")
      start().discard
    } else logger.info("Bot vs bot auto-start disabled in config (use CLI to start manually)")

  private def startNextGame(): Funit = {
    val online = onlineApiUsers.get
    logger.info(s"Bot vs bot seeking next game — online API users: ${online.mkString(", ").some.filter(_.nonEmpty) | "(none)"}")
    findNextAvailableGame(cycleIdx.get(), 0, online) match {
      case Some((idx, spec)) =>
        logger.info(s"Bot vs bot matched: ${spec.p1.name} vs ${spec.p2.name} (${spec.variant.name})")
        cycleIdx.set((idx + 1) % allGames.length)
        createGame(spec)
      case None =>
        val needed = BotVsBotConfig.allBotIds
        val missing = needed.diff(online)
        logger.info(s"Bot vs bot: no bots available — need ${needed.mkString(", ")}, missing ${missing.mkString(", ")}, retrying in 30s")
        scheduler.scheduleOnce(30.seconds)(startNextGame().discard)
        funit
    }
  }

  private def findNextAvailableGame(startIdx: Int, attempts: Int, online: Set[String]): Option[(Int, BotVsBotGame)] =
    if (attempts >= allGames.length) none
    else {
      val idx  = startIdx % allGames.length
      val spec = allGames(idx)
      if (online.contains(spec.p1.id) && online.contains(spec.p2.id)) Some((idx, spec))
      else findNextAvailableGame(idx + 1, attempts + 1, online)
    }

  private def createGame(spec: BotVsBotGame): Funit = {
    import lila.challenge.Challenge.*
    val timeControl = TimeControl.Clock(spec.clock)
    userRepo.namePair(spec.p1.id, spec.p2.id) flatMap {
      _.fold(funit) { case (p1User, p2User) =>
        val challenge = Challenge.make(
          variant = spec.variant,
          fenVariant = none,
          initialFen = none,
          timeControl = timeControl,
          mode = strategygames.Mode.Casual,
          playerIndex = "p1",
          challenger = toRegistered(spec.variant, timeControl)(p1User),
          destUser = p2User.some,
          rematchOf = none
        )
        challengeApi.create(challenge) flatMap {
          case false =>
            logger.warn(
              s"Failed to create bot-vs-bot challenge: ${spec.p1.name} vs ${spec.p2.name} (${spec.variant.name})"
            )
            funit
          case true =>
            challengeApi.oauthAccept(p2User, challenge) flatMap {
              case Some(game) =>
                logger.info(s"Bot vs bot game started: ${spec.p1.name} vs ${spec.p2.name} (${spec.variant.name}) — game ${game.id}")
                currentGame.set(game.id.some)
                funit
              case None =>
                logger.warn(
                  s"Failed to accept bot-vs-bot challenge: ${spec.p1.name} vs ${spec.p2.name} (${spec.variant.name}), trying next matchup"
                )
                scheduler.scheduleOnce(5.seconds)(startNextGame().discard)
                funit
            }
        }
      }
    }
  }
}
