package lila.round

import akka.stream.OverflowStrategy
import akka.stream.scaladsl._
import strategygames.{ Player => PlayerIndex }
import strategygames.format.Forsyth
import play.api.libs.json._
import scala.concurrent.ExecutionContext

import lila.common.Bus
import lila.game.actorApi.MoveGameEvent
import lila.game.{ Game, GameRepo }
import strategygames.{ Centis, Replay }
import lila.game.actorApi.FinishGame

final class ApiActionStream(gameRepo: GameRepo, gameJsonView: lila.game.JsonView)(implicit
    ec: ExecutionContext
) {

  private val delayMovesBy         = 3
  private val delayKeepsFirstMoves = 5

  def apply(game: Game): Source[JsObject, _] =
    Source futureSource {
      gameRepo.initialFen(game) map { initialFen =>
        val buffer = scala.collection.mutable.Queue.empty[JsObject]
        var moves  = 0
        Source(List(gameJsonView(game, initialFen))) concat
          Source
            .queue[JsObject](16, akka.stream.OverflowStrategy.dropHead)
            .statefulMapConcat { () => js =>
              moves += 1
              if (game.finished || moves <= delayKeepsFirstMoves) List(js)
              else {
                buffer.enqueue(js)
                (buffer.size > delayMovesBy) ?? List(buffer.dequeue())
              }
            }
            .mapMaterializedValue { queue =>
              val clocks = ~(for {
                clk   <- game.clock
                times <- game.bothClockStates
              } yield Vector(
                Centis(clk.graceSeconds * 100),
                clk.config.initTime,
                clk.config.initTime
              ) ++ times)
              val clockOffset = game.startPlayerIndex.fold(0, 1)
              Replay.situations(game.variant.gameLogic, game.actionStrs, initialFen, game.variant) foreach {
                _.zipWithIndex foreach { case (s, index) =>
                  val clkValues = for {
                    p1           <- clocks.lift(index + 2 - clockOffset)
                    p2           <- clocks.lift(index + 1 - clockOffset)
                    graceSeconds <- clocks.lift(index)
                  } yield (p1, p2, Centis(0), Centis(0), graceSeconds, graceSeconds)
                  queue.offer(
                    toJson(
                      Forsyth.exportBoard(s.board.variant.gameLogic, s.board),
                      s.player,
                      s.board.history.lastAction.map(_.uci),
                      clkValues
                    )
                  )
                }
              }
              if (game.finished) {
                queue offer gameJsonView(game, initialFen)
                queue.complete()
              } else {
                val chans = List(MoveGameEvent makeChan game.id, "finishGame")
                val sub = Bus.subscribeFun(chans: _*) {
                  case MoveGameEvent(g, fen, move) =>
                    queue.offer(toJson(g, fen, move.some)).unit
                  case FinishGame(g, _, _) if g.id == game.id =>
                    queue offer gameJsonView(g, initialFen)
                    (1 to buffer.size) foreach { _ => queue.offer(Json.obj()) } // push buffer content out
                    queue.complete()
                }
                queue.watchCompletion() dforeach { _ =>
                  Bus.unsubscribe(sub, chans)
                }
              }
            }
      }
    }

  private def toJson(game: Game, fen: String, lastMoveUci: Option[String]): JsObject =
    toJson(
      fen,
      game.turnPlayerIndex,
      lastMoveUci,
      game.clock.map { clk =>
        (
          clk.remainingTime(strategygames.P1),
          clk.remainingTime(strategygames.P2),
          clk.pending(strategygames.P1),
          clk.pending(strategygames.P2),
          Centis(clk.graceSeconds * 100),
          Centis(clk.graceSeconds * 100)
        )
      }
    )

  private def toJson(
      boardFen: String,
      turnPlayerIndex: PlayerIndex,
      lastMoveUci: Option[String],
      clock: Option[(Centis, Centis, Centis, Centis, Centis, Centis)]
  ): JsObject =
    clock.foldLeft(
      Json
        .obj("fen" -> s"$boardFen ${turnPlayerIndex.letter}")
        .add("lm" -> lastMoveUci)
    ) { case (js, clk) =>
      // TODO: this has the potential to introduce bugs, but is consistent with our rename
      js ++ Json.obj(
        "p1"        -> clk._1.roundSeconds,
        "p2"        -> clk._2.roundSeconds,
        "p1Pending" -> clk._3.roundSeconds,
        "p2Pending" -> clk._4.roundSeconds,
        "p1Delay"   -> clk._5.roundSeconds,
        "p2Delay"   -> clk._6.roundSeconds
      )
    }
}
