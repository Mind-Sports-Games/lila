package lila.common

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

object DuctHealth {

  final private case class Running(name: String, startedAtNanos: Long)

  private val queues  = new ConcurrentHashMap[String, () => Int](64)
  private val running = new ConcurrentHashMap[Long, Running](256)

  def register(name: String, queueSize: () => Int): Unit = {
    if (!name.contains(':')) queues.put(name, queueSize)
    ()
  }

  def started(id: Long, name: String, startedAtNanos: Long): Unit = {
    running.put(id, Running(name, startedAtNanos))
    ()
  }

  def finished(id: Long): Unit = {
    running.remove(id)
    ()
  }

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
    () => {
      var keepRunning = true
      while (keepRunning)
        try {
          Thread.sleep(intervalMillis)
          report(System.nanoTime(), minRunMillis, minDepth) foreach { lines =>
            lila.log("duct").info(s"health\n$lines")
          }
        } catch {
          case _: InterruptedException =>
            Thread.currentThread().interrupt()
            keepRunning = false
          case NonFatal(e) => lila.log("duct").warn("health: reporting pass failed", e)
        }
    },
    "lila-duct-health"
  )
  reporter.setDaemon(true)
  reporter.start()
}
