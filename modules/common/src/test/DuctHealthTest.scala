package lila.common

class DuctHealthTest extends munit.FunSuite {

  test("ignore per-key duct names in the registry") {
    DuctHealth.register("tournament:abc123", () => 99)
    assertEquals(DuctHealth.report(0L, 1L, 1), None)
  }

  test("report a duct whose queue is deep enough") {
    DuctHealth.register("fishnetApi", () => 12)
    val out = DuctHealth.report(0L, Long.MaxValue, 8)
    assert(out.exists(_.contains("fishnetApi")), out)
    DuctHealth.register("fishnetApi", () => 0)
  }

  test("report a task running longer than the bar") {
    DuctHealth.started(1L, "slowOne", 0L)
    val out = DuctHealth.report(5000000000L, 1000L, Int.MaxValue)
    assert(out.exists(_.contains("slowOne")), out)
    DuctHealth.finished(1L, "slowOne", 0L, 5000L, "success")
  }

  test("forget a task once it finishes") {
    DuctHealth.started(2L, "quickOne", 0L)
    DuctHealth.finished(2L, "quickOne", 0L, 1L, "success")
    assertEquals(DuctHealth.report(5000000000L, 1000L, Int.MaxValue), None)
  }

  test("bound the recent completions buffer") {
    (1 to 1000).foreach { i =>
      DuctHealth.finished(i.toLong, s"n$i", 0L, 1L, "success")
    }
    assert(DuctHealth.recentSize <= 256, DuctHealth.recentSize)
  }
}
