package lila.hub

import com.github.blemale.scaffeine.LoadingCache
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Promise
import scala.util.{ Failure, Success }

import lila.base.LilaTimeout
import lila.common.DuctRegistry

final class DuctSequencer(maxSize: Int, timeout: FiniteDuration, name: String, logging: Boolean = true)(
    implicit
    scheduler: akka.actor.Scheduler,
    ec: Executor
) {

  import DuctSequencer.*

  private val nextId = new AtomicLong(0)

  def apply[A](fu: => Fu[A]): Fu[A] = run(() => fu)

  def run[A](task: Task[A]): Fu[A] =
    duct.ask[A](TaskWithPromise(task, _, nextId.incrementAndGet(), System.nanoTime(), duct.queueSize))

  // explicit type: the process closure below reads `duct.queueSize`, so inference would be cyclic
  private val duct: BoundedDuct = new BoundedDuct(maxSize, name, logging)({
    case TaskWithPromise(task, promise, id, enqueuedAtNanos, depthAtEnqueue) =>
      val startedAtNanos = System.nanoTime()
      val waitMillis     = (startedAtNanos - enqueuedAtNanos) / 1000000
      DuctRegistry.started(name, id, startedAtNanos, "task")
      lila.mon.duct.queueWait(name).record(waitMillis)

      val real = task()

      // The timeout below abandons `real` rather than cancelling it, so without this observer a timed out
      // task leaves no trace of what became of it. Distinguishing "finished just over the limit" from
      // "finished a minute later" from "never finished at all" is the whole point of this instrumentation:
      // only the last of those three indicates a lost callback or an unfulfilled promise.
      real.onComplete { result =>
        val runMillis = (System.nanoTime() - startedAtNanos) / 1000000
        DuctRegistry.finished(name, id)
        lila.mon.duct.runTime(name).record(runMillis)
        if (runMillis > timeout.toMillis) {
          lila.mon.duct.lateCompletion(name).record(runMillis)
          val outcome = result match {
            case Success(_) => "success"
            case Failure(e) => s"failure:${e.getClass.getSimpleName}"
          }
          lila.log("duct").warn(
            s"[$name#$id] completed AFTER its ${timeout.toMillis}ms timeout: " +
              s"wait=${waitMillis}ms run=${runMillis}ms depthAtEnqueue=$depthAtEnqueue outcome=$outcome"
          )
        }
      }

      promise.completeWith {
        real
          .withTimeout(timeout, s"$name DuctSequencer")
          .transform(
            identity,
            {
              case LilaTimeout(msg) =>
                lila.mon.duct.timeout(name).increment()
                val fullMsg =
                  s"$name DuctSequencer $msg [id=$id wait=${waitMillis}ms " +
                    s"depthAtEnqueue=$depthAtEnqueue depthNow=${duct.queueSize}]"
                if (logging) lila.log("duct").warn(fullMsg)
                LilaTimeout(fullMsg)
              case e => e
            }
          )
      }.future
  })

  DuctRegistry.register(name, () => duct.queueSize)
}

// Distributes tasks to many sequencers
final class DuctSequencers(
    maxSize: Int,
    expiration: FiniteDuration,
    timeout: FiniteDuration,
    name: String,
    logging: Boolean = true
)(implicit
    scheduler: akka.actor.Scheduler,
    ec: Executor,
    mode: play.api.Mode
) {

  def apply[A](key: String)(task: => Fu[A]): Fu[A] =
    sequencers.get(key).run(() => task)

  private val sequencers: LoadingCache[String, DuctSequencer] =
    lila.common.LilaCache
      .scaffeine(mode)
      .expireAfterAccess(expiration)
      .build(key => new DuctSequencer(maxSize, timeout, s"$name:$key", logging))
}

object DuctSequencer {

  private type Task[A] = () => Fu[A]
  private case class TaskWithPromise[A](
      task: Task[A],
      promise: Promise[A],
      id: Long,
      enqueuedAtNanos: Long,
      depthAtEnqueue: Int
  )
}
