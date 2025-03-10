package lila.common

import scala.concurrent.duration._

object ResilientScheduler {

  private case object Tick
  private case object Done

  def apply(
      every: Every,
      atMost: AtMost,
      initialDelay: FiniteDuration
  )(f: => Funit)(implicit ec: scala.concurrent.ExecutionContext, scheduler: akka.actor.Scheduler): Unit = {
    val run = () => f
    def runAndScheduleNext(): Unit =
      run()
        .withTimeout(atMost.value)
        .addEffectAnyway {
          scheduler.scheduleOnce(every.value) { runAndScheduleNext() }.unit
        }
        .unit
    scheduler
      .scheduleOnce(initialDelay) {
        runAndScheduleNext()
      }
      .unit
  }
}
