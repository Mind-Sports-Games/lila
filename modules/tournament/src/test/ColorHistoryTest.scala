package lila.tournament
import org.specs2.mutable.Specification

import strategygames.{ P2, P1 }

object SGPlayerHistoryTest {
  def apply(s: String): SGPlayerHistory = {
    s.foldLeft(SGPlayerHistory(0, 0)) { (acc, c) =>
      c match {
        case 'W' => acc.inc(P1)
        case 'B' => acc.inc(P2)
      }
    }
  }
  def toTuple2(history: SGPlayerHistory): (Int, Int)                = (history.strike, history.balance)
  def unpack(s: String): (Int, Int)                              = toTuple2(apply(s))
  def couldPlay(s1: String, s2: String, maxStreak: Int): Boolean = apply(s1).couldPlay(apply(s2), maxStreak)
  def sameSGPlayers(s1: String, s2: String): Boolean                = apply(s1).sameSGPlayers(apply(s2))
  def firstGetsP1(s1: String, s2: String): Boolean =
    apply(s1).firstGetsP1(apply(s2)) { () =>
      true
    }
}

class SGPlayerHistoryTest extends Specification {
  import SGPlayerHistoryTest.{ apply, couldPlay, firstGetsP1, sameSGPlayers, unpack }
  "arena tournament sgPlayer history" should {
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
    "sameSGPlayers" in {
      sameSGPlayers("WWW", "W") must beTrue
      sameSGPlayers("BBB", "B") must beTrue
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
