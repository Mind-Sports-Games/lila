package lila.common

import org.apache.pekko.actor.Scheduler

import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration

object LilaFuture:

  def delay[A](
      duration: FiniteDuration
  )(run: => Fu[A])(using ec: scala.concurrent.ExecutionContext, scheduler: Scheduler): Fu[A] =
    if duration == 0.millis then run
    else org.apache.pekko.pattern.after(duration, scheduler)(run)

  def sleep(duration: FiniteDuration)(using ec: Executor, scheduler: Scheduler): Funit =
    val p = Promise[Unit]()
    scheduler.scheduleOnce(duration)(p.success(()))
    p.future

  def makeItLast[A](
      duration: FiniteDuration
  )(run: => Fu[A])(using ec: Executor, scheduler: Scheduler): Fu[A] =
    if duration == 0.millis then run
    else run.zip(org.apache.pekko.pattern.after(duration, scheduler)(funit)).dmap(_._1)

  def retry[T](op: () => Fu[T], delay: FiniteDuration, retries: Int, logger: Option[lila.log.Logger])(using
      ec: Executor,
      scheduler: Scheduler
  ): Fu[T] =
    op().recoverWith:
      case e if retries > 0 =>
        logger.foreach { _.info(s"$retries retries - ${e.getMessage}") }
        org.apache.pekko.pattern.after(delay, scheduler)(retry(op, delay, retries - 1, logger))

  def linear[A, B](list: Iterable[A])(f: A => Fu[B])(using ec: scala.concurrent.ExecutionContext): Fu[List[B]] =
    list.foldLeft(fuccess(List.empty[B])) { (acc, a) =>
      acc.flatMap { bs => f(a).map(bs :+ _) }
    }

  def applySequentially[A](list: Iterable[A])(f: A => Funit)(using ec: scala.concurrent.ExecutionContext): Funit =
    list.foldLeft(funit) { (acc, a) =>
      acc.flatMap { _ => f(a) }
    }

  def find[A](list: Iterable[A])(f: A => Fu[Boolean])(using ec: scala.concurrent.ExecutionContext): Fu[Option[A]] =
    list.foldLeft(fuccess(none[A])) { (acc, a) =>
      acc.flatMap {
        case None => f(a).map { if _ then Some(a) else None }
        case res  => fuccess(res)
      }
    }

  def fold[A, B](list: List[A])(zero: B)(f: (B, A) => Fu[B])(using ec: scala.concurrent.ExecutionContext): Fu[B] =
    list.foldLeft(fuccess(zero)) { (acc, a) =>
      acc.flatMap { b => f(b, a) }
    }
