package lila.security

import org.joda.time.Instant

class FloodTest extends munit.FunSuite {

  import Flood.*

  private def isDup = duplicateMessage

  private def m(s: String) = Message(s, Instant.now)

  private val str = "Implementation uses dynamic programming (Wagner–Fischer algorithm)"
  private val msg = m(str)

  test("same") {
    assert(!isDup(msg, Nil), "no messages")
    assert(!isDup(msg, List(m("foo"))), "different message")
    assert(isDup(msg, List(msg)), "same message")
    assert(isDup(msg, List(m("foo"), msg)), "same message after different")
    assert(isDup(msg, List(m("foo"), msg, m("bar"))), "same message in middle")
    assert(!isDup(msg, List(m("foo"), m("bar"), msg)), "same message too far back")
  }
  test("levenshtein") {
    assert(isDup(msg, List(m(s"$str!"))), "one char appended")
    assert(isDup(msg, List(m(s"-$str"))), "one char prepended")
    assert(isDup(msg, List(m(s"$str!!"))), "two chars appended")
    assert(isDup(msg, List(m(s"$str!!!!"))), "four chars appended")
    assert(isDup(msg, List(m(str.take(str.length - 1)))), "one char removed")
    assert(!isDup(msg, List(m(str.take(str.length / 2)))), "half removed")
    assert(!isDup(msg, List(m(s"$str$str"))), "doubled")

    assert(isDup(m("hey"), List(m(s"hey!"))), "short with one char appended")
    assert(!isDup(m("hey"), List(m(s"hey!!"))), "short with two chars appended")
  }
}
