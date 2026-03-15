package lila.common

import scala.concurrent.duration._

object ResilientScheduler:

  def apply(
      every: Every,
      atMost: AtMost,
      initialDelay: FiniteDuration
  )(f: => Funit)(using ec: Executor, scheduler: Scheduler): Unit =
    val run = () => f
    def runAndScheduleNext(): Unit =
      run()
        .withTimeout(atMost.value, "ResilientScheduler")
        .addEffectAnyway:
          scheduler.scheduleOnce(every.value) { runAndScheduleNext() }
    scheduler.scheduleOnce(initialDelay) { runAndScheduleNext() }
