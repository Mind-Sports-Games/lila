package lila.study

import strategygames.variant.Variant

class SgfDumpTest extends munit.FunSuite {

  private def variant(key: String) = Variant.apply(key).get

  private def tagString(tags: List[strategygames.format.sgf.Tag]) = tags.mkString(" ")

  // gnubg reads the variant from RU only. Without it a hyper/nackgammon chapter loads
  // onto the standard 15-checker starting position and replays into nonsense — which
  // nothing on the lila side can see, since the damage only shows in an external tool.
  test("RU carries the backgammon variant") {
    assertEquals(
      SgfDump.backgammonTags(variant("backgammon")).find(_.name.name == "RU").map(_.value),
      Some("Crawford")
    )
    assertEquals(
      SgfDump.backgammonTags(variant("hyper")).find(_.name.name == "RU").map(_.value),
      Some("Crawford:Hypergammon3")
    )
    assertEquals(
      SgfDump.backgammonTags(variant("nackgammon")).find(_.name.name == "RU").map(_.value),
      Some("Crawford:Nackgammon")
    )
  }

  test("no SU tag, which gnubg would ignore anyway") {
    List("backgammon", "hyper", "nackgammon") foreach { key =>
      assert(!SgfDump.backgammonTags(variant(key)).exists(_.name.name == "SU"), key)
    }
  }

  test("a chapter is one game, so MI reports game 0 of a 1 point match") {
    assertEquals(
      SgfDump.backgammonTags(variant("backgammon")).find(_.name.name == "MI").map(_.value),
      Some("length:1][game:0][ws:0][bs:0")
    )
  }

  // Backgammon's P1 is white, so PW takes the P1 tag and PB the P2 tag.
  test("player tags follow the variant's colours") {
    val tags = SgfDump.playerTags(variant("backgammon"), Some("alice"), Some("bob"))
    assertEquals(tagString(tags), "PB[bob] PW[alice]")
  }

  test("a missing player name drops its tag rather than emitting an empty one") {
    assertEquals(tagString(SgfDump.playerTags(variant("backgammon"), None, Some("bob"))), "PB[bob]")
    assertEquals(tagString(SgfDump.playerTags(variant("backgammon"), None, None)), "")
  }
}
