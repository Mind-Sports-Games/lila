package lila.relay

import org.joda.time.DateTime
import scala.concurrent.Await

import lila.study.MultiPgn

/* Once every game of a round has ended there is nothing left to poll for, so
 * RelayFetch.continueRelay hangs up instead of hitting the source every 6s for
 * the hour it takes sync.until to lapse. These pin the two halves of that: the
 * predicate that decides a round is over, and the state a finished round is
 * left in. */
class RelayRoundTest extends munit.FunSuite {

  given ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  private def games(pgn: String) =
    Await.result(RelayFetch.multiPgnToGames(MultiPgn.split(pgn, 64)), 10.seconds)

  private def game(round: String, result: String) = s"""[Event "Rapidplay"]
[Site "London"]
[Round "$round"]
[White "Carlsen, Magnus"]
[Black "Nakamura, Hikaru"]
[Result "$result"]

1. e4 e5 2. Nf3 Nc6 3. Bb5 $result"""

  private val round = RelayRound(
    _id = RelayRound.Id("abcd1234"),
    tourId = RelayTour.Id("tour1234"),
    name = "Round 1",
    sync = RelayRound.Sync(
      upstream = RelayRound.Sync.UpstreamUrl("http://example.org/games.pgn").some,
      until = none,
      nextAt = none,
      delay = none,
      log = SyncLog.empty
    ),
    startsAt = none,
    startedAt = none,
    finished = false,
    createdAt = DateTime.now
  )

  test("a round whose games have all ended is over") {
    assert(RelayFetch.allGamesEnded(games(s"${game("1.1", "1-0")}\n\n${game("1.2", "0-1")}")))
  }

  test("a round with one game still running is not over") {
    assert(!RelayFetch.allGamesEnded(games(s"${game("1.1", "1-0")}\n\n${game("1.2", "*")}")))
  }

  /* forall on an empty vector is true. A source yielding nothing for one tick -
   * an empty game index, an UpstreamIds round whose games have not appeared yet
   * - must not end the round, because ending it drops the connection for good.
   * A body that fails to parse fails the sync instead, and never gets here. */
  test("a source that yields no games at all is not over") {
    assert(!RelayFetch.allGamesEnded(Vector.empty))
  }

  /* toSync selects on sync.until existing and sync.nextAt being in the past, so
   * clearing both is what actually takes the round out of the poll loop. */
  test("finishing takes the round out of the sync selector") {
    val done = round.withSync(_.play).finish
    assert(done.finished)
    assertEquals(done.sync.until, none)
    assertEquals(done.sync.nextAt, none)
    assert(done.sync.paused)
    assert(!done.sync.ongoing)
  }

  test("a live round is in the sync selector") {
    val live = round.withSync(_.play)
    assert(live.sync.playing)
    assert(live.sync.ongoing)
  }

  /* requestPlay(v = true) resumes rather than only calling sync.play: clicking
   * connect on a finished round has to clear `finished` too, or the next tick
   * would see an already-finished round and hang up again. */
  test("resuming a finished round makes it live again") {
    val again = round.withSync(_.play).finish.resume
    assert(!again.finished)
    assert(again.sync.playing)
    assert(again.sync.ongoing)
  }

  /* autoFinishNeverStarted selects on the three flags and then defers the
   * decision to shouldGiveUp, so these are the rule it actually applies. The
   * round it exists for is the one with no start date at all: nothing else
   * finishes that, and while it stays unfinished its tour stays active and an
   * official one holds the homepage spotlight. */
  test("a round with no start date is given up on a day after creation") {
    assert(!round.copy(createdAt = DateTime.now.minusHours(23)).shouldGiveUp)
    assert(round.copy(createdAt = DateTime.now.minusDays(2)).shouldGiveUp)
  }

  test("a round with a start date is given up on three hours after it") {
    val at = round.copy(createdAt = DateTime.now.minusDays(9))
    assert(!at.copy(startsAt = DateTime.now.plusDays(1).some).shouldGiveUp)
    assert(!at.copy(startsAt = DateTime.now.minusHours(2).some).shouldGiveUp)
    assert(at.copy(startsAt = DateTime.now.minusHours(4).some).shouldGiveUp)
  }

  /* autoStart only looks back a day, so a startsAt older than that is never
   * picked up - the case where lila was down across the window. */
  test("a round whose start date is long past is still given up on") {
    assert(round.copy(startsAt = DateTime.now.minusDays(30).some).shouldGiveUp)
  }

  test("a round that has started is never given up on, however old") {
    val started = round.copy(
      startedAt = DateTime.now.minusDays(30).some,
      createdAt = DateTime.now.minusDays(30),
      startsAt = DateTime.now.minusDays(30).some
    )
    assert(!started.shouldGiveUp)
  }

  /* Giving up is not destructive: connecting the round by hand resumes it,
   * which is what makes this safe to apply to a round someone created early. */
  test("a round given up on can be connected again") {
    val revived = round.finish.resume
    assert(!revived.finished)
    assert(revived.sync.ongoing)
  }
}
