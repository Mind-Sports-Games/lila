package lila.fishnet

import scala.concurrent.duration._

import strategygames.{ Clock, P1, P2 }
import strategygames.format.Uci

import lila.common.Future
import lila.game.{ Game, GameRepo, UciMemo }
import ornicar.scalalib.Random.approximately

final class FishnetPlayer(
    redis: FishnetRedis,
    gameRepo: GameRepo,
    uciMemo: UciMemo,
    val maxTurns: Int
)(implicit
    ec: scala.concurrent.ExecutionContext,
    scheduler: akka.actor.Scheduler
) {

  def apply(game: Game): Funit =
    game.aiLevel ?? { level =>
      Future.delay(delayFor(game) | 0.millis) {
        makeWork(game, level) addEffect redis.request void
      }
    } recover { case e: Exception =>
      logger.info(e.getMessage)
    }

  private val delayFactor  = 0.011f
  private val defaultClock = Clock(300, 0)

  private def delayFor(g: Game): Option[FiniteDuration] =
    if (!g.bothPlayersHaveMoved) 2.seconds.some
    else
      for {
        pov <- g.aiPov
        clock     = g.clock | defaultClock
        totalTime = clock.estimateTotalTime.centis
        if totalTime > 20 * 100
        delay = (clock.remainingTime(pov.playerIndex).centis atMost totalTime) * delayFactor
        accel = 1 - ((g.turnCount - 20) atLeast 0 atMost 100) / 150f
        sleep = (delay * accel) atMost 500
        if sleep > 25
        millis     = sleep * 10
        randomized = approximately(0.5f)(millis)
        divided    = randomized / (if (g.turnCount > 9) 1 else 2)
      } yield divided.millis

  private def makeWork(game: Game, level: Int): Fu[Work.Move] =
    if (game.situation playable true)
      if (game.turnCount <= maxTurns) gameRepo.initialFen(game) zip uciMemo.get(game) map {
        case (initialFen, moves) =>
          Work.Move(
            _id = Work.makeId,
            game = Work.Game(
              id = game.id,
              initialFen = initialFen,
              studyId = none,
              variant = game.variant,
              //ok to flatten as fishnet doesnt handle multimove
              moves = moves.flatten
                .flatMap(Uci(game.variant.gameLogic, game.variant.gameFamily, _))
                .map(_.uci)
                .mkString(" ")
            ),
            level =
              if (level < 3 && game.clock.exists(_.config.limit.toSeconds < 60)) 3
              else level,
            clock = game.clock.map { clk =>
              Work.Clock(
                wtime = clk.remainingTime(P1).centis,
                btime = clk.remainingTime(P2).centis,
                // TODO: this field should be renamed to better indicate that it also represents bronstein delay and simple delay
                inc = clk.graceSeconds
              )
            }
          )
      }
      else fufail(s"[fishnet] Too many turns (${game.turnCount}), won't play ${game.id}")
    else fufail(s"[fishnet] invalid position on ${game.id}")
}
