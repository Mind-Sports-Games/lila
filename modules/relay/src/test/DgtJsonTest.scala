package lila.relay

import play.api.libs.json.Json
import scala.concurrent.Await

import lila.study.MultiPgn

/* LiveChessCloud is the path a DGT smart-board tournament takes to reach us: the
 * organiser's software uploads to livechesscloud.com and RelayFormat rewrites the
 * broadcaster's view.livechesscloud.com URL into
 * http://1.pool.livechesscloud.com/get/<uuid>/round-N/index.json.
 *
 * The payloads below are trimmed but otherwise verbatim from that service. They
 * are here because the JSON keys are DGT's, not ours, and deriving the Reads from
 * our case classes silently tied them to our field names: the P1/P2 rename turned
 * `white`/`black` into `p1`/`p2` and the multiaction rename turned `moves` into
 * `turns`, and LCC broadcasts stopped parsing with nothing but an
 * "error.path.missing" in the sync log to show for it. */
class DgtJsonTest extends munit.FunSuite {

  given ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  import RelayFetch.DgtJson.*

  private val indexJson = """{
  "date": "2026-03-11",
  "pairings": [
    {
      "white": { "fname": "Abhimanyu", "mname": null, "lname": "Puranik",
                 "title": "GM", "federation": null, "gender": null, "fideid": 5061245 },
      "black": { "fname": "K", "mname": "G", "lname": "Akhil",
                 "title": null, "federation": null, "gender": null, "fideid": 35051148 },
      "result": "1-0",
      "live": false
    },
    {
      "white": { "fname": "R", "mname": null, "lname": "Arun",
                 "title": null, "federation": null, "gender": null, "fideid": 46637338 },
      "black": { "fname": "Murali", "mname": null, "lname": "Karthikeyan",
                 "title": "GM", "federation": null, "gender": null, "fideid": 5074452 },
      "result": "0-1",
      "live": true
    }
  ]
}"""

  private val gameJson = """{
  "live": false,
  "serialNr": 47083,
  "firstMove": 1766555192374,
  "chess960": 518,
  "result": "WHITEWIN",
  "comment": null,
  "clock": null,
  "moves": ["b3 918+1", "d5 912+7", "Bb2 926+2", "Nf6 918+5", "Nf3 934+2", "c5 924+4",
            "e3 942+2", "Nc6 931+3", "Bb5 950+2", "Qc7 919+22", "O-O 937+67", "Bg4 916+13"]
}"""

  private def round = Json.parse(indexJson).as[RoundJson]
  private def game  = Json.parse(gameJson).as[GameJson]

  /* Note the asymmetry, and that it is deliberate: the JSON is keyed on white and
   * black, the Scala fields are p1 and p2, and the explicit Reads is what bridges
   * them. That is what lets the codebase keep renaming players without touching
   * DGT's contract. */
  test("a real index.json parses, keyed on white and black") {
    assertEquals(round.pairings.size, 2)
    assertEquals(round.pairings.head.p1.lname, Some("Puranik"))
    assertEquals(round.pairings.head.p2.lname, Some("Akhil"))
  }

  /* The guard for the rename that broke this. If the Reads ever goes back to
   * being derived from the case class, LCC's payload stops parsing and this
   * fails - rather than the sync failing silently in production. */
  test("pairings are read by LCC's field names, not ours") {
    val ours = """{"pairings":[{"p1":{"fname":"A","mname":null,"lname":"B","title":null},
                                "p2":{"fname":"C","mname":null,"lname":"D","title":null},
                                "result":"1-0"}]}"""
    assert(Json.parse(ours).validate[RoundJson].isError)
  }

  test("the move list is read from moves, not turns") {
    assertEquals(game.turns.size, 12)
    assert(Json.parse("""{"turns":["e4 100+1"]}""").validate[GameJson].isError)
  }

  test("names join first, middle and last") {
    assertEquals(round.pairings.head.p1.fullName, Some("Abhimanyu Puranik"))
    // K G Akhil carries a middle name, which has to survive into the tag
    assertEquals(round.pairings.head.p2.fullName, Some("K G Akhil"))
  }

  test("pairings become P1/P2 tags with titles and result") {
    val tags = round.pairings.head.tags
    assertEquals(tags(_.P1), Some("Abhimanyu Puranik"))
    assertEquals(tags(_.P1Title), Some("GM"))
    assertEquals(tags(_.P2), Some("K G Akhil"))
    assertEquals(tags(_.P2Title), None) // untitled, so no tag rather than an empty one
    assertEquals(tags(_.Result), Some("1-0"))
  }

  test("a title on the black side lands on P2") {
    val tags = round.pairings(1).tags
    assertEquals(tags(_.P1Title), None)
    assertEquals(tags(_.P2Title), Some("GM"))
    assertEquals(tags(_.Result), Some("0-1"))
  }

  /* "b3 918+1" is SAN then seconds remaining then the increment gained. The
   * event this came from was G90+30, so 918 is seconds, not centiseconds. */
  test("clocks are read as seconds remaining and rendered as %clk") {
    val pgn = game.toPgn()
    assert(pgn.contains("b3"), pgn)
    assert(pgn.contains("[%clk 0:15:18]"), pgn) // 918s
    assert(!pgn.contains("918+1"), "the increment suffix must not leak into the PGN")
  }

  /* LCC's own result is a word, not a PGN result, and toPgn must not emit it -
   * the usable result is the one on the pairing in index.json. */
  test("the game's WHITEWIN result is parsed but kept out of the PGN") {
    assertEquals(game.result, Some("WHITEWIN"))
    assert(!game.toPgn().contains("WHITEWIN"))
  }

  /* The whole path RelayFetch takes for a ManyFiles source: pairing tags from
   * index.json, moves from game-N.json, combined into a PGN and parsed. */
  test("a pairing and its game combine into a game we can parse") {
    val pgn = game.toPgn(round.pairings.head.tags)
    val gs  = Await.result(RelayFetch.multiPgnToGames(MultiPgn.split(pgn, 64)), 10.seconds)
    assertEquals(gs.size, 1)
    assertEquals(gs.head.tags(_.P1), Some("Abhimanyu Puranik"))
    assertEquals(gs.head.tags(_.P2), Some("K G Akhil"))
    assertEquals(gs.head.root.mainline.size, 12)
    assert(gs.head.root.mainline.forall(_.clock.isDefined))
  }

  /* LCC gives names, titles and a result and nothing else - no Event, Site,
   * Round, Board or rating. RelayGame.staticTags compares the absent ones as
   * None == None on both sides, so chapter matching still works on the players
   * alone. Pinned because it is the difference between a broadcast that matches
   * boards across syncs and one that duplicates chapters. */
  test("the thin LCC tag set still leaves games matchable") {
    val pgn = game.toPgn(round.pairings.head.tags)
    val gs  = Await.result(RelayFetch.multiPgnToGames(MultiPgn.split(pgn, 64)), 10.seconds)
    assertEquals(gs.head.tags(_.Event), None)
    assertEquals(gs.head.tags(_.Round), None)
    assert(gs.head.staticTagsMatch(gs.head.tags))
  }

  /* A broadcaster pastes whatever their organiser sent them, which may be upper
   * case or lack the fragment marker. All of these have to reach the LCC branch
   * of RelayFormat.guessFormat; anything that misses it falls through to the
   * generic guessers and dies with "No games found". */
  test("the LCC url is recognised whatever case and shape it arrives in") {
    val id  = "eece909b-5005-48f3-bb31-f8ca9b068220"
    val re  = RelayRound.Sync.UpstreamUrl.LccRegex
    val ok  = List(
      s"http://view.livechesscloud.com/#$id",
      s"https://view.livechesscloud.com/#$id",
      s"http://view.livechesscloud.com/$id",
      s"http://view.livechesscloud.com/#${id.toUpperCase}"
    )
    ok foreach { url => assert(re.matches(url), url) }
    assert(!re.matches("http://example.org/games.pgn"))
  }
}
