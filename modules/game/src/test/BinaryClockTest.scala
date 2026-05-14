package lila.game

import strategygames.{ ByoyomiClock, Centis, Clock, ClockBase, P1, P2 }
// import scala.util.chaining.*

import lila.db.ByteArray

class BinaryClockTest extends munit.FunSuite {

  val _0_   = "00000000"
  val since = org.joda.time.DateTime.now.minusHours(1)
  def writeBytes(c: ClockBase) = c match {
    case fc: Clock        => BinaryFormat.fischerClock(since).write(fc)
    case bc: ByoyomiClock => BinaryFormat.byoyomiClock(since).write(bc)
  }
  def readBytesFischer(bytes: ByteArray, berserk: Boolean = false): Clock =
    (BinaryFormat.fischerClock(since).read(Clock.Config.apply, bytes, berserk, false))(P1)
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

  val fischerClock = Clock(120, 2)
  val bits22       = List("00000010", "00000010")

  test("Fischer - write") {
    assertEquals(write(fischerClock), bits22 ::: List.fill(6)(_0_))
    assertEquals(
      write(fischerClock.giveTime(P1, Centis(3))),
      bits22 ::: List("10000000", "00000000", "00000011") ::: List.fill(3)(_0_)
    )
    assertEquals(
      write(fischerClock.giveTime(P1, Centis(-3))),
      bits22 ::: List("00000000", "00000000", "00000011") ::: List.fill(3)(_0_)
    )
    assertEquals(
      write(Clock(0, 3)),
      List("00000000", "00000011", "10000000", "00000001", "00101100", "10000000", "00000001", "00101100")
    )
  }

  test("Fischer - read with timestamp") {
    assertEquals(readFischer(bits22 ::: List.fill(11)(_0_)), fischerClock)
    assertEquals(
      readFischer(bits22 ::: List("10000000", "00000000", "00000011") ::: List.fill(8)(_0_)),
      fischerClock.giveTime(P1, Centis(3))
    )
    assertEquals(
      readFischer(bits22 ::: List("00000000", "00000000", "00000011") ::: List.fill(8)(_0_)),
      fischerClock.giveTime(P1, Centis(-3))
    )
  }

  test("Fischer - read without timestamp bytes") {
    assertEquals(readFischer(bits22 ::: List.fill(7)(_0_)), fischerClock)
    assertEquals(
      readFischer(bits22 ::: List("10000000", "00000000", "00000011") ::: List.fill(4)(_0_)),
      fischerClock.giveTime(P1, Centis(3))
    )
    assertEquals(
      readFischer(bits22 ::: List("00000000", "00000000", "00000011") ::: List.fill(4)(_0_)),
      fischerClock.giveTime(P1, Centis(-3))
    )
  }

  test("Fischer - isomorphism without berserk") {
    assertEquals(isomorphism(fischerClock), fischerClock)

    val c2 = fischerClock.giveTime(P1, Centis.ofSeconds(15))
    assertEquals(isomorphism(c2), c2)

    val c3 = fischerClock.giveTime(P2, Centis.ofSeconds(5))
    assertEquals(isomorphism(c3), c3)

    val c4 = fischerClock.start
    assert(Math.abs(isomorphism(c4).timestamp.get.value - c4.timestamp.get.value) <= 10L)

    Clock(120, 60).pipe { c =>
      assertEquals(isomorphism(c), c)
    }
  }

  test("Fischer - isomorphism with berserk") {
    val b1 = fischerClock.goBerserk(P1)
    assertEquals(readBytesFischer(writeBytes(b1), true), b1)

    val b2 = fischerClock.giveTime(P1, Centis(15)).goBerserk(P1)
    assertEquals(readBytesFischer(writeBytes(b2), true), b2)

    val b3 = Clock(60, 2).goBerserk(P1)
    assertEquals(readBytesFischer(writeBytes(b3), true), b3)
  }

  val byoyomiClock = ByoyomiClock(120, 2, 10, 1)
  val bitsA1       = List("00001010", "00000001")

  test("Byoyomi - write") {
    assertEquals(write(byoyomiClock), bits22 ::: List.fill(6)(_0_) ::: bitsA1)
    assertEquals(
      write(byoyomiClock.giveTime(P1, Centis(3))),
      bits22 ::: List("10000000", "00000000", "00000011") ::: List.fill(3)(_0_) ::: bitsA1
    )
    assertEquals(
      write(byoyomiClock.giveTime(P1, Centis(-3))),
      bits22 ::: List("00000000", "00000000", "00000011") ::: List.fill(3)(_0_) ::: bitsA1
    )
    assertEquals(
      write(ByoyomiClock(0, 3, 5, 2)),
      List("00000000", "00000011", "10000000", "00000001", "11110100", "10000000", "00000001", "11110100",
        "00000101", "00000010")
    )
    assertEquals(
      write(ByoyomiClock(0, 5, 0, 0)),
      List("00000000", "00000101", "10000000", "00000001", "11110100", "10000000", "00000001", "11110100",
        _0_, _0_)
    )
  }

  test("Byoyomi - read with timestamp") {
    assertEquals(readByoyomi(bits22 ::: List.fill(10)(_0_) ::: bitsA1), byoyomiClock)
    assertEquals(
      readByoyomi(bits22 ::: List("10000000", "00000000", "00000011") ::: List.fill(7)(_0_) ::: bitsA1),
      byoyomiClock.giveTime(P1, Centis(3))
    )
    assertEquals(
      readByoyomi(bits22 ::: List("00000000", "00000000", "00000011") ::: List.fill(7)(_0_) ::: bitsA1),
      byoyomiClock.giveTime(P1, Centis(-3))
    )
  }

  test("Byoyomi - read without timestamp bytes") {
    assertEquals(readByoyomi(bits22 ::: List.fill(6)(_0_) ::: bitsA1), byoyomiClock)
    assertEquals(
      readByoyomi(bits22 ::: List("10000000", "00000000", "00000011") ::: List.fill(3)(_0_) ::: bitsA1),
      byoyomiClock.giveTime(P1, Centis(3))
    )
    assertEquals(
      readByoyomi(bits22 ::: List("00000000", "00000000", "00000011") ::: List.fill(3)(_0_) ::: bitsA1),
      byoyomiClock.giveTime(P1, Centis(-3))
    )
  }

  test("Byoyomi - isomorphism without berserk") {
    assertEquals(isomorphism(byoyomiClock), byoyomiClock)

    val c2 = byoyomiClock.giveTime(P1, Centis.ofSeconds(15))
    assertEquals(isomorphism(c2), c2)

    val c3 = byoyomiClock.giveTime(P2, Centis.ofSeconds(5))
    assertEquals(isomorphism(c3), c3)

    val c4 = byoyomiClock.start
    assert(Math.abs(isomorphism(c4).timestamp.get.value - c4.timestamp.get.value) <= 10L)

    ByoyomiClock(120, 60, 5, 2).pipe { c =>
      assertEquals(isomorphism(c), c)
    }

    val c5 = ByoyomiClock(15, 0, 10, 1).giveTime(P1, Centis.ofSeconds(-20)).start
    assert(Math.abs(isomorphism(c5).timestamp.get.value - c5.timestamp.get.value) <= 10L)
    isomorphism(c5).currentClockFor(P1).pipe { cc =>
      assertEquals(cc.periods, 1)
      assert(Math.abs(cc.time.centis - 500) <= 10)
    }
  }

  test("Byoyomi - isomorphism with berserk") {
    val b1 = byoyomiClock.goBerserk(P1)
    assertEquals(readBytesByoyomi(writeBytes(b1), true), b1)

    val b2 = byoyomiClock.giveTime(P1, Centis(15)).goBerserk(P1)
    assertEquals(readBytesByoyomi(writeBytes(b2), true), b2)

    val b3 = ByoyomiClock(60, 2, 5, 2).goBerserk(P1)
    assertEquals(readBytesByoyomi(writeBytes(b3), true), b3)
  }
}
