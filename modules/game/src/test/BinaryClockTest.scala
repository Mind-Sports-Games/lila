package lila.game

import strategygames.{ ByoyomiClock, Centis, Clock, FischerClock, P1, P2 }
import org.specs2.mutable._
import scala.util.chaining._

import lila.db.ByteArray

class BinaryClockTest extends Specification {

  val _0_                  = "00000000"
  val since                = org.joda.time.DateTime.now.minusHours(1)
  def writeBytes(c: Clock) = BinaryFormat.fischerClock(since) write c
  def readBytes(bytes: ByteArray, berserk: Boolean = false): FischerClock =
    (BinaryFormat.fischerClock(since).read(bytes, berserk, false))(P1)
  def isomorphism(c: Clock): FischerClock = readBytes(writeBytes(c))

  def write(c: Clock): List[String] = writeBytes(c).showBytes.split(',').toList
  def read(bytes: List[String])     = readBytes(ByteArray.parseBytes(bytes))

  "binary Fischer Clock" should {
    val clock  = FischerClock(120, 2)
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
      write(FischerClock(0, 3)) must_== {
        List("00000000", "00000011", "10000000", "00000001", "00101100", "10000000", "00000001", "00101100")
      }
    }
    "read" in {
      "with timer" in {
        read(bits22 ::: List.fill(11)(_0_)) must_== {
          clock
        }
        read(bits22 ::: List("10000000", "00000000", "00000011") ::: List.fill(8)(_0_)) must_== {
          clock.giveTime(P1, Centis(3))
        }
        read(bits22 ::: List("00000000", "00000000", "00000011") ::: List.fill(8)(_0_)) must_== {
          clock.giveTime(P1, Centis(-3))
        }
      }
      "without timer bytes" in {
        read(bits22 ::: List.fill(7)(_0_)) must_== {
          clock
        }
        read(bits22 ::: List("10000000", "00000000", "00000011") ::: List.fill(4)(_0_)) must_== {
          clock.giveTime(P1, Centis(3))
        }
        read(bits22 ::: List("00000000", "00000000", "00000011") ::: List.fill(4)(_0_)) must_== {
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
        isomorphism(c4).timer.get.value must beCloseTo(c4.timer.get.value, 10)

        FischerClock(120, 60) pipe { c =>
          isomorphism(c) must_== c
        }
      }

      "with berserk" in {
        val b1 = clock.goBerserk(P1)
        readBytes(writeBytes(b1), true) must_== b1

        val b2 = clock.giveTime(P1, Centis(15)).goBerserk(P1)
        readBytes(writeBytes(b2), true) must_== b2

        val b3 = FischerClock(60, 2).goBerserk(P1)
        readBytes(writeBytes(b3), true) must_== b3
      }
    }
  }
}
