package lila.game

import strategygames.{ ByoyomiClock, Centis, Clock, ClockBase, P1, P2 }
import org.specs2.mutable._
import scala.util.chaining._

import lila.db.ByteArray
class BinaryClockTest extends Specification {

  val _0_   = "00000000"
  val since = org.joda.time.DateTime.now.minusHours(1)
  def writeBytes(c: ClockBase) = c match {
    case fc: Clock        => BinaryFormat.fischerClock(since).write(fc)
    case bc: ByoyomiClock => BinaryFormat.byoyomiClock(since).write(bc)
  }
  def readBytesFischer(bytes: ByteArray, berserk: Boolean = false): Clock =
    (BinaryFormat.fischerClock(since).read(Clock.Config, bytes, berserk, false))(P1)
  def readBytesByoyomi(
      bytes: ByteArray,
      berserk: Boolean = false,
      periodEntries: PeriodEntries = PeriodEntries.default
  ): ByoyomiClock =
    (BinaryFormat.byoyomiClock(since).read(bytes, periodEntries, berserk, false))(P1)
  def isomorphism(c: ClockBase): ClockBase = c match {
    case _: Clock        => readBytesFischer(writeBytes(c))
    case _: ByoyomiClock => readBytesByoyomi(writeBytes(c))
  }

  def write(c: ClockBase): List[String] = writeBytes(c).showBytes.split(',').toList
  def readFischer(bytes: List[String])  = readBytesFischer(ByteArray.parseBytes(bytes))
  def readByoyomi(bytes: List[String])  = readBytesByoyomi(ByteArray.parseBytes(bytes))

  "binary Fischer ClockBase" should {
    val clock  = Clock(120, 2)
    val bits22 = List("00000010", "00000010")
    "write" in {
      write(clock) must_== {
        bits22 ::: List.fill(6)(_0_)
      }
      write(clock.giveTime(P1, Centis(3))) must_== {
        bits22 ::: List("10000000", "00000000", "00000011") ::: List.fill(3)(_0_)
      }
      write(clock.giveTime(P1, Centis(-3))) must_== {
        bits22 ::: List("00000000", "00000000", "00000011") ::: List.fill(3)(_0_)
      }
      write(Clock(0, 3)) must_== {
        List("00000000", "00000011", "10000000", "00000001", "00101100", "10000000", "00000001", "00101100")
      }
    }
    "read" in {
      "with timestamp" in {
        readFischer(bits22 ::: List.fill(11)(_0_)) must_== {
          clock
        }
        readFischer(bits22 ::: List("10000000", "00000000", "00000011") ::: List.fill(8)(_0_)) must_== {
          clock.giveTime(P1, Centis(3))
        }
        readFischer(bits22 ::: List("00000000", "00000000", "00000011") ::: List.fill(8)(_0_)) must_== {
          clock.giveTime(P1, Centis(-3))
        }
      }
      "without timestamp bytes" in {
        readFischer(bits22 ::: List.fill(7)(_0_)) must_== {
          clock
        }
        readFischer(bits22 ::: List("10000000", "00000000", "00000011") ::: List.fill(4)(_0_)) must_== {
          clock.giveTime(P1, Centis(3))
        }
        readFischer(bits22 ::: List("00000000", "00000000", "00000011") ::: List.fill(4)(_0_)) must_== {
          clock.giveTime(P1, Centis(-3))
        }
      }
    }
    "isomorphism" in {

      "without berserk" in {
        isomorphism(clock) must_== clock

        val c2 = clock.giveTime(P1, Centis.ofSeconds(15))
        isomorphism(c2) must_== c2

        val c3 = clock.giveTime(P2, Centis.ofSeconds(5))
        isomorphism(c3) must_== c3

        val c4 = clock.start
        isomorphism(c4).timestamp.get.value must beCloseTo(c4.timestamp.get.value, 10L)

        Clock(120, 60) pipe { c =>
          isomorphism(c) must_== c
        }
      }

      "with berserk" in {
        val b1 = clock.goBerserk(P1)
        readBytesFischer(writeBytes(b1), true) must_== b1

        val b2 = clock.giveTime(P1, Centis(15)).goBerserk(P1)
        readBytesFischer(writeBytes(b2), true) must_== b2

        val b3 = Clock(60, 2).goBerserk(P1)
        readBytesFischer(writeBytes(b3), true) must_== b3
      }
    }
  }

  "binary Byoyomi ClockBase" should {
    val clock  = ByoyomiClock(120, 2, 10, 1)
    val bits22 = List("00000010", "00000010")
    val bitsA1 = List("00001010", "00000001")
    "write" in {
      write(clock) must_== {
        bits22 ::: List.fill(6)(_0_) ::: bitsA1
      }
      write(clock.giveTime(P1, Centis(3))) must_== {
        bits22 ::: List("10000000", "00000000", "00000011") ::: List.fill(3)(_0_) ::: bitsA1
      }
      write(clock.giveTime(P1, Centis(-3))) must_== {
        bits22 ::: List("00000000", "00000000", "00000011") ::: List.fill(3)(_0_) ::: bitsA1
      }
      write(ByoyomiClock(0, 3, 5, 2)) must_== {
        List(
          "00000000",
          "00000011",
          "10000000",
          "00000001",
          "11110100",
          "10000000",
          "00000001",
          "11110100",
          "00000101",
          "00000010"
        )
      }
      write(ByoyomiClock(0, 5, 0, 0)) must_== {
        List(
          "00000000",
          "00000101",
          "10000000",
          "00000001",
          "11110100",
          "10000000",
          "00000001",
          "11110100",
          _0_,
          _0_
        )
      }
    }
    "read" in {
      "with timestamp" in {
        readByoyomi(bits22 ::: List.fill(10)(_0_) ::: bitsA1) must_== { clock }
        readByoyomi(
          bits22 ::: List("10000000", "00000000", "00000011") ::: List.fill(7)(_0_) ::: bitsA1
        ) must_== {
          clock.giveTime(P1, Centis(3))
        }
        readByoyomi(
          bits22 ::: List("00000000", "00000000", "00000011") ::: List.fill(7)(_0_) ::: bitsA1
        ) must_== {
          clock.giveTime(P1, Centis(-3))
        }
      }
      "without timestamp bytes" in {
        readByoyomi(bits22 ::: List.fill(6)(_0_) ::: bitsA1) must_== {
          clock
        }
        readByoyomi(
          bits22 ::: List("10000000", "00000000", "00000011") ::: List.fill(3)(_0_) ::: bitsA1
        ) must_== {
          clock.giveTime(P1, Centis(3))
        }
        readByoyomi(
          bits22 ::: List("00000000", "00000000", "00000011") ::: List.fill(3)(_0_) ::: bitsA1
        ) must_== {
          clock.giveTime(P1, Centis(-3))
        }
      }
    }
    "isomorphism" in {

      "without berserk" in {
        isomorphism(clock) must_== clock

        val c2 = clock.giveTime(P1, Centis.ofSeconds(15))
        isomorphism(c2) must_== c2

        val c3 = clock.giveTime(P2, Centis.ofSeconds(5))
        isomorphism(c3) must_== c3

        val c4 = clock.start
        isomorphism(c4).timestamp.get.value must beCloseTo(c4.timestamp.get.value, 10L)

        ByoyomiClock(120, 60, 5, 2) pipe { c =>
          isomorphism(c) must_== c
        }

        val c5 = ByoyomiClock(15, 0, 10, 1).giveTime(P1, Centis.ofSeconds(-20)).start
        isomorphism(c5).timestamp.get.value must beCloseTo(c5.timestamp.get.value, 10L)
        isomorphism(c5).currentClockFor(P1) pipe { cc =>
          cc.periods must_== 1
          cc.time.centis must beCloseTo(500, 10)
        }
      }

      "with berserk" in {
        val b1 = clock.goBerserk(P1)
        readBytesByoyomi(writeBytes(b1), true) must_== b1

        val b2 = clock.giveTime(P1, Centis(15)).goBerserk(P1)
        readBytesByoyomi(writeBytes(b2), true) must_== b2

        val b3 = ByoyomiClock(60, 2, 5, 2).goBerserk(P1)
        readBytesByoyomi(writeBytes(b3), true) must_== b3
      }
    }
  }
}
