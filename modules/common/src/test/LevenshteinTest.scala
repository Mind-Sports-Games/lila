package lila.common.base

import scala.util.Random

object LevenshteinTest:
  def check0(a: String, b: String): Boolean =
    val d = StringUtils.levenshtein(a, b)
    !Levenshtein.isLevenshteinDistanceLessThan(a, b, d) &&
    Levenshtein.isLevenshteinDistanceLessThan(a, b, d + 1)

  def check(a: String, b: String) = check0(a, b) && check0(b, a)

  def rndStr(r: Random, l: Int, sigma: Int): String =
    val sb = new StringBuilder(l)
    for _ <- 0 until l do sb.append((48 + r.nextInt(sigma)).toChar)
    sb.result()

  def rt(r: Random, l1: Int, l2: Int, sigma: Int) =
    val s1 = rndStr(r, l1, sigma)
    val s2 = rndStr(r, l2, sigma)
    check(s1, s2)

  def mt(seed: Int, nt: Int, l: Int, sigma: Int) =
    val r = new Random(seed)
    (0 until nt).forall(i => rt(r, r.nextInt(l + 1), l, sigma))

class LevenshteinTest extends munit.FunSuite:
  import LevenshteinTest.{ check, mt }

  test("Levenshtein - random"):
    assert(mt(1, 1000, 10, 2))
    assert(mt(2, 1000, 10, 3))
    assert(mt(3, 10, 1000, 2))
    assert(mt(4, 10, 1000, 3))

  test("Levenshtein - empty"):
    assert(!Levenshtein.isLevenshteinDistanceLessThan("", "", 0))
    assert(Levenshtein.isLevenshteinDistanceLessThan("", "", 1))
    assert(!Levenshtein.isLevenshteinDistanceLessThan("a", "", 1))
    assert(!Levenshtein.isLevenshteinDistanceLessThan("", "a", 1))
    assert(Levenshtein.isLevenshteinDistanceLessThan("a", "", 2))
    assert(Levenshtein.isLevenshteinDistanceLessThan("", "a", 2))

  test("Levenshtein - hand"):
    assert(check("aba", "a"))
    assert(check("abb", "a"))
    assert(check("aab", "a"))
    assert(check("a", "abbbb"))
    assert(check("a", "bbbba"))
    assert(check("abacabada", "aba"))
    assert(check("abacabada", "abacbada"))
    assert(check("hippo", "elephant"))
    assert(check("some", "none"))
    assert(check("true", "false"))
    assert(check("kitten", "mittens"))
    assert(check("a quick brown fox jump over the lazy dog", "a slow green turtle"))
    assert(check("I'll be back", "not today"))
    assert(Levenshtein.isLevenshteinDistanceLessThan("cab", "abc", 3))
    assert(
      !Levenshtein.isLevenshteinDistanceLessThan(
        "a quick brown fox jump over the lazy dog",
        "a slow green turtle",
        0
      )
    )
    assert(
      !Levenshtein.isLevenshteinDistanceLessThan(
        "a quick brown fox jump over the lazy dog",
        "a slow green turtle",
        1
      )
    )
