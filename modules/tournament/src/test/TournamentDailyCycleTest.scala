package lila.tournament

import org.joda.time.DateTime

import strategygames.variant.Variant

import TournamentDailyCycle.*

class TournamentDailyCycleTest extends munit.FunSuite {

  private val start = new DateTime(2026, 3, 2, 0, 0) // a Monday
  private val weeks = 52
  private val days  = (0 until weeks * 7).toList.map(start.plusDays)

  /* The hours a fully stocked week has taken by something other than the daily cycle: a 1h57
   * shield at 12:00 and 18:00 on weekdays, the 12/14/16/18/20 shield and medley chain at the
   * weekend, and the 24h yearly on friday. The scheduler works these out from the plans it has
   * already built; here they are just the busy set the fillers have to work around.
   */
  private def busyHours(day: DateTime): Set[Int] =
    day.getDayOfWeek match {
      case 5     => (0 until 24).toSet
      case 6 | 7 => (12 to 21).toSet
      case _     => Set(12, 13, 18, 19)
    }

  private def fillers(day: DateTime, blocks: List[Slot], busy: Set[Int]): List[Slot] =
    fillerSlots(day, blocks.map(_.hour).toSet ++ busy, blocks.map(_.variant).toSet)

  private def allSlots(day: DateTime): List[Slot] = {
    val blocks = blockSlots(day)
    blocks ::: fillers(day, blocks, busyHours(day))
  }

  private val everyVariant = Group.inBlockOrder.flatMap(_.variants)

  test("groups partition the variants schedulable in tournaments") {
    assertEquals(everyVariant.distinct.size, everyVariant.size)
    assertEquals(everyVariant.toSet, Variant.all.filterNot(_.fromPositionVariant).toSet)
  }

  test("every group entry has a speed") {
    assertEquals(TournamentDailyCycle.allVariantSpeeds.map(_._1).toSet, everyVariant.toSet)
  }

  test("hours match the grid, and no two slots on a day collide") {
    days.foreach { day =>
      val hours = allSlots(day).map(_.hour)
      val expected = day.getDayOfWeek match {
        case 5     => Nil
        case 6 | 7 => List(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 22, 23)
        case _     => List(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 14, 15, 16, 17, 20, 21, 22, 23)
      }
      assertEquals(hours.sorted, expected, s"$day")
      assertEquals(hours.distinct.size, hours.size, s"$day")
    }
  }

  test("blocks run the groups in a fixed order") {
    days.foreach { day =>
      val starts = blockHours(day)
      blockSlots(day).foreach { slot =>
        val offset = slot.hour - starts.filter(_ <= slot.hour).max
        val group  = Group.inBlockOrder(offset)
        assert(group.variants.contains(slot.variant), s"$day ${slot.hour} ${slot.variant.key}")
      }
    }
  }

  test("no variant is scheduled twice a day, except backgammon which has too few variants") {
    days.foreach { day =>
      val repeated = allSlots(day)
        .groupBy(_.variant)
        .collect { case (variant, slots) if slots.sizeIs > 1 => variant }
      assert(
        repeated.forall(Group.Backgammon.variants.contains),
        s"$day repeated ${repeated.map(_.key)}"
      )
    }
  }

  test("fillers never pick backgammon") {
    days.foreach { day =>
      fillers(day, blockSlots(day), busyHours(day)).foreach { slot =>
        assert(!Group.Backgammon.variants.contains(slot.variant), s"$day ${slot.variant.key}")
      }
    }
  }

  test("fillers avoid variants already running that day") {
    days.foreach { day =>
      val blocks   = blockSlots(day)
      val reserved = blocks.map(_.variant).toSet + Variant.wrap(strategygames.chess.variant.Atomic)
      fillerSlots(day, blocks.map(_.hour).toSet ++ busyHours(day), reserved).foreach { slot =>
        assert(!reserved.contains(slot.variant), s"$day ${slot.variant.key}")
      }
    }
  }

  /* The point of driving fillers off a busy set rather than a hardcoded hour list: a day that
   * loses its shields, or a friday that has no yearly, hands those hours to the daily cycle.
   */
  test("fillers take the hours a missing shield or yearly leaves behind") {
    days.foreach { day =>
      val blocks = blockSlots(day)
      val filled = fillers(day, blocks, Set.empty)
      assertEquals(filled.map(_.hour), (0 until 24).toList.filterNot(blocks.map(_.hour).toSet), s"$day")
      val blockVariants = blocks.map(_.variant).toSet
      assertEquals(filled.map(_.variant).distinct.size, filled.size, s"$day")
      assert(filled.forall(s => !blockVariants(s.variant)), s"$day")
    }
  }

  /* Every group takes one slot per block, so a group's variants share the block count
   * evenly. Groups whose cycle rolls over mid day drift a little, because a variant that
   * would repeat within the day is replaced by the next one the group has coming up.
   */
  test("each group's variants get near equal use over a year") {
    val blocksSeen = days.map(d => blockHours(d).size).sum
    Group.inBlockOrder.foreach { group =>
      val counts = days
        .flatMap(blockSlots)
        .filter(s => group.variants.contains(s.variant))
        .groupBy(identity[Slot](_).variant)
        .view
        .mapValues(_.size)
        .toMap
      assertEquals(counts.keySet, group.variants.toSet, group.key)
      val used = group.variants.map(v => counts.getOrElse(v, 0))
      val mean = blocksSeen.toDouble / group.variants.size
      assert(
        used.max - used.min <= mean * 0.1,
        s"${group.key} mean $mean spread ${used.min}..${used.max}"
      )
      assertEquals(used.sum, blocksSeen, group.key)
    }
  }

  test("variants do not stick to a single hour") {
    Group.ChessLike.variants.foreach { variant =>
      val hours = days.flatMap(blockSlots).filter(_.variant == variant).map(_.hour).distinct
      assert(hours.sizeIs > 1, s"${variant.key} only ever runs at $hours")
    }
  }

  /* The scheduler treats a daily cycle slot as taken by whatever already occupies it,
   * whatever the variant, which is what lets a group's variant list be edited without
   * doubling up tournaments that have already gone out. That rests on two arenas in the
   * same slot overlapping, and on neighbouring slots not overlapping.
   */
  test("a slot overlaps itself but never the next one") {
    val day = start
    val built = allSlots(day).map { slot =>
      slot.hour -> Tournament.scheduleAs(
        Schedule(Schedule.Freq.DailyCycle, slot.speed, slot.variant, None, day.plusHours(slot.hour)),
        arenaMinutes
      )
    }
    built.foreach { case (hour, tour) =>
      built.foreach { case (otherHour, other) =>
        assertEquals(tour.overlaps(other), hour == otherHour, s"$hour vs $otherHour")
      }
    }
  }

  test("the schedule is a pure function of the date") {
    days.foreach { day =>
      assertEquals(allSlots(day), allSlots(day.plusMinutes(37)))
    }
  }
}
