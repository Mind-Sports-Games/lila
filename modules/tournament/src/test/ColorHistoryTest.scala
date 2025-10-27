package lila.tournament
import org.specs2.mutable.Specification

import strategygames.{ P1, P2 }

object PlayerIndexHistoryTest {
  def apply(s: String): PlayerIndexHistory = {
    s.foldLeft(PlayerIndexHistory(0, 0)) { (acc, c) =>
      c match {
        case 'W' => acc.inc(P1)
        case 'B' => acc.inc(P2)
      }
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

class PlayerIndexHistoryTest extends Specification {
  import PlayerIndexHistoryTest.{ apply, couldPlay, firstGetsP1, samePlayerIndexs, unpack }
  "arena tournament playerIndex history" should {
    "hand tests" in {
      unpack("WWW") must be equalTo ((3, 3))
      unpack("WWWB") must be equalTo ((-1, 2))
      unpack("BBB") must be equalTo ((-3, -3))
      unpack("BBBW") must be equalTo ((1, -2))
      unpack("WWWBBB") must be equalTo ((-3, 0))
    }
    "couldPlay" in {
      couldPlay("WWW", "WWW", 3) must beFalse
      couldPlay("BBB", "BBB", 3) must beFalse
      couldPlay("BB", "BB", 3) must beTrue
    }
    "samePlayerIndexs" in {
      samePlayerIndexs("WWW", "W") must beTrue
      samePlayerIndexs("BBB", "B") must beTrue
    }
    "firstGetsP1" in {
      firstGetsP1("WWW", "WW") must beFalse
      firstGetsP1("WW", "WWW") must beTrue
      firstGetsP1("BB", "B") must beTrue
      firstGetsP1("B", "BB") must beFalse
      firstGetsP1("WW", "BWW") must beFalse
      firstGetsP1("BB", "WBB") must beTrue
    }
    "equals" in {
      apply("") must be equalTo apply("")
      apply("WBW") must be equalTo apply("W")
    }
  }
}
