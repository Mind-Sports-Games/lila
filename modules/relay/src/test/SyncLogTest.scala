package lila.relay

class SyncLogTest extends munit.FunSuite {

  test("an error message is recorded, capped at 100 chars") {
    val event = SyncLog.event(0, Some(new Exception("a" * 200)))
    assertEquals(event.error.map(_.length), Some(100))
    assert(event.isKo)
  }

  /* RelayFetch builds this inside its recover block, so anything thrown here
   * loses the failure: nothing reaches the sync log and sync.nextAt is never
   * pushed forward, leaving the actor to retry the source every 500ms. */
  test("an exception with no message still produces an event") {
    val event = SyncLog.event(0, Some(new NullPointerException))
    assertEquals(event.error, Some("NullPointerException"))
  }

  test("an exception with a blank message still produces an event") {
    val event = SyncLog.event(0, Some(new Exception("")))
    assertEquals(event.error, Some("Exception"))
  }

  test("a timeout is reported as such") {
    val event = SyncLog.event(0, Some(new java.util.concurrent.TimeoutException("whatever")))
    assertEquals(event.error, Some("Request timeout"))
  }

  test("a successful sync is ok and carries no error") {
    val event = SyncLog.event(3, None)
    assert(event.isOk)
    assertEquals(event.error, None)
  }

  test("the log keeps only the last five events") {
    val log = (1 to 20).foldLeft(SyncLog.empty) { case (l, i) => l.add(SyncLog.event(i, None)) }
    assertEquals(log.events.size, SyncLog.historySize)
    assertEquals(log.events.map(_.moves).toList, List(16, 17, 18, 19, 20))
  }

  /* Drives the backoff in RelayFetch.continueRelay: a source that has failed
   * five times running is polled every 60s instead of every 6s. */
  test("alwaysFails only once the whole history is failures") {
    val err  = SyncLog.event(0, Some(new Exception("nope")))
    val ok   = SyncLog.event(1, None)
    val bad  = (1 to SyncLog.historySize).foldLeft(SyncLog.empty) { case (l, _) => l.add(err) }
    val some = bad.add(ok)
    assert(bad.alwaysFails)
    assert(!some.alwaysFails)
    assert(!SyncLog.empty.alwaysFails)
  }

  /* The window has to slide off the old successes, or a round that synced fine
   * and then broke would poll a dead source every 6s until sync.until lapsed. */
  test("a source that worked and then broke eventually reports alwaysFails") {
    val err = SyncLog.event(0, Some(new Exception("nope")))
    val log = (1 to 10)
      .foldLeft(SyncLog.empty) { case (l, i) => l.add(SyncLog.event(i, None)) }
      .pipe(good => (1 to SyncLog.historySize).foldLeft(good) { case (l, _) => l.add(err) })
    assert(log.alwaysFails)
  }
}
