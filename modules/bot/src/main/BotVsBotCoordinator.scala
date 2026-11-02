package lila.bot

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger, AtomicReference }
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext
import akka.actor.{ Cancellable, Scheduler }
import play.api.Configuration
import strategygames.GameFamily
import strategygames.format.FEN
import strategygames.variant.Variant

import lila.challenge.{ Challenge, ChallengeApi }
import lila.common.Bus
import lila.game.{ Game, actorApi }
import lila.hub.actorApi.bot.*
import lila.hub.actorApi.map.Tell
import lila.hub.actorApi.round.NextGameLinked
import lila.user.UserRepo

final class BotVsBotCoordinator(
    repo: BotStreamRepo,
    userRepo: UserRepo,
    challengeApi: ChallengeApi,
    onlineApiUsers: OnlineApiUsers,
    appConfig: Configuration
)(implicit ec: ExecutionContext, scheduler: Scheduler) {

  val autoStart = appConfig.getOptional[Boolean]("autoBotvsBot.enabled") | false

  private class StreamRunner(val stream: BotStream) {
    private val running     = new AtomicBoolean(false)
    private val currentGame = new AtomicReference[Option[Game.ID]](none)
    private val watchdog    = new AtomicReference[Option[Cancellable]](none)
    private val cycleIdx    = new AtomicInteger(scala.util.Random.nextInt(stream.games.length))

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

    def liveStatus = BotStreamStatus(
      currentGame = currentGame.get(),
      cyclePosition = cycleIdx.get() + 1,
      cycleLength = stream.games.length,
      missingBots = stream.botIds.diff(onlineApiUsers.get)
    )

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
          val missing = stream.botIds.diff(online)
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

    // TODO: multipoint is hardcoded to false because the stream currently has no backgammonPoints.
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
            botVsBotSource = stream.source.some
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

  private val runners = TrieMap.empty[BotStream.ID, StreamRunner]

  private val syncSequencer =
    new lila.hub.DuctSequencer(maxSize = 64, timeout = 10.seconds, name = "botVsBotSync")

  private def sync(canStart: BotStream.ID => Boolean, restart: Boolean = false): Funit =
    syncSequencer {
      repo.all map { streams =>
        val wanted     = streams.filter(_.shouldRun).map(s => s.id -> s).toMap
        val wasRunning = runners.keySet.toSet
        runners.foreach { case (id, runner) =>
          if (restart || !wanted.get(id).exists(runner.stream.sameDefinition)) {
            runners.remove(id)
            runner.stop()
          }
        }
        wanted.values.foreach { stream =>
          if (!runners.contains(stream.id) && (wasRunning(stream.id) || canStart(stream.id))) {
            val runner = new StreamRunner(stream)
            runners.put(stream.id, runner)
            runner.start().discard
          }
        }
      }
    }

  def refresh: Funit = sync(_ => autoStart)

  def liveStatus: Map[BotStream.ID, BotStreamStatus] =
    runners.view.mapValues(_.liveStatus).toMap

  def state(stream: BotStream): String =
    if (runners.contains(stream.id)) "Running"
    else if (stream.isExpired) "Expired"
    else if (stream.active) "Active, not running"
    else "Stopped"

  def start(stream: BotStream): Fu[String] =
    if (stream.isExpired) fuccess(s"Bot stream '${stream.name}' has expired: move its end date first")
    else
      repo.setActive(stream.id, true) >> sync(id => autoStart || id == stream.id) inject
        s"Bot stream '${stream.name}' started"

  def stop(stream: BotStream): Fu[String] =
    repo.setActive(stream.id, false) >> refresh inject s"Bot stream '${stream.name}' stopped"

  def restart(stream: BotStream): Fu[String] =
    if (stream.isExpired) fuccess(s"Bot stream '${stream.name}' has expired: move its end date first")
    else
      syncSequencer {
        runners.remove(stream.id).foreach(_.stop())
        funit
      } >> start(stream) inject s"Bot stream '${stream.name}' restarted"

  private def startAll: Funit = repo.activateAllUnexpired >> sync(_ => true)

  private def stopAll: Funit = repo.deactivateAll >> refresh

  private def restartAll: Funit = sync(_ => autoStart, restart = true)

  private def statusText: Fu[String] =
    repo.all map { streams =>
      if (streams.isEmpty) "No bot streams"
      else
        streams
          .map { s =>
            runners.get(s.id).fold(s"[${s.name}] ${state(s)}")(_.status())
          }
          .mkString("\n")
    }

  private def withStream(idOrName: String)(f: BotStream => Fu[String]): Fu[String] =
    repo.byIdOrName(idOrName) flatMap {
      case Some(stream) => f(stream)
      case None         =>
        repo.all map { streams =>
          s"Bot stream not found: '$idOrName'. Available: ${streams.map(_.name).mkString(", ")}"
        }
    }

  Bus.subscribeFun("finishGame") {
    case actorApi.FinishGame(game, _, _) =>
      runners.values.find(_.ownsGame(game.id)).foreach { runner =>
        if (runner.isRunning) runner.gameFinished(game.id)
      }
  }

  Bus.subscribeFun("botVsBot") {
    case BotVsBotStatus(promise)  => promise.completeWith(statusText)
    case BotVsBotStart(promise)   => promise.completeWith(startAll inject "Started every bot stream that has not expired")
    case BotVsBotStop(promise)    =>
      promise.completeWith(stopAll inject "Stopped every bot stream and marked them inactive")
    case BotVsBotRestart(promise) => promise.completeWith(restartAll inject "Restarted every running bot stream")
    case BotVsBotStartStream(name, promise)   => promise.completeWith(withStream(name)(start))
    case BotVsBotStopStream(name, promise)    => promise.completeWith(withStream(name)(stop))
    case BotVsBotRestartStream(name, promise) => promise.completeWith(withStream(name)(restart))
  }

  scheduler.scheduleWithFixedDelay(1.minute, 1.minute) { () =>
    refresh.discard
  }

  private[bot] def init(): Unit = {
    if (autoStart) logger.info("Bot vs bot auto-start enabled, starting active streams")
    else logger.info("Bot vs bot auto-start disabled in config (start streams from the mod page or CLI)")
    refresh.discard
  }
}
