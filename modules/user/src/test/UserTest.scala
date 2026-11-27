package lila.user

class UserTest extends munit.FunSuite {

  def canSignup(str: User.ID) =
    User.newUsernamePrefix.pattern.matcher(str).matches &&
      User.newUsernameSuffix.pattern.matcher(str).matches &&
      User.newUsernameChars.pattern.matcher(str).matches &&
      User.newUsernameLetters.pattern.matcher(str).matches

  import User.couldBeUsername

  test("bad prefix: can login") {
    assert(couldBeUsername("000"))
    assert(couldBeUsername("0foo"))
    assert(!couldBeUsername("_foo"))
    assert(!couldBeUsername("-foo"))
  }

  test("bad prefix: cannot signup") {
    assert(!canSignup("000"))
    assert(!canSignup("0foo"))
    assert(!canSignup("_foo"))
    assert(!canSignup("-foo"))
  }

  test("bad suffix") {
    assert(!couldBeUsername("a_"))
    assert(!couldBeUsername("a-"))
  }

  test("too many consecutive non-letter chars") {
    assert(!canSignup("a_-a"))
    assert(!canSignup("_-a"))
    assert(!canSignup("a__a"))
    assert(!canSignup("a_-a"))
    assert(!canSignup("a--a"))
    assert(!canSignup("a--_"))
    assert(couldBeUsername("a--a"))
    assert(canSignup("a-a"))
    assert(canSignup("a_a"))
    assert(canSignup("a333"))
    assert(canSignup("ksean222"))
    assert(canSignup("Ksean222"))
  }

  test("OK things") {
    assert(couldBeUsername("g-foo"))
    assert(couldBeUsername("G_FOo"))
    assert(couldBeUsername("g-foo"))
  }

  test("favourite games: round trip, unknown keys dropped, empty set not stored") {
    import reactivemongo.api.bson.BSONDocument
    val h = User.userBSONHandler
    val u = User(
      id = "fav",
      username = "fav",
      perfs = Perfs.default,
      count = Count.default,
      enabled = true,
      roles = Nil,
      playTime = None,
      createdAt = org.joda.time.DateTime.now,
      seenAt = None,
      kid = false,
      lang = None,
      plan = Plan.empty,
      favVariants = Set("chess960", "abalone")
    )
    val doc = h.writeTry(u).get
    assertEquals(h.readTry(doc).get.favVariants, Set("chess960", "abalone"))
    val stale = (doc -- "favVariants") ++ BSONDocument("favVariants" -> List("chess960", "gone"))
    assertEquals(h.readTry(stale).get.favVariants, Set("chess960"))
    assert(!h.writeTry(u.copy(favVariants = Set.empty)).get.contains("favVariants"))
    assertEquals(h.readTry(doc -- "favVariants").get.favVariants, Set.empty[String])
  }
}
