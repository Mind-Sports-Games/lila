package lila.common

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{ AtomicInteger, AtomicReferenceArray }
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

object DuctHealth {

  final private case class Running(name: String, startedAtNanos: Long)
  final private case class Finished(name: String, waitMillis: Long, runMillis: Long, outcome: String)

  private val recentCapacity = 256

  private val queues  = new ConcurrentHashMap[String, () => Int](64)
  private val running = new ConcurrentHashMap[Long, Running](256)

  private val recent      = new AtomicReferenceArray[Finished](recentCapacity)
  private val recentIndex = new AtomicInteger(0)

  def register(name: String, queueSize: () => Int): Unit = {
    if (!name.contains(':')) queues.put(name, queueSize)
    ()
  }

  def started(id: Long, name: String, startedAtNanos: Long): Unit = {
    running.put(id, Running(name, startedAtNanos))
    ()
  }

  def finished(id: Long, name: String, waitMillis: Long, runMillis: Long, outcome: String): Unit = {
    running.remove(id)
    val slot = math.floorMod(recentIndex.getAndIncrement(), recentCapacity)
    recent.set(slot, Finished(name, waitMillis, runMillis, outcome))
  }

  def recentSize: Int = (0 until recentCapacity).count(recent.get(_) != null)

  private def safeSize(name: String, size: () => Int): Option[Int] =
    try Some(size())
    catch {
      case NonFatal(e) =>
        lila.log("duct").warn(s"health: $name queue size threw", e)
        None
    }

  def report(nowNanos: Long, minRunMillis: Long, minDepth: Int): Option[String] = {
    val deep = queues.asScala.toList
      .flatMap { case (name, size) => safeSize(name, size).map(name -> _) }
      .filter { case (_, depth) => depth >= minDepth }
      .sortBy { case (_, depth) => -depth }
      .map { case (name, depth) => s"  deep $name depth=$depth" }

    val stuck = running.asScala.values.toList
      .map(r => r.name -> (nowNanos - r.startedAtNanos) / 1000000)
      .filter { case (_, ms) => ms >= minRunMillis }
      .sortBy { case (_, ms) => -ms }
      .take(20)
      .map { case (name, ms) => s"  running $name for ${ms}ms" }

    val lines = deep ::: stuck
    if (lines.isEmpty) None else Some(lines.mkString("\n"))
  }

  private val intervalMillis  = 10000L
  private val minRunMillis    = 5000L
  private val minDepth        = 8

  private val reporter = new Thread(
    () =>
      while (true) {
        Thread.sleep(intervalMillis)
        try
          report(System.nanoTime(), minRunMillis, minDepth) foreach { lines =>
            lila.log("duct").info(s"health\n$lines")
          }
        catch {
          case NonFatal(e) => lila.log("duct").warn("health: reporting pass failed", e)
        }
      },
    "lila-duct-health"
  )
  reporter.setDaemon(true)
  reporter.start()
}
