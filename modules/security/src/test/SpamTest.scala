package lila.security

import lila.common.Strings

class SpamTest extends munit.FunSuite {

  val spam   = new Spam(() => Strings(Nil))
  val foobar = """foo bar"""
  val _c2    = """https://chess24.com?ref=spammyboy"""

  test("detect") {
    assert(!spam.detect(foobar), "foobar is not spam")
    assert(spam.detect(_c2), "referral link is spam")
  }
  test("replace") {
    assertEquals(spam.replace(foobar), foobar)
    assertEquals(spam.replace(_c2), """https://chess24.com""")
  }
}
