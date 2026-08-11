package lila.fishnet

import org.joda.time.DateTime
import play.api.libs.json.{ JsValue, Json }

import lila.fishnet.JsonApi.readers.*

class BackgammonPostTest extends munit.FunSuite {

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

  private val post = payload.as[JsonApi.Request.BackgammonPost]

  private val stored = lila.analyse.BackgammonAnalysis(
    _id = "abcd1234",
    studyId = None,
    player1 = post.player1,
    player2 = post.player2,
    evaluators = post.evaluators,
    games = post.toGames,
    date = DateTime.now,
    fk = None
  )

  private val json: JsValue = Json.toJson(stored)(using lila.analyse.BackgammonAnalysis.matchWrites)
  private val moves         = (json \ "games" \ 0 \ "moves").as[List[JsValue]]
  private val chequer       = moves.head
  private val cands         = (chequer \ "candidates").as[List[JsValue]]

  test("rank is the candidate's index, and only the played one is flagged") {
    assertEquals(cands.map(c => (c \ "rank").as[Int]), List(1, 2))
    assertEquals(cands.map(c => (c \ "played").as[Boolean]), List(false, true))
  }

  test("lose is derived as 1 - win") {
    assertEquals((cands.head \ "probabilities" \ "win").as[Double], 0.535)
    assertEquals((cands.head \ "probabilities" \ "lose").as[Double], 0.465)
  }

  test("evaluator labels come back exactly, cubeless included") {
    assertEquals(cands.map(c => (c \ "evaluator").as[String]), List("Cubeful 2-ply", "Cubeless 0-ply"))
  }

  test("x1000 integers decode to gnubg's 3dp values") {
    assertEquals((cands(1) \ "equity").as[Double], 0.012)
    assertEquals((cands(1) \ "equityDelta").as[Double], -0.057)
    assertEquals((chequer \ "rollLuck").as[Double], 0.066)
  }

  test("rank 1 omits equityDelta rather than sending a zero") {
    assertEquals((cands.head \ "equityDelta").toOption, None)
  }

  test("a chequer play's action and equities are read off the played candidate") {
    assertEquals((chequer \ "action").as[String], "24/15")
    assertEquals((chequer \ "playedEquity").as[Double], 0.012)
    assertEquals((chequer \ "bestAction").as[String], "24/18 13/10")
    assertEquals((chequer \ "bestEquity").as[Double], 0.069)
  }

  test("a decision with no played candidate keeps the action sent on the wire") {
    assertEquals((moves(2) \ "action").as[String], "(not played)")
    assertEquals((moves(2) \ "playedEquity").toOption, None)
  }

  test("player index and kind code map back to names") {
    assertEquals(moves.map(m => (m \ "player").as[String]), List("alice", "bob", "alice"))
    assertEquals(moves.map(m => (m \ "kind").as[String]), List("ChequerPlay", "Dance", "ChequerPlay"))
  }

  test("move number is the index within the game") {
    assertEquals(moves.map(m => (m \ "number").as[Int]), List(1, 2, 3))
  }

  test("a dance carries its action and no candidates") {
    assertEquals((moves(1) \ "action").as[String], "(no legal moves)")
    assertEquals((moves(1) \ "candidates").as[List[JsValue]], Nil)
  }
}
