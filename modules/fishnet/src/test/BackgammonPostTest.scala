package lila.fishnet

import play.api.libs.json.Json

import lila.fishnet.JsonApi.readers.*

/** The compact wire format mindcube posts, decoded back into the stored model.
  * mindcube owns the encoder and never decodes, so this is the only test of the
  * half of the contract lila is responsible for.
  */
class BackgammonPostTest extends munit.FunSuite {

  // one chequer play (two candidates, the second one played), one dance, and a
  // chequer play the game ended before: no candidate is starred, so its action
  // has to travel on the wire.
  private val payload = Json.parse("""{
    "p1": "alice",
    "p2": "bob",
    "e": ["Cubeful 2-ply", "Cubeless 0-ply"],
    "g": [{
      "n": 1,
      "w": { "player": "alice", "points": 2, "winType": "gammon" },
      "s": [],
      "m": [
        { "u": 1, "k": 0, "i": "63", "l": 66, "y": 1, "c": [
            ["24/18 13/10", 0, 69, null, 535, 150, 6, 140, 5],
            ["24/15",       1, 12, -57,  504, 123, 5, 135, 4]
        ]},
        { "u": 2, "k": 1, "i": "55", "a": "(no legal moves)", "c": [] },
        { "u": 1, "k": 0, "i": "21", "a": "(not played)", "c": [
            ["13/11 6/5", 0, 40, null, 512, 130, 4, 132, 3]
        ]}
      ]
    }]
  }""")

  private val decoded = payload.as[JsonApi.Request.BackgammonPost]
  private val moves   = decoded.toGames.head.moves
  private val chequer = moves.head

  test("rank is the candidate's index, and only the played one is flagged") {
    assertEquals(chequer.candidates.map(_.rank), List(1, 2))
    assertEquals(chequer.candidates.map(_.played), List(false, true))
  }

  test("lose is derived as 1 - win") {
    assertEquals(chequer.candidates.head.probabilities.win, 0.535)
    assertEquals(chequer.candidates.head.probabilities.lose, 0.465)
  }

  test("evaluator labels come back exactly, cubeless included") {
    assertEquals(chequer.candidates.map(_.evaluator), List("Cubeful 2-ply", "Cubeless 0-ply"))
  }

  test("x1000 integers decode to gnubg's 3dp values") {
    val c = chequer.candidates(1)
    assertEquals(c.equity, 0.012)
    assertEquals(c.equityDelta, Some(-0.057))
    assertEquals(chequer.rollLuck, Some(0.066))
  }

  test("rank 1 keeps a null equityDelta rather than a zero") {
    assertEquals(chequer.candidates.head.equityDelta, None)
  }

  test("a chequer play's action and equities are read off the played candidate") {
    assertEquals(chequer.action, "24/15")
    assertEquals(chequer.playedEquity, Some(0.012))
    assertEquals(chequer.bestAction, Some("24/18 13/10"))
    assertEquals(chequer.bestEquity, Some(0.069))
  }

  test("a decision with no played candidate keeps the action sent on the wire") {
    assertEquals(moves(2).action, "(not played)")
    assertEquals(moves(2).playedEquity, None)
  }

  test("player index and kind code map back to names") {
    assertEquals(moves.map(_.player), List("alice", "bob", "alice"))
    assertEquals(moves.map(_.kind), List("ChequerPlay", "Dance", "ChequerPlay"))
  }

  test("move number is the index within the game") {
    assertEquals(moves.map(_.number), List(1, 2, 3))
  }

  test("a dance carries its action and no candidates") {
    assertEquals(moves(1).action, "(no legal moves)")
    assertEquals(moves(1).candidates, Nil)
  }
}
