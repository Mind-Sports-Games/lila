package lila.tournament

import org.apache.pekko.actor._
import org.apache.pekko.stream.scaladsl._
import scala.concurrent.duration._
import scala.util.chaining._
import lila.common.ThreadLocalRandom

final private class StartedOrganizer(
    api: TournamentApi,
    tournamentRepo: TournamentRepo,
    playerRepo: PlayerRepo,
    socket: TournamentSocket
)(implicit
    ec: scala.concurrent.ExecutionContext,
    scheduler: org.apache.pekko.actor.Scheduler,
    mat: akka.stream.Materializer
) extends Actor {

  override def preStart(): Unit = {
    context.setReceiveTimeout(120.seconds)
    scheduleNext()
  }

  case object Tick

  def scheduleNext(): Unit =
    { val _ = scheduler.scheduleOnce(2.seconds, self, Tick) }

  def receive = {

    case ReceiveTimeout =>
      val msg = "tournament.StartedOrganizer timed out!"
      pairingLogger.error(msg)
      throw new RuntimeException(msg)

    case Tick =>
      tournamentRepo.startedCursor
        .documentSource()
        .mapAsyncUnordered(4) { tour =>
          processTour(tour) recover { case e: Exception =>
            logger.error(s"StartedOrganizer $tour", e)
            0
          }
        }
        .toMat(Sink.fold(0 -> 0) { case ((tours, users), tourUsers) =>
          (tours + 1, users + tourUsers)
        })(Keep.right)
        .run()
        .addEffect { case (tours, users) =>
          lila.mon.tournament.started.update(tours)
          val _ = lila.mon.tournament.waitingPlayers.record(users)
        }
        .monSuccess(_.tournament.startedOrganizer.tick)
        .addEffectAnyway(scheduleNext())
        .unit
  }

  private def processMedleyRoundChange(tour: Tournament) =
    if (tour.needsNewMedleyRound) api.newMedleyRound(tour)
    else tour

  private def processPairings(tour: Tournament) =
    if (!tour.isScheduled && tour.nbPlayers < 30 && ThreadLocalRandom.nextInt(10) == 0) {
      playerRepo nbActiveUserIds tour.id flatMap { nb =>
        (nb >= 2) so startPairing(tour)
      }
    } else startPairing(tour)

  private def processTour(tour: Tournament): Fu[Int] =
    if (tour.isOver)
      api.finish(tour).inject(0)
    else
      tour
        .pipe(processMedleyRoundChange)
        .pipe(processPairings)

  // returns number of users actively awaiting a pairing
  private def startPairing(tour: Tournament): Fu[Int] =
    (!tour.pairingsClosed && tour.nbPlayers > 1) so
      socket
        .getWaitingUsers(tour)
        .monSuccess(_.tournament.startedOrganizer.waitingUsers)
        .flatMap { waiting =>
          api.makePairings(tour, waiting) inject waiting.size
        }
}
