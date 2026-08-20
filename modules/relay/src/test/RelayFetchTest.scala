package lila.relay

import scala.concurrent.Await

import lila.study.MultiPgn

class RelayFetchTest extends munit.FunSuite {

  given ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  private def games(pgn: String) =
    Await.result(RelayFetch.multiPgnToGames(MultiPgn.split(pgn, 64)), 10.seconds)

  private val twoGames = """[Event "Golders Green Rapidplay"]
[Site "London"]
[Round "1.1"]
[White "Carlsen, Magnus"]
[Black "Nakamura, Hikaru"]
[Result "*"]

1. e4 e5 2. Nf3 Nc6 3. Bb5 *

[Event "Golders Green Rapidplay"]
[Site "London"]
[Round "1.2"]
[White "Caruana, Fabiano"]
[Black "Nepomniachtchi, Ian"]
[Result "1-0"]

1. d4 d5 2. c4 e6 3. Nc3 1-0"""

  test("a multi-game chess PGN parses into indexed relay games") {
    val gs = games(twoGames)
    assertEquals(gs.size, 2)
    assertEquals(gs.map(_.index).toList, List(0, 1))
  }

  test("White and Black survive as P1 and P2") {
    val gs = games(twoGames)
    assertEquals(gs.head.tags(_.P1), Some("Carlsen, Magnus"))
    assertEquals(gs.head.tags(_.P2), Some("Nakamura, Hikaru"))
    assertEquals(gs(1).tags(_.P1), Some("Caruana, Fabiano"))
    assertEquals(gs(1).tags(_.P2), Some("Nepomniachtchi, Ian"))
  }

  test("event, site and round survive for chapter matching") {
    val g = games(twoGames).head
    assertEquals(g.tags(_.Event), Some("Golders Green Rapidplay"))
    assertEquals(g.tags(_.Site), Some("London"))
    assertEquals(g.tags(_.Round), Some("1.1"))
  }

  test("ratings and titles survive as P1/P2 tags") {
    val g = games("""[Event "Test"]
[White "Carlsen, Magnus"]
[WhiteElo "2839"]
[WhiteTitle "GM"]
[Black "Nakamura, Hikaru"]
[BlackElo "2802"]
[BlackTitle "GM"]
[Result "*"]

1. e4 *""").head
    assertEquals(g.tags(_.P1Elo), Some("2839"))
    assertEquals(g.tags(_.P1Title), Some("GM"))
    assertEquals(g.tags(_.P2Elo), Some("2802"))
    assertEquals(g.tags(_.P2Title), Some("GM"))
  }

  test("moves land on the mainline") {
    val gs = games(twoGames)
    assertEquals(gs.head.root.mainline.size, 5)
    assertEquals(gs(1).root.mainline.size, 5)
  }

  test("a finished game carries an end, an ongoing one does not") {
    val gs = games(twoGames)
    assert(gs.head.end.isEmpty)
    assert(gs(1).end.isDefined)
  }

  test("games are not reported empty") {
    assert(games(twoGames).forall(!_.isEmpty))
  }

  test("a chess960 PGN parses") {
    val gs = games("""[Event "Test"]
[Variant "Chess960"]
[FEN "bqnbnrkr/pppppppp/8/8/8/8/PPPPPPPP/BQNBNRKR w HFhf - 0 1"]
[Result "*"]

1. e4 e5 *""")
    assertEquals(gs.size, 1)
    assertEquals(gs.head.root.mainline.size, 2)
  }

  private val withClocks = """[Event "Test"]
[White "Carlsen, Magnus"]
[Black "Nakamura, Hikaru"]
[TimeControl "600+5"]
[Result "*"]

1. e4 { [%clk 0:09:57] } e5 { [%clk 0:09:55] } 2. Nf3 { [%clk 0:09:52] } *"""

  test("the Fischer time control survives as a tag") {
    assertEquals(games(withClocks).head.tags(_.TimeControl), Some("600+5"))
  }

  test("clock comments land on the nodes") {
    val mainline = games(withClocks).head.root.mainline
    assertEquals(mainline.map(_.clock.map(_.centis)).toList, List(Option(59700), Option(59500), Option(59200)))
  }

  test("clock comments are stripped from the visible comments") {
    assert(games(withClocks).head.root.mainline.forall(_.comments.value.isEmpty))
  }

  test("a non-chess PGN fails with the chess-only message instead of throwing") {
    val err = intercept[Exception] {
      games("""[Event "Frisian draughts"]
[Variant "Frisian"]
[Result "*"]

1. 32-28 19-23 *""")
    }
    assertEquals(err.getMessage, RelayGame.unsupportedVariant)
  }

  test("an unparseable PGN fails rather than throwing out of the cache") {
    val err = intercept[Exception] {
      games("""[Event "Broken"]
[Result "*"]

1. zz9 qq7 *""")
    }
    assert(err.getMessage.nonEmpty)
  }

  /* Anything a broken or unexpected source can send must come back as a failed
   * future carrying a message, never as a thrown exception escaping the cache:
   * RelayFetch only recovers Exception, and the message is what the broadcaster
   * reads in the sync log panel. */
  private def failureOf(pgn: String): String =
    intercept[Exception](games(pgn)).getMessage

  test("an empty body is reported, not silently accepted") {
    assert(failureOf("").startsWith("Found an empty PGN"))
  }

  test("an HTML error page served instead of PGN fails with a parse message") {
    assert(failureOf("<html><body>404 Not Found</body></html>").nonEmpty)
  }

  test("JSON served where PGN was expected fails with a parse message") {
    assert(failureOf("""{"games":[{"moves":"e4 e5"}]}""").nonEmpty)
  }

  test("an illegal move fails with a parse message") {
    assert(failureOf("""[Event "X"]
[Result "*"]

1. e4 e5 2. Ke2 Ke7 3. Qxz9 *""").nonEmpty)
  }

  test("unbalanced variation parens fail rather than hanging") {
    val pgn = """[Event "X"]
[Result "*"]

1. e4 """ + ("(" * 200) + "e5" + (")" * 200) + " *"
    assert(failureOf(pgn).nonEmpty)
  }

  test("every failure carries a message for the sync log") {
    List("", "<html>nope</html>", "%%%%", """{"a":1}""") foreach { body =>
      val msg = failureOf(body)
      assert(msg != null && msg.nonEmpty, s"empty message for: $body")
    }
  }

  test("tags the parser does not know are dropped without failing the game") {
    val gs = games("""[Foo "bar"]
[Baz "qux"]
[White "A"]
[Black "B"]
[Result "*"]

1. e4 e5 *""")
    assertEquals(gs.size, 1)
    assertEquals(gs.head.tags(_.P1), Some("A"))
  }
}
