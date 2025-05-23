package lila.lobby

import actorApi._
import cats.implicits._
import org.joda.time.DateTime
import scala.concurrent.duration._
import scala.concurrent.Promise

import lila.common.config.Max
import lila.common.{ AtMost, Bus, Every }
import lila.game.Game
import lila.hub.Trouper
import lila.socket.Socket.{ Sri, Sris }
import lila.user.User
import lila.i18n.VariantKeys

final private class LobbyTrouper(
    seekApi: SeekApi,
    biter: Biter,
    gameCache: lila.game.Cached,
    maxPlaying: Max,
    playbanApi: lila.playban.PlaybanApi,
    discordApi: lila.irc.DiscordApi,
    poolApi: lila.pool.PoolApi,
    onStart: lila.round.OnStart
)(implicit ec: scala.concurrent.ExecutionContext, scheduler: akka.actor.Scheduler)
    extends Trouper {

  import LobbyTrouper._

  private val hookRepo = new HookRepo

  private var remoteDisconnectAllAt = DateTime.now

  private var socket: Trouper = Trouper.stub

  val process: Trouper.Receive = {

    // solve circular reference
    case SetSocket(trouper) => socket = trouper

    case msg @ AddHook(hook) =>
      lila.mon.lobby.hook.create.increment()
      hookRepo bySri hook.sri foreach remove
      hook.sid ?? { sid =>
        hookRepo bySid sid foreach remove
      }
      !hook.compatibleWithPools(hook.realVariant) ?? findCompatible(hook) match {
        case Some(h) => biteHook(h.id, hook.sri, hook.user)
        case None =>
          hookRepo.save(msg.hook)
          socket ! msg
          discordApi
            .matchmakingAnnouncement(
              hook.message,
              hook.realVariant.gameFamily.key,
              VariantKeys.variantName(hook.realVariant),
              true
            )
            .effectFold(
              err => logger.warn(s"discord hook failed: $err"),
              _ => ()
            )
      }

    case msg @ AddSeek(seek) =>
      lila.mon.lobby.seek.create.increment()
      findCompatible(seek) foreach {
        case Some(s) => this ! BiteSeek(s.id, seek.user)
        case None    => this ! SaveSeek(msg)
      }

    case SaveSeek(msg) =>
      seekApi.insert(msg.seek)
      socket ! msg
      discordApi
        .matchmakingAnnouncement(
          msg.seek.message,
          msg.seek.realVariant.gameFamily.key,
          VariantKeys.variantName(msg.seek.realVariant),
          false
        )
        .effectFold(
          err => logger.warn(s"discord seek failed: $err"),
          _ => ()
        )

    case CancelHook(sri) =>
      hookRepo bySri sri foreach remove

    case CancelSeek(seekId, user) =>
      seekApi.removeBy(seekId, user.id)
      socket ! RemoveSeek(seekId)

    case BiteHook(hookId, sri, user) =>
      NoPlayban(user) {
        biteHook(hookId, sri, user)
      }

    case BiteSeek(seekId, user) =>
      NoPlayban(user.some) {
        gameCache.nbPlaying(user.id) foreach { nbPlaying =>
          if (maxPlaying > nbPlaying) {
            lila.mon.lobby.seek.join.increment()
            seekApi find seekId foreach {
              _ foreach { seek =>
                biter(seek, user) foreach this.!
              }
            }
          }
        }
      }

    case msg @ JoinHook(_, hook, game, _) =>
      onStart(game.id)
      socket ! msg
      remove(hook)

    case msg @ JoinSeek(_, seek, game, _) =>
      onStart(game.id)
      seekApi.archive(seek, game.id)
      socket ! msg
      socket ! RemoveSeek(seek.id)

    case LeaveAll => remoteDisconnectAllAt = DateTime.now

    case Tick(promise) =>
      hookRepo.truncateIfNeeded()
      socket
        .ask[Sris](GetSrisP)
        .chronometer
        .logIfSlow(100, logger) { r =>
          s"GetSris size=${r.sris.size}"
        }
        .mon(_.lobby.socket.getSris)
        .result
        .logFailure(logger, err => s"broom cannot get sris from socket: $err")
        .foreach { this ! WithPromise(_, promise) }

    case WithPromise(Sris(sris), promise) =>
      poolApi socketIds Sris(sris)
      val fewSecondsAgo = DateTime.now minusSeconds 5
      if (remoteDisconnectAllAt isBefore fewSecondsAgo) this ! RemoveHooks {
        hookRepo
          .notInSris(sris)
          .filter { h =>
            !h.boardApi && (h.createdAt isBefore fewSecondsAgo)
          }
          .toSet ++ hookRepo.cleanupOld
      }
      lila.mon.lobby.socket.member.update(sris.size)
      lila.mon.lobby.hook.size.record(hookRepo.size)
      lila.mon.trouper.queueSize("lobby").update(queueSize)
      promise.success(())

    case RemoveHooks(hooks) => hooks foreach remove

    case Resync => socket ! HookIds(hookRepo.ids)

    case HookSub(member, true) =>
      socket ! AllHooksFor(member, hookRepo.filter { biter.showHookTo(_, member) }.toSeq)

    case lila.pool.HookThieve.GetCandidates(clock, variant, promise) =>
      promise success lila.pool.HookThieve.PoolHooks(hookRepo.poolCandidates(clock, variant))

    case lila.pool.HookThieve.StolenHookIds(ids) =>
      hookRepo byIds ids.toSet foreach remove
  }

  private def NoPlayban(user: Option[LobbyUser])(f: => Unit): Unit = {
    user.?? { u =>
      playbanApi.currentBan(u.id)
    } foreach {
      case None => f
      case _    =>
    }
  }

  private def biteHook(hookId: String, sri: Sri, user: Option[LobbyUser]) =
    hookRepo byId hookId foreach { hook =>
      remove(hook)
      hookRepo bySri sri foreach remove
      biter(hook, sri, user) foreach this.!
    }

  private def findCompatible(hook: Hook): Option[Hook] =
    hookRepo.filter(_ compatibleWith hook).find { existing =>
      biter.canJoin(existing, hook.user) && !(
        (existing.user, hook.user).mapN((_, _)) ?? { case (u1, u2) =>
          recentlyAbortedUserIdPairs.exists(u1.id, u2.id)
        }
      )
    }

  def registerAbortedGame(g: Game) = recentlyAbortedUserIdPairs register g

  private object recentlyAbortedUserIdPairs {
    private val cache                                     = new lila.memo.ExpireSetMemo(1 hour)
    private def makeKey(u1: User.ID, u2: User.ID): String = if (u1 < u2) s"$u1/$u2" else s"$u2/$u1"
    def register(g: Game) =
      for {
        w <- g.p1Player.userId
        b <- g.p2Player.userId
        if g.fromLobby
      } cache.put(makeKey(w, b))
    def exists(u1: User.ID, u2: User.ID) = cache.get(makeKey(u1, u2))
  }

  private def findCompatible(seek: Seek): Fu[Option[Seek]] =
    seekApi forUser seek.user map {
      _ find (_ compatibleWith seek)
    }

  private def remove(hook: Hook) = {
    hookRepo remove hook
    socket ! RemoveHook(hook.id)
    Bus.publish(RemoveHook(hook.id), s"hookRemove:${hook.id}")
  }
}

private object LobbyTrouper {

  case class SetSocket(trouper: Trouper)

  private case class Tick(promise: Promise[Unit])

  private case class WithPromise[A](value: A, promise: Promise[Unit])

  def start(
      broomPeriod: FiniteDuration,
      resyncIdsPeriod: FiniteDuration
  )(
      makeTrouper: () => LobbyTrouper
  )(implicit ec: scala.concurrent.ExecutionContext, scheduler: akka.actor.Scheduler) = {
    val trouper = makeTrouper()
    Bus.subscribe(trouper, "lobbyTrouper")
    scheduler.scheduleWithFixedDelay(15 seconds, resyncIdsPeriod)(() => trouper ! actorApi.Resync)
    lila.common.ResilientScheduler(
      every = Every(broomPeriod),
      atMost = AtMost(10 seconds),
      initialDelay = 7 seconds
    ) { trouper.ask[Unit](Tick) }
    trouper
  }
}
