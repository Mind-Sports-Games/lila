package lila.common

class HeapsortTest extends munit.FunSuite:
  import lila.common.Heapsort.implicits._

  test("Heapsort - empty collection"):
    assertEquals(List.empty[Int].topN(10), List.empty[Int])

  test("Heapsort - select more elements than collection has should return sorted collection"):
    assertEquals(List.range(0, 10).topN(30), List.range(9, -1, -1))

  test("Heapsort - hand"):
    assertEquals(List.range(0, 10).topN(3), List(9, 8, 7))
    assertEquals(List.range(0, 10).topN(0), List())
    assertEquals(List(5, 3, 1, 4, 2).topN(2), List(5, 4))
    assertEquals(List(5, 3, 1, 4, 2).botN(2), List(1, 2))

  test("Heapsort - Vector"):
    assertEquals(Heapsort.topN(Vector(5, 3, 1, 4, 2), 2, Ordering.Int), Vector(5, 4))
    assertEquals(Heapsort.topN(Vector(5, 3, 1, 4, 2), 10, Ordering.Int), Vector(5, 4, 3, 2, 1))
