package lila.tournament

import org.joda.time.DateTime

import strategygames.variant.Variant

import Schedule.Freq.*
import Schedule.Speed.*

class TournamentPruneConflictsTest extends munit.FunSuite {

  private val friday = new DateTime(2026, 3, 6, 0, 0)
  private val monday = new DateTime(2026, 3, 2, 0, 0)

  private val chess    = Variant.wrap(strategygames.chess.variant.Standard)
  private val atomic   = Variant.wrap(strategygames.chess.variant.Atomic)
  private val draughts = Variant.wrap(strategygames.draughts.variant.Standard)

  private def tour(
      freq: Schedule.Freq,
      variant: Variant,
      at: DateTime,
      minutes: Int,
      speed: Schedule.Speed = Blitz32
  ) = Tournament.scheduleAs(Schedule(freq, speed, variant, None, at), minutes)

  private def groupCycle(variant: Variant, at: DateTime, speed: Schedule.Speed = Blitz32) =
    tour(GroupCycle, variant, at, TournamentDaySchedule.arenaMinutes, speed)

  private def wildcard(variant: Variant, at: DateTime, speed: Schedule.Speed = Blitz32) =
    tour(Wildcard, variant, at, TournamentDaySchedule.arenaMinutes, speed)

  private def yearly(variant: Variant, at: DateTime, speed: Schedule.Speed = Blitz32) =
    tour(Yearly, variant, at, 60 * 24, speed)

  private def kept(scheds: List[Tournament], planned: List[Tournament]) =
    TournamentScheduler.pruneConflicts(scheds, planned).size

  test("a group cycle slot is claimed whatever the variant") {
    val existing = groupCycle(chess, monday.plusHours(2))
    assertEquals(kept(List(existing), List(groupCycle(atomic, monday.plusHours(2)))), 0)
  }

  test("editing a game group does not double up the slots already created") {
    val hours    = TournamentDaySchedule.blockSlots(monday).map(_.hour)
    val existing = hours.map(h => groupCycle(chess, monday.plusHours(h)))
    val replanned = hours.map(h => groupCycle(draughts, monday.plusHours(h)))
    assertEquals(kept(existing, replanned), 0)
  }

  test("neighbouring group cycle slots do not block each other") {
    val existing = groupCycle(chess, monday.plusHours(2))
    assertEquals(kept(List(existing), List(groupCycle(atomic, monday.plusHours(3)))), 1)
  }

  test("a yearly is not recreated when its speed is edited") {
    val existing = yearly(draughts, friday, Blitz32)
    assertEquals(kept(List(existing), List(yearly(draughts, friday, Blitz53))), 0)
  }

  test("a yearly is not recreated when its variant is edited") {
    val existing = yearly(draughts, friday)
    assertEquals(kept(List(existing), List(yearly(chess, friday))), 0)
  }

  test("yearlies on different days both survive") {
    val existing = yearly(draughts, friday)
    assertEquals(kept(List(existing), List(yearly(chess, friday.plusDays(7)))), 1)
  }

  test("a shield does not block the arenas around it") {
    val shield = tour(Shield, chess, monday.plusHours(12), TournamentShield.arenaMinutes)
    assertEquals(kept(List(shield), List(groupCycle(chess, monday.plusHours(11)))), 1)
    assertEquals(kept(List(shield), List(groupCycle(chess, monday.plusHours(14)))), 1)
  }

  test("a friday yearly does not block the saturday wildcard that follows it") {
    val existing = yearly(chess, friday)
    assertEquals(kept(List(existing), List(wildcard(atomic, friday.plusDays(1)))), 1)
  }

  /* A block and a wildcard are the same hourly slot under two freqs, so the slot has to stay
   * claimed across them - otherwise a wildcard would be planned on top of a block whenever the
   * variants happened to differ.
   */
  test("a wildcard does not double up on a group cycle slot") {
    val existing = groupCycle(chess, monday.plusHours(2))
    assertEquals(kept(List(existing), List(wildcard(atomic, monday.plusHours(2)))), 0)
    assertEquals(kept(List(wildcard(atomic, monday.plusHours(2))), List(existing)), 0)
  }

  test("a wildcard still survives in the hour beside a group cycle slot") {
    val existing = groupCycle(chess, monday.plusHours(2))
    assertEquals(kept(List(existing), List(wildcard(atomic, monday.plusHours(3)))), 1)
  }
}
