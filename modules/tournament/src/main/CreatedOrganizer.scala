package lila.tournament

import akka.actor.*
import akka.stream.scaladsl.*
import scala.concurrent.duration.*

import lila.common.extensions.*

final private class CreatedOrganizer(
    api: TournamentApi,
    tournamentRepo: TournamentRepo,
    playerRepo: PlayerRepo
)(implicit
    ec: scala.concurrent.ExecutionContext,
    scheduler: akka.actor.Scheduler,
    mat: akka.stream.Materializer
) extends Actor {

  override def preStart(): Unit = {
    context.setReceiveTimeout(15.seconds)
    val _ = scheduler.scheduleOnce(10 seconds, self, Tick)
  }

  case object Tick

  def scheduleNext(): Unit = {
    { val _ = scheduler.scheduleOnce(2 seconds, self, Tick) }
  }

  @volatile private var tickId        = 0L
  @volatile private var tickStartedAt = 0L
  @volatile private var tickPending   = false

  def receive = {

    case ReceiveTimeout =>
      val stuckForMillis = if (tickPending) (System.nanoTime() - tickStartedAt) / 1000000 else -1L
      val msg            = "tournament.CreatedOrganizer timed out!"
      pairingLogger.error(s"$msg tick=$tickId pending=$tickPending stuckFor=${stuckForMillis}ms")
      lila.mon.tournament.createdOrganizer.timeout.increment()
      throw new RuntimeException(msg)

    case Tick =>
      tickId += 1
      tickStartedAt = System.nanoTime()
      tickPending = true
      tournamentRepo.shouldStartCursor
        .documentSource()
        .mapAsync(1) { tour =>
          playerRepo.count(tour.id) flatMap {
            case 0 => api.destroy(tour)
            case _ => api.start(tour)
          }
        }
        .log(getClass.getName)
        .toMat(Sink.ignore)(Keep.right)
        .run()
        .monSuccess(_.tournament.createdOrganizer.tick)
        .addEffectAnyway {
          tickPending = false
          scheduleNext()
        }
        .discard
  }
}
