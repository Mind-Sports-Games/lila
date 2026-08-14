package lila.bot

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger, AtomicReference }
import scala.concurrent.ExecutionContext
import akka.actor.{ Cancellable, Scheduler }
import play.api.Configuration
import strategygames.GameFamily
import strategygames.format.FEN
import strategygames.variant.Variant

import lila.challenge.{ Challenge, ChallengeApi }
import lila.common.Bus
import lila.game.{ Game, actorApi }
import lila.hub.actorApi.map.Tell
import lila.hub.actorApi.round.NextGameLinked
import lila.user.UserRepo

final class BotVsBotCoordinator(
    userRepo: UserRepo,
    challengeApi: ChallengeApi,
    onlineApiUsers: OnlineApiUsers,
    appConfig: Configuration
)(implicit ec: ExecutionContext, scheduler: Scheduler) {

  private val enabled = new AtomicBoolean(appConfig.getOptional[Boolean]("autoBotvsBot.enabled") | false)

  private class StreamRunner(val stream: BotVsBotStream) {
    private val running     = new AtomicBoolean(false)
    private val currentGame = new AtomicReference[Option[Game.ID]](none)
    private val watchdog    = new AtomicReference[Option[Cancellable]](none)
    private val cycleIdx    = new AtomicInteger(scala.util.Random.nextInt(stream.games.length))
    private val botIds      = stream.games.flatMap(g => List(g.p1.id, g.p2.id)).toSet

    def isRunning: Boolean = running.get()

    def ownsGame(gameId: Game.ID): Boolean = currentGame.get().contains(gameId)

    def start(): Funit =
      if (!running.compareAndSet(false, true)) fuccess(logger.info(s"[${stream.name}] already running"))
      else {
        logger.info(s"[${stream.name}] starting — ${stream.games.length} games in cycle")
        startNextGame()
      }

    def stop(): Unit = {
      running.set(false)
      watchdog.getAndSet(none).foreach(_.cancel())
      logger.info(s"[${stream.name}] stopped")
    }

    def status(): String = {
      val cyclePos = s"${cycleIdx.get() + 1}/${stream.games.length}"
      if (running.get())
        currentGame.get().fold(s"[${stream.name}] Running — no game in progress (cycle $cyclePos)")(id =>
          s"[${stream.name}] Running — current game: $id (cycle $cyclePos)"
        )
      else s"[${stream.name}] Stopped (cycle $cyclePos)"
    }

    def gameFinished(finishedGameId: Game.ID): Unit = {
      currentGame.set(none)
      watchdog.getAndSet(none).foreach(_.cancel())
      scheduler.scheduleOnce(2.seconds)(startNextGame(finishedGameId.some).discard)
    }

    def startNextGame(finishedGameId: Option[Game.ID] = none): Funit = {
      if (!running.get()) return funit
      val online = onlineApiUsers.get
      findNextAvailableGame(cycleIdx.get(), 0, online) match {
        case Some((idx, spec)) =>
          cycleIdx.set((idx + 1) % stream.games.length)
          createGame(spec, finishedGameId)
        case None =>
          val missing = botIds.diff(online)
          logger.info(s"[${stream.name}] no bots available — missing ${missing.mkString(", ")}, retrying in 30s")
          scheduler.scheduleOnce(30.seconds)(startNextGame(finishedGameId).discard)
          funit
      }
    }

    private def findNextAvailableGame(startIdx: Int, attempts: Int, online: Set[String]): Option[(Int, BotVsBotGame)] =
      if (attempts >= stream.games.length) none
      else {
        val idx  = startIdx % stream.games.length
        val spec = stream.games(idx)
        if (online.contains(spec.p1.id) && online.contains(spec.p2.id)) Some((idx, spec))
        else findNextAvailableGame(idx + 1, attempts + 1, online)
      }

    // Backgammon draws for the starting player, and every other creation path settles that draw
    // into an initial FEN before making the challenge. Left to none, ChallengeJoiner builds the
    // game from a randomly sided Situation while leaving startedAtTurn at 0: startPlayerIndex then
    // disagrees with the board, and both players' clock histories are attributed to the wrong side
    // and truncated. Always single point here, the stream never sets backgammonPoints.
    private def openingFen(variant: Variant): Option[FEN] =
      (variant.gameFamily == GameFamily.Backgammon())
        .option(FEN(variant.gameLogic, variant.toBackgammon.fenFromSetupConfig(false).value))

    private def createGame(spec: BotVsBotGame, finishedGameId: Option[Game.ID]): Funit = {
      import lila.challenge.Challenge.*
      val timeControl = TimeControl.Clock(spec.clock)
      userRepo.namePair(spec.p1.id, spec.p2.id) flatMap {
        _.fold {
          logger.warn(s"[${stream.name}] users not found: ${spec.p1.name} or ${spec.p2.name}, trying next matchup")
          scheduler.scheduleOnce(5.seconds)(startNextGame(finishedGameId).discard)
          funit
        } { case (p1User, p2User) =>
          val challenge = Challenge.make(
            variant = spec.variant,
            fenVariant = none,
            initialFen = openingFen(spec.variant),
            timeControl = timeControl,
            mode = strategygames.Mode.Casual,
            playerIndex = "p1",
            challenger = toRegistered(spec.variant, timeControl)(p1User),
            destUser = p2User.some,
            rematchOf = none,
            isBotvsBotStream = true
          )
          challengeApi.create(challenge) flatMap {
            case false =>
              logger.warn(
                s"[${stream.name}] failed to create challenge: ${spec.p1.name} vs ${spec.p2.name} (${spec.variant.name}), trying next matchup"
              )
              scheduler.scheduleOnce(5.seconds)(startNextGame(finishedGameId).discard)
              funit
            case true =>
              challengeApi.botVsBotAccept(p2User, challenge) flatMap {
                case Some(game) =>
                  currentGame.set(game.id.some)
                  finishedGameId.foreach(oldId => tellRound(oldId, NextGameLinked(game.id)))
                  val watchdogTimeout = (4 * spec.clock.estimateTotalSeconds).seconds
                  val wd = scheduler.scheduleOnce(watchdogTimeout) {
                    logger.warn(
                      s"[${stream.name}] game ${game.id} watchdog timeout after $watchdogTimeout, moving to next matchup"
                    )
                    currentGame.set(none)
                    startNextGame().discard
                  }
                  watchdog.getAndSet(wd.some).foreach(_.cancel())
                  funit
                case None =>
                  logger.warn(
                    s"[${stream.name}] failed to accept challenge: ${spec.p1.name} vs ${spec.p2.name} (${spec.variant.name}), trying next matchup"
                  )
                  scheduler.scheduleOnce(5.seconds)(startNextGame(finishedGameId).discard)
                  funit
              }
          }
        }
      }
    }
  }

  private def tellRound(gameId: Game.ID, msg: Any): Unit =
    Bus.publish(Tell(gameId, msg), "roundSocket")

  private val runners: List[StreamRunner] = BotVsBotConfig.streams.map(new StreamRunner(_))

  Bus.subscribeFun("finishGame") {
    case actorApi.FinishGame(game, _, _) =>
      runners.find(_.ownsGame(game.id)).foreach { runner =>
        if (runner.isRunning) runner.gameFinished(game.id)
      }
  }

  Bus.subscribeFun("botVsBot") {
    case lila.hub.actorApi.bot.BotVsBotStart => start().discard
    case lila.hub.actorApi.bot.BotVsBotStop  => stop()
    case lila.hub.actorApi.bot.BotVsBotRestart =>
      stop()
      start().discard
    case lila.hub.actorApi.bot.BotVsBotStatus(promise) =>
      promise.success(runners.map(_.status()).mkString("\n"))
    case lila.hub.actorApi.bot.BotVsBotStartStream(name, promise) =>
      runners.find(_.stream.name == name) match {
        case Some(runner) => runner.start().discard; promise.success(s"Stream '$name' start requested")
        case None         => promise.success(s"Stream not found: '$name'. Available: ${runners.map(_.stream.name).mkString(", ")}")
      }
    case lila.hub.actorApi.bot.BotVsBotStopStream(name, promise) =>
      runners.find(_.stream.name == name) match {
        case Some(runner) => runner.stop(); promise.success(s"Stream '$name' stopped")
        case None         => promise.success(s"Stream not found: '$name'. Available: ${runners.map(_.stream.name).mkString(", ")}")
      }
    case lila.hub.actorApi.bot.BotVsBotRestartStream(name, promise) =>
      runners.find(_.stream.name == name) match {
        case Some(runner) => runner.stop(); runner.start().discard; promise.success(s"Stream '$name' restart requested")
        case None         => promise.success(s"Stream not found: '$name'. Available: ${runners.map(_.stream.name).mkString(", ")}")
      }
  }

  def start(): Funit = {
    runners.foreach(_.start().discard)
    funit
  }

  def stop(): Unit = runners.foreach(_.stop())

  private[bot] def init(): Unit =
    if (enabled.get()) {
      logger.info(s"Bot vs bot auto-start enabled (${runners.length} streams), starting on init")
      start().discard
    } else logger.info("Bot vs bot auto-start disabled in config (use CLI to start manually)")
}
