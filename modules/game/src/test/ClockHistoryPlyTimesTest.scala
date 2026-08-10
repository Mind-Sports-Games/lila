package lila.game

import strategygames.{ Centis, P1, P2, Player as PlayerIndex, Status }

class ClockHistoryPlyTimesTest extends munit.FunSuite {

  private val grace = Centis(200) // 2s increment

  private def plyTimes(
      clocks: Vector[Centis],
      actionsPerTurn: Vector[Int],
      finished: Boolean = false,
      turnPlayerIndex: PlayerIndex = P1
  ) =
    FischerClockHistory(p1 = clocks).plyTimes(
      playerIndex = P1,
      turnPlayerIndex = turnPlayerIndex,
      playedPlies = clocks.size,
      startedAtTurn = 0,
      finished = finished,
      status = if (finished) Status.Mate else Status.Started,
      grace = grace,
      byo = Centis(0),
      actionsPerTurn = actionsPerTurn
    )

  test("single action per turn: increment is added back to every action") {
    val clocks = Vector(Centis(17700), Centis(17400), Centis(17100))
    assertEquals(plyTimes(clocks, Vector(1, 1, 1)), List(Centis(0), Centis(500), Centis(500)))
  }

  test("the game-ending action gets no increment") {
    val clocks = Vector(Centis(17700), Centis(17400), Centis(17100))
    assertEquals(
      plyTimes(clocks, Vector(1, 1, 1), finished = true, turnPlayerIndex = P2),
      List(Centis(0), Centis(500), Centis(300))
    )
  }

  test("multiaction: increment is only added back on the action that ends the turn") {
    val clocks = Vector(
      Centis(17950), // roll (0.5s)
      Centis(17850), // move (1s)
      Centis(17750), // move (1s)
      Centis(17930)  // endturn (0.2s spent, +2s increment)
    )
    assertEquals(
      plyTimes(clocks, Vector(4)),
      List(Centis(0), Centis(100), Centis(100), Centis(20))
    )
    assertEquals(
      plyTimes(clocks, Vector.empty),
      List(Centis(0), Centis(300), Centis(300), Centis(20))
    )
  }

  test("multiaction: turn boundaries are tracked across turns") {
    val clocks = Vector(
      Centis(17900), // t1 roll (1s)
      Centis(18000), // t1 endturn (1s spent, +2s)
      Centis(17900), // t2 roll (1s)
      Centis(17800), // t2 move (1s)
      Centis(17900)  // t2 endturn (1s spent, +2s)
    )
    assertEquals(
      plyTimes(clocks, Vector(2, 3)),
      List(Centis(0), Centis(100), Centis(100), Centis(100), Centis(100))
    )
  }
}
