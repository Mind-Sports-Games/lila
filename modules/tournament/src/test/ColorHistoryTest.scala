package lila.tournament

import strategygames.{ P1, P2 }

object PlayerIndexHistoryTest {
  def apply(s: String): PlayerIndexHistory =
    s.foldLeft(PlayerIndexHistory(0, 0)) { (acc, c) =>
      c match {
        case 'W' => acc.inc(P1)
        case 'B' => acc.inc(P2)
      }
    }
  def toTuple2(history: PlayerIndexHistory): (Int, Int)          = (history.strike, history.balance)
  def unpack(s: String): (Int, Int)                              = toTuple2(apply(s))
  def couldPlay(s1: String, s2: String, maxStreak: Int): Boolean = apply(s1).couldPlay(apply(s2), maxStreak)
  def samePlayerIndexs(s1: String, s2: String): Boolean          = apply(s1).samePlayerIndexs(apply(s2))
  def firstGetsP1(s1: String, s2: String): Boolean =
    apply(s1).firstGetsP1(apply(s2)) { () =>
      true
    }
}

class ColorHistoryTest extends munit.FunSuite {
  import PlayerIndexHistoryTest.{ apply, couldPlay, firstGetsP1, samePlayerIndexs, unpack }

  test("hand tests") {
    assertEquals(unpack("WWW"), (3, 3))
    assertEquals(unpack("WWWB"), (-1, 2))
    assertEquals(unpack("BBB"), (-3, -3))
    assertEquals(unpack("BBBW"), (1, -2))
    assertEquals(unpack("WWWBBB"), (-3, 0))
  }

  test("couldPlay") {
    assert(!couldPlay("WWW", "WWW", 3))
    assert(!couldPlay("BBB", "BBB", 3))
    assert(couldPlay("BB", "BB", 3))
  }

  test("samePlayerIndexs") {
    assert(samePlayerIndexs("WWW", "W"))
    assert(samePlayerIndexs("BBB", "B"))
  }

  test("firstGetsP1") {
    assert(!firstGetsP1("WWW", "WW"))
    assert(firstGetsP1("WW", "WWW"))
    assert(firstGetsP1("BB", "B"))
    assert(!firstGetsP1("B", "BB"))
    assert(!firstGetsP1("WW", "BWW"))
    assert(firstGetsP1("BB", "WBB"))
  }

  test("equals") {
    assertEquals(apply(""), apply(""))
    assertEquals(apply("WBW"), apply("W"))
  }
}
