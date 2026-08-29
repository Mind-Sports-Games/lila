package lila.common

import java.lang.management.ManagementFactory
import java.util.concurrent.{ ArrayBlockingQueue, Executors, TimeUnit }
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.ExecutionContext

/** Detects and characterises application-wide stalls.
  *
  * The application has two independent scarce resources that can each stall everything: the default
  * execution context (a bounded fork-join pool shared by HTTP handling, Mongo callbacks, ducts and akka
  * streams) and the akka scheduler, which drives every periodic actor and every future timeout. When those
  * stall, the resulting logs are indistinguishable: DuctSequencer timeouts and actor ReceiveTimeouts fire
  * together regardless of the cause.
  *
  * This runs two probes on a dedicated thread, outside both resources, so it keeps measuring while they are
  * saturated:
  *
  *   - execution context latency: how long a trivial task waits before the pool runs it. High means no free
  *     pool threads, i.e. threads are blocked or saturated.
  *   - scheduler drift: how late the akka scheduler fires a short timer, with its callback delivered on a
  *     private thread so a saturated pool cannot contaminate the reading. High means the scheduler itself is
  *     behind, which makes periodic actors miss heartbeats even when the work they do is fast.
  *
  * The two readings together identify the failure mode:
  *
  *   - context latency high, drift low: pool exhaustion. The captured thread dump names the culprit.
  *   - both high: the whole JVM is descheduled. Check GC and safepoint logs.
  *   - both low while timeouts still fire: the work itself is genuinely waiting, most likely on I/O, and the
  *     concurrency layer is not at fault.
  *
  * On a stall it captures duct state and a thread dump, summarised by top stack frame so that many threads
  * parked in one place are obvious at a glance.
  */
final class StallDetector(
    stallThresholdMillis: Long = 1000,
    probeIntervalMillis: Long = 200,
    dumpCooldownMillis: Long = 30000,
    probeTimeoutMillis: Long = 15000
)(implicit ec: ExecutionContext, scheduler: akka.actor.Scheduler) {

  private val logger = lila.log("stall")

  // The scheduler probe's callback must not run on the pool we are also measuring, otherwise a saturated
  // pool would show up as scheduler drift and the two readings would stop being independent.
  private val schedulerCallbackEc: ExecutionContext =
    ExecutionContext.fromExecutorService(
      Executors.newSingleThreadExecutor { r =>
        val t = new Thread(r, "lila-stall-scheduler-probe")
        t.setDaemon(true)
        t
      }
    )

  private val lastDumpAt = new AtomicLong(0)

  private val thread = new Thread(() => probeLoop(), "lila-stall-detector")
  thread.setDaemon(true)
  thread.start()

  logger.info(
    s"stall detector started, threshold=${stallThresholdMillis}ms interval=${probeIntervalMillis}ms"
  )

  private def probeLoop(): Unit =
    while (true) {
      val contextLatency = probeExecutionContext()
      val drift          = probeScheduler()

      lila.mon.stall.contextLatency.record(contextLatency)
      lila.mon.stall.schedulerDrift.record(drift)

      if (contextLatency > stallThresholdMillis || drift > stallThresholdMillis)
        onStall(contextLatency, drift)

      Thread.sleep(probeIntervalMillis)
    }

  /** Milliseconds between submitting a no-op task and the pool running it. */
  private def probeExecutionContext(): Long = {
    val handoff = new ArrayBlockingQueue[java.lang.Long](1)
    val startedAt = System.nanoTime()
    ec.execute { () =>
      handoff.offer(java.lang.Long.valueOf((System.nanoTime() - startedAt) / 1000000))
      ()
    }
    Option(handoff.poll(probeTimeoutMillis, TimeUnit.MILLISECONDS))
      .fold(probeTimeoutMillis)(_.longValue)
  }

  /** Milliseconds by which the akka scheduler overshoots a 100ms timer. */
  private def probeScheduler(): Long = {
    val handoff   = new ArrayBlockingQueue[java.lang.Long](1)
    val startedAt = System.nanoTime()
    scheduler.scheduleOnce(100.millis) {
      handoff.offer(java.lang.Long.valueOf(((System.nanoTime() - startedAt) / 1000000) - 100))
      ()
    }(using schedulerCallbackEc)
    Option(handoff.poll(probeTimeoutMillis, TimeUnit.MILLISECONDS))
      .fold(probeTimeoutMillis)(_.longValue)
  }

  private def onStall(contextLatency: Long, drift: Long): Unit = {
    lila.mon.stall.detected.increment()
    val now = System.currentTimeMillis()
    if (now - lastDumpAt.get() >= dumpCooldownMillis) {
      lastDumpAt.set(now)
      logger.warn(
        s"STALL contextLatency=${contextLatency}ms schedulerDrift=${drift}ms\n" +
          s"ducts:\n${DuctRegistry.dump()}\n" +
          s"threads:\n${threadReport()}"
      )
    }
  }

  private def threadReport(): String = {
    val infos = ManagementFactory.getThreadMXBean.dumpAllThreads(false, false).toList

    val byState = infos
      .groupBy(_.getThreadState)
      .view
      .mapValues(_.size)
      .toList
      .sortBy(-_._2)
      .map { case (state, n) => s"$state=$n" }
      .mkString(" ")

    // Many threads sharing a top frame is the signature of a common block: a lock, a blocking await, or a
    // saturated I/O pool. This histogram surfaces it without needing to read every stack.
    val hotFrames = infos
      .flatMap(_.getStackTrace.headOption.map(_.toString))
      .groupBy(identity)
      .view
      .mapValues(_.size)
      .toList
      .sortBy(-_._2)
      .take(10)
      .map { case (frame, n) => s"    ${n}x $frame" }
      .mkString("\n")

    val interesting = infos
      .filter { t =>
        val name = t.getThreadName
        name.contains("dispatcher") || name.contains("lila") || name.contains("reactivemongo")
      }
      .sortBy(_.getThreadName)
      .map { t =>
        val lock  = Option(t.getLockName).fold("")(l => s" blockedOn=$l")
        val owner = Option(t.getLockOwnerName).fold("")(o => s" lockOwner=$o")
        val stack = t.getStackTrace.take(15).map(f => s"      at $f").mkString("\n")
        s"  ${t.getThreadName} ${t.getThreadState}$lock$owner\n$stack"
      }
      .mkString("\n")

    s"  states: $byState\n  hottest frames:\n$hotFrames\n$interesting"
  }
}
