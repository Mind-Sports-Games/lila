package lila.relay

class RelayGameTest extends munit.FunSuite {

  private def pgn(tags: String) =
    s"""$tags
[White "Carlsen, Magnus"]
[Black "Nakamura, Hikaru"]

1. e4 e5 2. Nf3 *"""

  test("no variant tag is chess") {
    assert(RelayGame.isChessOnly(pgn("")))
  }
  test("standard variant tag is chess") {
    assert(RelayGame.isChessOnly(pgn("""[Variant "Standard"]""")))
  }
  test("chess960 variant tag is chess") {
    assert(RelayGame.isChessOnly(pgn("""[Variant "Chess960"]""")))
  }
  test("fischerandom variant tag is chess") {
    assert(RelayGame.isChessOnly(pgn("""[Variant "Fischerandom"]""")))
  }
  test("chess variants stay within chess logic") {
    assert(RelayGame.isChessOnly(pgn("""[Variant "Atomic"]""")))
    assert(RelayGame.isChessOnly(pgn("""[Variant "Antichess"]""")))
    assert(RelayGame.isChessOnly(pgn("""[Variant "Three-check"]""")))
  }
  test("unrecognised variant tag falls through to chess") {
    assert(RelayGame.isChessOnly(pgn("""[Variant "Something Else"]""")))
  }

  test("draughts variant tag is rejected") {
    assert(!RelayGame.isChessOnly(pgn("""[Variant "Frisian"]""")))
  }
  test("draughts GameType tag is rejected") {
    assert(!RelayGame.isChessOnly(pgn("""[GameType "20"]""")))
  }
  test("go variant tag is rejected") {
    assert(!RelayGame.isChessOnly(pgn("""[Variant "Go 19x19"]""")))
  }
  test("backgammon variant tag is rejected") {
    assert(!RelayGame.isChessOnly(pgn("""[Variant "Backgammon"]""")))
  }

  test("variant tag is matched case insensitively") {
    assert(!RelayGame.isChessOnly(pgn("""[variant "Frisian"]""")))
  }
  test("variant tag is matched beyond the first line") {
    assert(!RelayGame.isChessOnly(s"""[Event "Test"]
[Variant "Frisian"]

1. 32-28 *"""))
  }
  test("a variant named inside movetext is not a tag") {
    assert(RelayGame.isChessOnly(pgn("""[Annotator "Frisian expert"]""")))
  }

  private def renamed(tags: String) = RelayGame.withPlayStrategyPlayerTags(tags)

  test("White and Black become P1 and P2") {
    assertEquals(renamed("""[White "Carlsen, Magnus"]"""), """[P1 "Carlsen, Magnus"]""")
    assertEquals(renamed("""[Black "Nakamura, Hikaru"]"""), """[P2 "Nakamura, Hikaru"]""")
  }
  test("player tag suffixes are carried over") {
    assertEquals(renamed("""[WhiteElo "2839"]"""), """[P1Elo "2839"]""")
    assertEquals(renamed("""[BlackTitle "GM"]"""), """[P2Title "GM"]""")
    assertEquals(renamed("""[WhiteTeam "Norway"]"""), """[P1Team "Norway"]""")
  }
  test("other tags are untouched") {
    assertEquals(renamed("""[Event "Whitechapel Open"]"""), """[Event "Whitechapel Open"]""")
    assertEquals(renamed("""[Annotator "Black, Roger"]"""), """[Annotator "Black, Roger"]""")
  }
  test("a player named White is not renamed") {
    assertEquals(renamed("""[Black "White, James"]"""), """[P2 "White, James"]""")
  }
  test("values containing regex replacement characters survive") {
    assertEquals(renamed("""[White "A $1 \ name"]"""), """[P1 "A $1 \ name"]""")
  }
  test("every player tag in a full PGN is renamed") {
    val out = renamed(pgn(""))
    assert(out.contains("""[P1 "Carlsen, Magnus"]"""))
    assert(out.contains("""[P2 "Nakamura, Hikaru"]"""))
    assert(!out.contains("[White "))
    assert(!out.contains("[Black "))
  }
}
