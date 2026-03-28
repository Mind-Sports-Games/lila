package lila.common

import org.apache.pekko.stream.scaladsl._
import org.apache.pekko.stream.{ Materializer, QueueOfferResult }
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ Future, Promise }
import java.util.concurrent.TimeoutException

/* Sequences async tasks, so that:
 * queue.run(() => task1); queue.run(() => task2)
 * runs task2 only after task1 completes, much like:
 * task1 flatMap { _ => task2 }
 *
 * If the buffer is full, the new task is dropped,
 * and `run` returns a failed future.
 *
 * This is known to work poorly with parallelism=1
 * because the queue is used by multiple threads
 */
final class WorkQueue(buffer: Int, timeout: FiniteDuration, name: String, parallelism: Int)(implicit
    ec: Executor,
    scheduler: org.apache.pekko.actor.Scheduler,
    mat: Materializer
) {

  import WorkQueue.*

  def apply[A](future: => Fu[A]): Fu[A] = run(() => future)

  def run[A](task: () => Fu[A]): Fu[A] = {
    val promise = Promise[A]()
    queue.offer(TaskWithPromise(task, promise)) match {
      case QueueOfferResult.Enqueued =>
        promise.future
      case result =>
        lila.mon.workQueue.offerFail(name, result.toString).increment()
        Future failed new EnqueueException(s"Can't enqueue in $name: $result")
    }
  }

  private val queue = Source
    // TODO: Replace WorkQueue with scalalib.actor.AsyncActorSequencer/AsyncActorSequencers
    .queue[TaskWithPromise[?]](buffer)
    .mapAsyncUnordered(parallelism) { twp =>
      twp.run(timeout, name)(using ec, scheduler)
    }
    .toMat(Sink.ignore)(Keep.left)
    .run()
}

object WorkQueue {

  final class EnqueueException(msg: String) extends Exception(msg)

  private case class TaskWithPromise[A](task: () => Fu[A], promise: Promise[A]) {
    def run(timeout: FiniteDuration, name: String)(implicit ec: Executor, scheduler: org.apache.pekko.actor.Scheduler): Future[A] =
      task()
        .withTimeout(timeout, s"WorkQueue:$name")
        .tap(promise.completeWith)
        .transform(
          identity,
          e => {
            e match {
              case e: TimeoutException =>
                lila.mon.workQueue.timeout(name).increment()
                lila.log(s"WorkQueue:$name").warn(s"task timed out after $timeout", e)
              case e: Exception =>
                lila.log(s"WorkQueue:$name").info("task failed", e)
            }
            promise.failure(e)
            e
          }
        )
  }
}
