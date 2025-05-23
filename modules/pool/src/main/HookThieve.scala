package lila.pool

import scala.concurrent.Promise

import lila.common.Bus

final private class HookThieve()(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem,
    scheduler: akka.actor.Scheduler
) {

  import HookThieve._

  def candidates(clock: strategygames.ClockConfig, variant: strategygames.variant.Variant): Fu[PoolHooks] =
    Bus
      .ask[PoolHooks]("lobbyTrouper")(GetCandidates(clock, variant, _))
      .logFailure(logger)
      .nevermind(PoolHooks(Vector.empty))

  def stolen(poolHooks: Vector[PoolHook], monId: String) = {
    lila.mon.lobby.pool.thieve.stolen(monId).record(poolHooks.size)
    if (poolHooks.nonEmpty) Bus.publish(StolenHookIds(poolHooks.map(_.hookId)), "lobbyTrouper")
  }
}

object HookThieve {

  case class GetCandidates(
      clock: strategygames.ClockConfig,
      variant: strategygames.variant.Variant,
      promise: Promise[PoolHooks]
  )
  case class StolenHookIds(ids: Vector[String])

  case class PoolHook(hookId: String, member: PoolMember) {
    def is(m: PoolMember) = member.userId == m.userId
  }

  case class PoolHooks(hooks: Vector[PoolHook])
}
