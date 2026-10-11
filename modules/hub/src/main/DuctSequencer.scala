package lila.hub

import com.github.blemale.scaffeine.LoadingCache
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Promise
import scala.util.{ Failure, Success }
import scala.util.control.NonFatal

import lila.base.LilaTimeout
import lila.common.DuctHealth

final class DuctSequencer(maxSize: Int, timeout: FiniteDuration, name: String, logging: Boolean = true)(
    implicit
    scheduler: akka.actor.Scheduler,
    ec: Executor
) {

  import DuctSequencer.*

  def apply[A](fu: => Fu[A]): Fu[A] = run(() => fu)

  private val lastLateWarnNanos = new AtomicLong(0)

  def run[A](task: Task[A]): Fu[A] =
    duct.ask[A](TaskWithPromise(task, _, nextId.incrementAndGet(), System.nanoTime(), duct.queueSize))

  private val duct: BoundedDuct = new BoundedDuct(maxSize, name, logging)({
    case TaskWithPromise(task, promise, id, enqueuedAtNanos, depthAtEnqueue) =>
      val startedAtNanos = System.nanoTime()
      val waitMillis     = (startedAtNanos - enqueuedAtNanos) / 1000000

      DuctHealth.started(id, name, startedAtNanos)

      val real =
        try task()
        catch {
          case NonFatal(e) =>
            DuctHealth.finished(id)
            throw e
        }

      real.onComplete { result =>
        DuctHealth.finished(id)
        val runMillis = (System.nanoTime() - startedAtNanos) / 1000000
        if (logging && runMillis > timeout.toMillis) {
          val nowNanos  = System.nanoTime()
          val lastNanos = lastLateWarnNanos.get()
          if (
            nowNanos - lastNanos >= lateWarnIntervalNanos &&
            lastLateWarnNanos.compareAndSet(lastNanos, nowNanos)
          ) {
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
      }(using scala.concurrent.ExecutionContext.parasitic)

      promise.completeWith {
        real
          .withTimeout(timeout, s"$name DuctSequencer")
          .transform(
            identity,
            {
              case LilaTimeout(msg) =>
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

  DuctHealth.register(name, () => duct.queueSize)
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

  private val nextId = new AtomicLong(0)

  private val lateWarnIntervalNanos = 1000000000L

  private type Task[A] = () => Fu[A]
  private case class TaskWithPromise[A](
      task: Task[A],
      promise: Promise[A],
      id: Long,
      enqueuedAtNanos: Long,
      depthAtEnqueue: Int
  )
}
