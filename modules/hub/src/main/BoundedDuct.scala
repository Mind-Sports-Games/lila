package lila.hub

import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator
import scala.collection.immutable.Queue
import scala.concurrent.Promise

/*
 * Sequential like an actor, but for async functions,
 * and using an atomic backend instead of akka actor.
 */
final class BoundedDuct(maxSize: Int, name: String, logging: Boolean = true)(process: Duct.ReceiveAsync)(
    implicit ec: scala.concurrent.ExecutionContext
) {

  import BoundedDuct.*

  def !(msg: Any): Boolean =
    stateRef.getAndUpdate { state =>
      Some {
        state.fold(emptyQueue) { q =>
          if (q.size >= maxSize) q
          else q.enqueue(msg)
        }
      }
    } match {
      case None => // previous state was idle, we can run immediately
        run(msg)
        true
      case Some(q) =>
        val success = q.size < maxSize
        if (!success) {
          lila.mon.duct.overflow(name).increment()
          if (logging) lila.log("duct").warn(s"[$name] queue is full ($maxSize)")
        }
        success
    }

  def ask[A](makeMsg: Promise[A] => Any): Fu[A] = {
    val promise = Promise[A]()
    val success = this ! makeMsg(promise)
    if (!success) promise failure new EnqueueException(s"The $name duct queue is full ($maxSize)")
    promise.future
  }

  def queueSize = stateRef.get().fold(0)(_.size + 1)

  /*
   * Idle: None
   * Busy: Some(Queue.empty)
   * Busy with backlog: Some(Queue.nonEmpty)
   */
  private val stateRef: AtomicReference[State] = new AtomicReference(None)

  /* `process` is expected to return a future; if it instead throws synchronously, the state ref is left
   * marked busy and this duct never runs another task for the lifetime of the JVM. Callers then see their
   * futures hang rather than fail, which is close to invisible in the logs. Draining the queue anyway keeps
   * the duct alive and makes the event loud. The exception is still rethrown so that callers observe exactly
   * what they observe today.
   */
  private def run(msg: Any): Unit =
    try process.applyOrElse(msg, fallback).onComplete(postRun)
    catch {
      case e: Throwable =>
        lila.mon.duct.wedged(name).increment()
        lila.log("duct").error(
          s"[$name] process threw synchronously on ${msg.getClass.getSimpleName}; " +
            "duct would have wedged permanently, draining queue instead",
          e
        )
        postRun(())
        throw e
    }

  private val postRun = (_: Any) => stateRef.getAndUpdate(postRunUpdate).flatMap(_.headOption).foreach(run)

  private lazy val fallback = (msg: Any) => {
    lila.log("duct").warn(s"[$name] unhandled msg: $msg")
    funit
  }
}

object BoundedDuct {

  final class EnqueueException(msg: String) extends Exception(msg)

  private case class SizedQueue(queue: Queue[Any], size: Int) {
    def enqueue(a: Any) = SizedQueue(queue.enqueue(a), size + 1)
    def isEmpty         = size == 0
    def tailOption      = (!isEmpty).option(SizedQueue(queue.tail, size - 1))
    def headOption      = queue.headOption
  }
  private val emptyQueue = SizedQueue(Queue.empty, 0)

  private type State = Option[SizedQueue]

  private val postRunUpdate = new UnaryOperator[State] {
    override def apply(state: State): State = state.flatMap(_.tailOption)
  }
}
