package lila.common

import org.apache.pekko.actor._
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.duration._

final class Debouncer[Id](scheduler: Scheduler, duration: FiniteDuration, initialCapacity: Int = 64)(
    f: Id => Unit
)(using ec: Executor):

  private enum Queued:
    case Another, Empty

  private val debounces = ConcurrentHashMap[Id, Queued](initialCapacity)

  def push(id: Id): Unit = debounces
    .compute(
      id,
      (_, prev) =>
        Option(prev) match
          case None =>
            f(id)
            scheduler.scheduleOnce(duration) { runScheduled(id) }
            Queued.Empty
          case _ => Queued.Another
    )

  private def runScheduled(id: Id): Unit = debounces
    .computeIfPresent(
      id,
      (_, queued) =>
        if queued == Queued.Another then
          f(id)
          scheduler.scheduleOnce(duration) { runScheduled(id) }
          Queued.Empty
        else nullToRemove
    )

  @scala.annotation.nowarn
  private var nullToRemove: Queued = scala.compiletime.uninitialized
