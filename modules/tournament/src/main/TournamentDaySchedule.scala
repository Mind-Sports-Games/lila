package lila.tournament

import org.joda.time.{ DateTime, Days, Weeks }
import scala.util.Random

import strategygames.variant.Variant

import Schedule.Speed.*

/* A day's automatic tournament schedule, in two kinds of slot: group cycle and wildcard.
 *
 * We define four hour *blocks* hold four consecutive hourly arenas, one per game
 * group, always in the same group order; these are scheduled as Freq.GroupCycle.
 * Every hour the blocks and the day's shields, medleys and yearly leave empty then
 * takes a random variant as a Freq.Wildcard, so the day fills itself instead of
 * relying on a hardcoded list of spare hours.
 *
 *   Mon-Thu  blocks at 02, 08, 14, 20
 *   Sat/Sun  blocks at 02, 08
 *   Fri      no blocks - the 24h yearly usually owns the day
 *
 * With the usual shields in place that leaves wildcards at 00, 01, 06, 07 on Mon-Thu
 * and 00, 01, 06, 07, 22, 23 at the weekend. A week whose shield is missing, or a
 * friday with no yearly, gets more wildcards rather than an empty stretch.
 *
 * Which variant lands in a slot is a pure function of the date, so the scheduler
 * can be run at any time over any horizon and produce the same plan. Adding a new
 * variant is a single line in the relevant group below: the group's cycle simply
 * gets one longer, and `TournamentScheduler.overlaps` treats an already occupied
 * hourly slot as taken regardless of variant, so no duplicate is ever created for a
 * tournament that has already gone out.
 */
object TournamentDaySchedule {

  sealed abstract class Group(val key: String, val variantSpeeds: List[(Variant, Schedule.Speed)]) {
    val variants: List[Variant] = variantSpeeds.map(_._1)
  }

  object Group {

    case object ChessLike
        extends Group(
          "chessLike",
          List(
            Variant.wrap(strategygames.chess.variant.Standard)      -> Blitz32,
            Variant.wrap(strategygames.chess.variant.Chess960)      -> Blitz32,
            Variant.wrap(strategygames.chess.variant.Crazyhouse)    -> Blitz32,
            Variant.wrap(strategygames.chess.variant.KingOfTheHill) -> Blitz32,
            Variant.wrap(strategygames.chess.variant.ThreeCheck)    -> Blitz32,
            Variant.wrap(strategygames.chess.variant.FiveCheck)     -> Blitz32,
            Variant.wrap(strategygames.chess.variant.Antichess)     -> Blitz32,
            Variant.wrap(strategygames.chess.variant.Atomic)        -> Blitz32,
            Variant.wrap(strategygames.chess.variant.Horde)         -> Blitz53,
            Variant.wrap(strategygames.chess.variant.RacingKings)   -> Blitz32,
            Variant.wrap(strategygames.chess.variant.NoCastling)    -> Blitz32,
            Variant.wrap(strategygames.chess.variant.Monster)       -> Blitz32,
            Variant.wrap(strategygames.fairysf.variant.Shogi)       -> Byoyomi510,
            Variant.wrap(strategygames.fairysf.variant.MiniShogi)   -> Byoyomi35,
            Variant.wrap(strategygames.fairysf.variant.Xiangqi)     -> Blitz53,
            Variant.wrap(strategygames.fairysf.variant.MiniXiangqi) -> Blitz32
          )
        )

    case object Draughts
        extends Group(
          "draughts",
          List(
            Variant.wrap(strategygames.draughts.variant.Standard)     -> Blitz53,
            Variant.wrap(strategygames.draughts.variant.Frisian)      -> Blitz53,
            Variant.wrap(strategygames.draughts.variant.Frysk)        -> Blitz21,
            Variant.wrap(strategygames.draughts.variant.Antidraughts) -> Blitz53,
            Variant.wrap(strategygames.draughts.variant.Breakthrough) -> Blitz53,
            Variant.wrap(strategygames.draughts.variant.Russian)      -> Blitz32,
            Variant.wrap(strategygames.draughts.variant.Brazilian)    -> Blitz32,
            Variant.wrap(strategygames.draughts.variant.Pool)         -> Blitz32,
            Variant.wrap(strategygames.draughts.variant.Portuguese)   -> Blitz32,
            Variant.wrap(strategygames.draughts.variant.English)      -> Blitz32,
            Variant.wrap(strategygames.dameo.variant.Dameo)           -> Blitz53
          )
        )

    case object Other
        extends Group(
          "other",
          List(
            Variant.wrap(strategygames.chess.variant.LinesOfAction)            -> Blitz32,
            Variant.wrap(strategygames.chess.variant.ScrambledEggs)            -> Blitz32,
            Variant.wrap(strategygames.fairysf.variant.Flipello)               -> Blitz,
            Variant.wrap(strategygames.fairysf.variant.Flipello10)             -> Rapid8,
            Variant.wrap(strategygames.fairysf.variant.AntiFlipello)           -> Blitz,
            Variant.wrap(strategygames.fairysf.variant.OctagonFlipello)        -> Rapid8,
            Variant.wrap(strategygames.fairysf.variant.Amazons)                -> Blitz35,
            Variant.wrap(strategygames.fairysf.variant.BreakthroughTroyka)     -> Blitz32,
            Variant.wrap(strategygames.fairysf.variant.MiniBreakthroughTroyka) -> Blitz21,
            Variant.wrap(strategygames.samurai.variant.Oware)                  -> Blitz32,
            Variant.wrap(strategygames.togyzkumalak.variant.Togyzkumalak)      -> Blitz52,
            Variant.wrap(strategygames.togyzkumalak.variant.Bestemshe)         -> Blitz32,
            Variant.wrap(strategygames.go.variant.Go9x9)                       -> Byoyomi210x5,
            Variant.wrap(strategygames.go.variant.Go13x13)                     -> Byoyomi310x5,
            Variant.wrap(strategygames.go.variant.Go19x19)                     -> Byoyomi510x5,
            Variant.wrap(strategygames.abalone.variant.Abalone)                -> Delay62,
            Variant.wrap(strategygames.abalone.variant.GrandAbalone)           -> Delay66
          )
        )

    case object Backgammon
        extends Group(
          "backgammon",
          List(
            Variant.wrap(strategygames.backgammon.variant.Backgammon) -> Delay1510,
            Variant.wrap(strategygames.backgammon.variant.Nackgammon) -> Delay210,
            Variant.wrap(strategygames.backgammon.variant.Hyper)      -> Delay110
          )
        )

    // the order groups take the hours within a block, first hour first
    val inBlockOrder: List[Group] = List(ChessLike, Draughts, Other, Backgammon)
  }

  val allVariantSpeeds: List[(Variant, Schedule.Speed)] = Group.inBlockOrder.flatMap(_.variantSpeeds)

  private val speedByVariantKey: Map[String, Schedule.Speed] =
    allVariantSpeeds.map { case (variant, speed) => variant.key -> speed }.toMap

  private val defaultSpeed: Schedule.Speed = Blitz32

  def speedFor(variant: Variant): Schedule.Speed =
    speedByVariantKey.getOrElse(variant.key, defaultSpeed)

  val arenaMinutes = 57

  private val friday = 5

  private val weekdayBlockHours = List(2, 8, 14, 20)
  private val weekendBlockHours = List(2, 8)

  private def isWeekend(day: DateTime) = day.getDayOfWeek > friday

  def blockHours(day: DateTime): List[Int] =
    if (day.getDayOfWeek == friday) Nil
    else if (isWeekend(day)) weekendBlockHours
    else weekdayBlockHours

  /* The block counter is absolute: blocks elapsed since a fixed Monday. Every group
   * takes exactly one slot in every block, so this doubles as each group's own
   * occurrence counter.
   */
  private val epochMonday   = new DateTime(2026, 1, 5, 0, 0)
  private val blocksPerWeek = weekdayBlockHours.size * 4 + weekendBlockHours.size * 2

  private val blocksBeforeDayOfWeek = Map(1 -> 0, 2 -> 4, 3 -> 8, 4 -> 12, 5 -> 16, 6 -> 16, 7 -> 18)

  private def blockIndex(day: DateTime, blockOfDay: Int): Int =
    Weeks.weeksBetween(epochMonday, day.withDayOfWeek(1).withTimeAtStartOfDay).getWeeks * blocksPerWeek +
      blocksBeforeDayOfWeek.getOrElse(day.getDayOfWeek, 0) + blockOfDay

  private def dayIndex(day: DateTime): Int =
    Days.daysBetween(epochMonday, day.withTimeAtStartOfDay).getDays

  /* Every variant in a group plays exactly once per cycle, and the order is reshuffled
   * each cycle. A plain `n % size` would lock variants to fixed hours: the group sizes
   * share factors with the 20 blocks in a week, so e.g. the first ChessLike variant
   * would land on 02:00 forever.
   */
  private def orderFor(group: Group, cycle: Int): List[Variant] =
    new Random(group.key.hashCode.toLong * 1000003L + cycle).shuffle(group.variants)

  private def variantAt(group: Group, block: Int): Variant = {
    val size = group.variants.size
    orderFor(group, Math.floorDiv(block, size))(Math.floorMod(block, size))
  }

  case class Slot(hour: Int, variant: Variant) {
    def speed = speedFor(variant)
  }

  /* Slots for the four hour blocks. A group whose cycle rolls over mid day can offer
   * the same variant twice; where the group has a spare variant we take the next
   * unused one instead. Backgammon has only three variants for four weekday blocks,
   * so it necessarily repeats.
   */
  def blockSlots(day: DateTime): List[Slot] =
    blockHours(day).zipWithIndex
      .flatMap { case (blockHour, blockOfDay) =>
        val block = blockIndex(day, blockOfDay)
        Group.inBlockOrder.zipWithIndex.map { case (group, offset) =>
          (blockHour + offset, group, block)
        }
      }
      .sortBy(_._1)
      .foldLeft(List.empty[Slot]) { case (acc, (hour, group, block)) =>
        Slot(hour, nextUnused(group, block, acc.map(_.variant).toSet)) :: acc
      }
      .reverse

  private def nextUnused(group: Group, block: Int, used: Set[Variant]): Variant =
    LazyList
      .range(0, group.variants.size)
      .map(ahead => variantAt(group, block + ahead))
      .find(v => !used(v))
      .getOrElse(variantAt(group, block))

  /* A wildcard goes in every hour of the day that `busyHours` leaves free - the blocks above
   * plus whatever the shields, medleys and yearly are running through. Wildcards avoid
   * backgammon, and avoid anything else on that day, which is stronger than only checking
   * neighbouring slots since nothing repeats anywhere in the day. The calendar day is the
   * unit, so midnight is a seam: a day's last wildcard can meet the next day's 00:00 one, at
   * roughly one repeat every eight months. That is left alone.
   */
  def wildcardSlots(day: DateTime, busyHours: Set[Int], alreadyUsed: Set[Variant]): List[Slot] = {
    val pool = allVariantSpeeds.map(_._1).filterNot(Group.Backgammon.variants.contains)
    (0 until 24).toList
      .filterNot(busyHours.contains)
      .foldLeft(List.empty[Slot]) { case (acc, hour) =>
        val taken     = alreadyUsed ++ acc.map(_.variant)
        val available = pool.filterNot(taken.contains)
        new Random(dayIndex(day).toLong * 100L + hour)
          .shuffle(if (available.isEmpty) pool else available)
          .headOption
          .fold(acc)(Slot(hour, _) :: acc)
      }
      .reverse
  }
}
