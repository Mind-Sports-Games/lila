package lila.common

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

/** Diagnostic registry of every Duct and DuctSequencer in the JVM.
  *
  * Ducts are the serialisation points of the application: each one runs at most one task at a time, so a
  * single slow or wedged task stalls everything queued behind it. The registry records, for each duct, how
  * deep its queue is and which task (if any) is currently running, so that a stall can be attributed to a
  * specific duct rather than inferred from a scattering of timeout logs.
  *
  * Registration is by name. DuctSequencer names are unique by construction; DuctConcMap-backed ducts (round
  * games, study chapters) share a single registry entry per map, reporting aggregate depth.
  */
object DuctRegistry {

  final private case class Running(id: Long, startedAtNanos: Long, label: String)

  final private class Entry(val queueSize: () => Int) {
    val running = new ConcurrentHashMap[Long, Running](4)
  }

  private val entries = new ConcurrentHashMap[String, Entry](256)

  def register(name: String, queueSize: () => Int): Unit = {
    entries.put(name, new Entry(queueSize))
    ()
  }

  def started(name: String, id: Long, startedAtNanos: Long, label: String): Unit = {
    Option(entries.get(name)).foreach(_.running.put(id, Running(id, startedAtNanos, label)))
    ()
  }

  def finished(name: String, id: Long): Unit = {
    Option(entries.get(name)).foreach(_.running.remove(id))
    ()
  }

  /** Names and depths only, cheap enough to sample on a timer. */
  def depths: List[(String, Int)] =
    entries.asScala.toList.map { case (name, entry) => name -> entry.queueSize() }

  /** Human readable snapshot for stall logs. Only reports ducts that are busy or backed up, so the output
    * stays readable when a few hundred ducts are registered.
    */
  def dump(nowNanos: Long = System.nanoTime()): String = {
    val lines = entries.asScala.toList
      .flatMap { case (name, entry) =>
        val depth   = entry.queueSize()
        val running = entry.running.values.asScala.toList
        if (depth <= 0 && running.isEmpty) None
        else {
          val runningStr =
            if (running.isEmpty) "idle"
            else
              running
                .sortBy(_.startedAtNanos)
                .map(r => s"#${r.id}[${r.label}] for ${(nowNanos - r.startedAtNanos) / 1000000}ms")
                .mkString(", ")
          val oldest = running.map(r => (nowNanos - r.startedAtNanos) / 1000000).maxOption.getOrElse(0L)
          Some((oldest, depth, s"  $name depth=$depth $runningStr"))
        }
      }
      .sortBy { case (oldest, depth, _) => (-oldest, -depth) }
      .map(_._3)
    if (lines.isEmpty) "  (all ducts idle and empty)"
    else lines.take(40).mkString("\n")
  }
}
