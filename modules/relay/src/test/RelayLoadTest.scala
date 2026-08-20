package lila.relay

import scala.concurrent.Await

import lila.study.MultiPgn

/* A 60-board tournament is the size PlayStrategy expects to run, and it sits
 * just under the 64-game cap that MultiPgn.split applies for a non-official
 * tour. These check that a feed that size parses whole, and that nothing in the
 * fetch path truncates or chokes on it. */
class RelayLoadTest extends munit.FunSuite {

  given ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  private val boards = 60
  private val plies  = 60

  private def board(i: Int, upto: Int, result: String): String = {
    val moves = (0 until upto)
      .map { p =>
        val san = if (p % 4 == 0) "Nf3" else if (p % 4 == 1) "Nf6" else if (p % 4 == 2) "Ng1" else "Ng8"
        val clk = f"0:0${9 - (p / 12)}%s:${59 - (p * 7) % 60}%02d"
        val no  = if (p % 2 == 0) s"${p / 2 + 1}. " else ""
        s"$no$san { [%clk $clk] }"
      }
      .mkString(" ")
    s"""[Event "Mind Sports Olympiad Chess Open"]
[Site "London ENG"]
[Round "3.${i + 1}"]
[White "Player, W$i"]
[WhiteElo "${2400 + i}"]
[Black "Player, B$i"]
[BlackElo "${2500 + i}"]
[Board "${i + 1}"]
[TimeControl "600+5"]
[Result "$result"]

$moves $result
"""
  }

  private def feed(upto: Int, result: String) =
    (0 until boards).map(board(_, upto, result)).mkString("\n")

  private def parse(pgn: String) =
    Await.result(RelayFetch.multiPgnToGames(MultiPgn.split(pgn, 64)), 30.seconds)

  test("a 60 board feed parses every game") {
    val gs = parse(feed(plies, "*"))
    assertEquals(gs.size, boards)
    assertEquals(gs.map(_.index).toList, (0 until boards).toList)
  }

  test("board identity survives across all 60") {
    val gs = parse(feed(plies, "*"))
    assertEquals(gs.head.tags(_.P1), Some("Player, W0"))
    assertEquals(gs.last.tags(_.P1), Some(s"Player, W${boards - 1}"))
    assertEquals(gs.map(_.tags(_.Round)).toList.distinct.size, boards)
  }

  test("every board carries its full move list and clocks") {
    val gs = parse(feed(plies, "*"))
    assert(gs.forall(_.root.mainline.size == plies))
    assert(gs.forall(_.root.mainline.forall(_.clock.isDefined)))
  }

  test("a finished 60 board round reports every game as ended") {
    val gs = parse(feed(plies, "1-0"))
    assertEquals(gs.count(_.end.isDefined), boards)
    // this is what flips RelayRound.finished in RelayFetch.processRelay
    assert(gs.forall(_.end.isDefined))
  }

  /* MultiPgn.split caps at RelayFetch.maxChapters: 64 for a normal tour, 128
   * for an official one. Anything past the cap is dropped silently, so a
   * tournament bigger than that needs splitting across rounds. */
  test("a feed beyond the cap is truncated, not rejected") {
    val big = (0 until 80).map(board(_, 4, "*")).mkString("\n")
    assertEquals(parse(big).size, 64)
    assertEquals(Await.result(RelayFetch.multiPgnToGames(MultiPgn.split(big, 128)), 30.seconds).size, 80)
  }

  test("the payload for 60 boards stays in the tens of kilobytes") {
    val bytes = feed(plies, "*").getBytes("UTF-8").length
    assert(bytes > 50 * 1024, s"expected a realistic payload, got $bytes bytes")
    assert(bytes < 512 * 1024, s"60 boards should not approach a megabyte, got $bytes bytes")
  }
}
