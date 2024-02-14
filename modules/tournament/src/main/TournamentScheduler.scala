package lila.tournament

import akka.actor._
import strategygames.chess.StartingPosition
import strategygames.variant.Variant
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants._
import scala.util.chaining._
import lila.i18n.VariantKeys

final private class TournamentScheduler(
    api: TournamentApi,
    tournamentRepo: TournamentRepo
) extends Actor {

  import Schedule.Freq._
  import Schedule.Speed._
  import Schedule.Plan

  implicit def ec = context.dispatcher

  /* Month plan:
   * First week: Shield standard tournaments
   * Second week: Yearly tournament
   * Third week: Shield variant tournaments
   * Last week: Monthly tournaments
   */

  // def marathonDates = List(
  // Spring -> Saturday of the weekend after Orthodox Easter Sunday
  // Summer -> first Saturday of August
  // Autumn -> Saturday of weekend before the weekend Halloween falls on (c.f. half-term holidays)
  // Winter -> 28 December, convenient day in the space between Boxing Day and New Year's Day
  // )
  private[tournament] def allWithConflicts(rightNow: DateTime): List[Plan] = {
    val today       = rightNow.withTimeAtStartOfDay
    val tomorrow    = rightNow plusDays 1
    val startOfYear = today.dayOfYear.withMinimumValue

    class OfMonth(fromNow: Int) {
      val firstDay = today.plusMonths(fromNow).dayOfMonth.withMinimumValue
      val lastDay  = firstDay.dayOfMonth.withMaximumValue

      val firstWeek  = firstDay.plusDays(7 - (firstDay.getDayOfWeek - 1) % 7)
      val secondWeek = firstWeek plusDays 7
      val thirdWeek  = secondWeek plusDays 7
      val lastWeek   = lastDay.minusDays((lastDay.getDayOfWeek - 1) % 7)

      val index = firstDay.getMonthOfYear()
    }
    val thisMonth = new OfMonth(0)
    val nextMonth = new OfMonth(1)

    def xMonthWithDay(fromNowMonths: Int)(dayOfMonth: Int) =
      new DateTime().plusMonths(fromNowMonths).withDayOfMonth(dayOfMonth).withTimeAtStartOfDay
    def thisMonthWithDay(dayOfMonth: Int) =
      xMonthWithDay(0)(dayOfMonth)
    def nextMonthWithDay(dayOfMonth: Int) =
      xMonthWithDay(1)(dayOfMonth)

    def nextDayOfWeeks(dayNumber: Int, weekNumber: Int) =
      today.plusDays((dayNumber + 7 * weekNumber - today.getDayOfWeek) % (7 * weekNumber))
    def nextDayOfWeek(number: Int)      = nextDayOfWeeks(number, 1)
    def nextDayOfFortnight(number: Int) = nextDayOfWeeks(number, 2)
    val nextMonday                      = nextDayOfWeek(1)
    val nextTuesday                     = nextDayOfWeek(2)
    val nextWednesday                   = nextDayOfWeek(3)
    val nextThursday                    = nextDayOfWeek(4)
    val nextFriday                      = nextDayOfWeek(5)
    val nextSaturday                    = nextDayOfWeek(6)
    val nextSunday                      = nextDayOfWeek(7)

    def secondWeekOf(month: Int) = {
      val start = orNextYear(startOfYear.withMonthOfYear(month))
      start.plusDays(15 - start.getDayOfWeek)
    }

    def orTomorrow(date: DateTime) = if (date isBefore rightNow) date plusDays 1 else date
    def orNextWeek(date: DateTime) = if (date isBefore rightNow) date plusWeeks 1 else date
    def orNextYear(date: DateTime) = if (date isBefore rightNow) date plusYears 1 else date

    val isHalloween = today.getDayOfMonth == 31 && today.getMonthOfYear == OCTOBER

    def opening(offset: Int) = {
      val positions = StartingPosition.featurable
      positions((today.getDayOfYear + offset) % positions.size)
    }

    val farFuture = today plusMonths 7

    val birthday = new DateTime(2021, 7, 21, 12, 0, 0)

    val fss  = List(nextFriday, nextSaturday, nextSunday)
    val mwfs = List(nextMonday, nextWednesday, nextFriday, nextSunday)
    val tts  = List(nextTuesday, nextThursday, nextSaturday)

    def schedule10(hour: Int, v: Variant)(day: DateTime) =
      at(day, hour) map { date =>
        Schedule(Weekly, Bullet, v, none, date).plan
      }

    def schedule32(hour: Int, v: Variant)(day: DateTime) =
      at(day, hour) map { date =>
        Schedule(Weekly, Blitz32, v, none, date).plan
      }

    def schedule51(hour: Int, v: Variant)(day: DateTime) =
      at(day, hour) map { date =>
        Schedule(Weekly, Blitz51, v, none, date).plan
      }

    def scheduleUnique(hour: Int, speed: Schedule.Speed, variant: Variant, duration: Int)(
        day: DateTime
    ) =
      at(day, hour) map { date =>
        Schedule(Unique, speed, variant, none, date, Some(duration)).plan
      }

    def scheduleMedleyShield(speed: Schedule.Speed, medleyShield: TournamentShield.MedleyShield)(
        day: DateTime
    ) =
      at(day, medleyShield.hour) map { date =>
        Schedule(
          MedleyShield,
          speed,
          medleyShield.eligibleVariants.head,
          none,
          date,
          Some(medleyShield.arenaMinutes),
          Some(medleyShield),
          Condition.All(
            nbRatedGame = none,
            maxRating = none,
            minRating = none,
            titled = none,
            teamMember = medleyShield.teamOwner.some
          )
        ).plan
      }

    def scheduleYearly24hr(variant: Variant, speed: Schedule.Speed)(
        day: DateTime
    ) =
      at(day, 0) map { date =>
        Schedule(
          Yearly,
          speed,
          variant,
          none,
          date,
          Some(60 * 24)
        ).plan {
          _.copy(
            spotlight = Some(
              Spotlight(
                iconFont = variant.perfIcon.toString.some,
                headline = "",
                description = s"A yearly 24hr tournament for ${VariantKeys.variantName(variant)}",
                homepageHours = 24.some
              )
            )
          )
        }
      }

    def scheduleWeekly(speed: Schedule.Speed, variant: Variant)(
        day: DateTime,
        hour: Int
    ) =
      at(day, hour) map { date =>
        Schedule(
          Weekly,
          speed,
          variant,
          none,
          date
        ).plan {
          _.copy(
            spotlight = Some(
              Spotlight(
                iconFont = variant.perfIcon.toString.some,
                headline = "",
                description = s"A weekly tournament for ${VariantKeys.variantName(variant)}",
                homepageHours = 24.some
              )
            )
          )
        }
      }

    //schedule this week
    val thisWeekMedleyShields = TournamentShield.MedleyShield.all
      .map(ms =>
        scheduleMedleyShield(Blitz53, ms)(
          nextDayOfWeek(ms.dayOfWeek)
        )
      )
      .flatten filter { _.schedule.at isAfter rightNow }

    //and schedule two weeks in advance
    val nextWeekMedleyShields = TournamentShield.MedleyShield.all
      .map(ms =>
        scheduleMedleyShield(Blitz53, ms)(
          nextDayOfFortnight(ms.dayOfWeek + 7)
        )
      )
      .flatten filter { _.schedule.at isAfter rightNow }

    //schedule this months shields
    val thisMonthShields = TournamentShield.Category.all
      .map(shield =>
        at(thisMonthWithDay(shield.dayOfMonth), shield.scheduleHour(thisMonth.index)) map { date =>
          Schedule(Shield, shield.speed, shield.variant, none, date) plan {
            _.copy(
              name = s"${VariantKeys.variantName(shield.variant)} Shield",
              spotlight = Some(
                TournamentShield.spotlight(
                  VariantKeys.variantName(shield.variant),
                  shield.variant.perfIcon
                )
              )
            )
          }
        }
      )
      .flatten filter { _.schedule.at isAfter rightNow }

    //and schedule next month
    val nextMonthShields = TournamentShield.Category.all
      .map(shield =>
        at(nextMonthWithDay(shield.dayOfMonth), shield.scheduleHour(nextMonth.index)) map { date =>
          Schedule(Shield, shield.speed, shield.variant, none, date) plan {
            _.copy(
              name = s"${VariantKeys.variantName(shield.variant)} Shield",
              spotlight = Some(
                TournamentShield.spotlight(
                  VariantKeys.variantName(shield.variant),
                  shield.variant.perfIcon
                )
              )
            )
          }
        }
      )
      .flatten filter { _.schedule.at isAfter rightNow }

    val weeklySchedule = List(
      (nextMonday, 3),
      (nextMonday, 6),
      (nextMonday, 9),
      (nextMonday, 15),
      (nextMonday, 21),
      (nextTuesday, 2),
      (nextTuesday, 5),
      (nextTuesday, 8),
      (nextTuesday, 14),
      (nextTuesday, 20),
      (nextWednesday, 1),
      (nextWednesday, 4),
      (nextWednesday, 7),
      (nextWednesday, 10),
      (nextWednesday, 15),
      (nextWednesday, 19),
      (nextThursday, 0),
      (nextThursday, 3),
      (nextThursday, 6),
      (nextThursday, 9),
      (nextThursday, 14),
      (nextThursday, 20),
      (nextFriday, 1),
      (nextFriday, 5),
      (nextFriday, 8),
      (nextFriday, 15),
      (nextFriday, 21),
      (nextSaturday, 1),
      (nextSaturday, 4),
      (nextSaturday, 7),
      (nextSaturday, 10),
      (nextSaturday, 16),
      (nextSaturday, 22),
      (nextSunday, 4),
      (nextSunday, 7),
      (nextSunday, 10),
      (nextSunday, 16),
      (nextSunday, 22)
    )

    val nextWeeklySchedule = weeklySchedule.map { case (day, hour) => (day.plusDays(7), hour) }

    // How to add a new variant and slot without breaking the current schedule:
    // Decide on a new timeslot and add to `weeklySchedule`
    // Look at the current schedule on the site, and work out when that new slot is next going to be used
    // Add the new variant to the list below between the two variants it would be between when that slot is first used
    // Work out how many full cycles of weeklys there have been in the year
    // This is basically 0 if the week number of the year is less than the number of different variants, or 1 if it's greater than
    // Shuffle that many variants (0 or 1) from the start of the list to the bottom
    // Because we create two weeks in advance we will then need to delete one tournament in the second week where the new variant has cycled into. It should just be one, if not its gone wrong
    // Practise locally, can always delete any newly created tournaments and try again
    val weeklyVariants: List[(Variant, Schedule.Speed)] = List(
      (Variant.Draughts(strategygames.draughts.variant.Antidraughts), Blitz32),
      (Variant.FairySF(strategygames.fairysf.variant.MiniShogi), Byoyomi35),
      (Variant.Chess(strategygames.chess.variant.Atomic), Blitz32),
      (Variant.Go(strategygames.go.variant.Go19x19), Blitz53),
      (Variant.Draughts(strategygames.draughts.variant.Breakthrough), Blitz32),
      (Variant.FairySF(strategygames.fairysf.variant.Flipello), Blitz32),
      (Variant.Chess(strategygames.chess.variant.Crazyhouse), Blitz32),
      (Variant.Draughts(strategygames.draughts.variant.Pool), Blitz32),
      (Variant.Chess(strategygames.chess.variant.LinesOfAction), Blitz32),
      (Variant.Chess(strategygames.chess.variant.FiveCheck), Blitz32),
      (Variant.Draughts(strategygames.draughts.variant.Frysk), Blitz32),
      (Variant.FairySF(strategygames.fairysf.variant.Amazons), Blitz32),
      (Variant.Chess(strategygames.chess.variant.Horde), Blitz32),
      (Variant.Draughts(strategygames.draughts.variant.Portuguese), Blitz32),
      (Variant.Samurai(strategygames.samurai.variant.Oware), Blitz32),
      (Variant.Chess(strategygames.chess.variant.Antichess), Blitz32),
      (Variant.Draughts(strategygames.draughts.variant.Standard), Blitz32),
      (Variant.Go(strategygames.go.variant.Go9x9), Blitz32),
      (Variant.FairySF(strategygames.fairysf.variant.Xiangqi), Blitz53),
      (Variant.Chess(strategygames.chess.variant.Monster), Blitz32),
      (Variant.Chess(strategygames.chess.variant.KingOfTheHill), Blitz32),
      (Variant.Draughts(strategygames.draughts.variant.Brazilian), Blitz32),
      (Variant.FairySF(strategygames.fairysf.variant.Shogi), Byoyomi510),
      (Variant.Chess(strategygames.chess.variant.RacingKings), Blitz32),
      (Variant.Backgammon(strategygames.backgammon.variant.Backgammon), Delay212),
      (Variant.Draughts(strategygames.draughts.variant.Russian), Blitz32),
      (Variant.FairySF(strategygames.fairysf.variant.Flipello10), Blitz32),
      (Variant.Chess(strategygames.chess.variant.NoCastling), Blitz32),
      (Variant.Draughts(strategygames.draughts.variant.Frisian), Blitz32),
      (Variant.Togyzkumalak(strategygames.togyzkumalak.variant.Togyzkumalak), Blitz53),
      (Variant.Chess(strategygames.chess.variant.Chess960), Blitz32),
      (Variant.Draughts(strategygames.draughts.variant.English), Blitz32),
      (Variant.Chess(strategygames.chess.variant.ScrambledEggs), Blitz32),
      (Variant.Chess(strategygames.chess.variant.ThreeCheck), Blitz32),
      (Variant.Backgammon(strategygames.backgammon.variant.Nackgammon), Delay212),
      (Variant.FairySF(strategygames.fairysf.variant.MiniXiangqi), Blitz32),
      (Variant.Go(strategygames.go.variant.Go13x13), Blitz53),
      (Variant.Chess(strategygames.chess.variant.Standard), Blitz32)
    )

    val weeklyVariantDefault: (Variant, Schedule.Speed) =
      (Variant.Chess(strategygames.chess.variant.Standard), Blitz32)

    def rotate[A](l: List[A], x: Int): List[A] = {
      if (x <= 0 || l.size <= 1) l
      else if (x >= l.size) rotate(l, x % l.size)
      else rotate(l.tail ++ List(l.head), x - 1)
    }

    // weekly tournaments
    val weeklyTourmaments = (weeklySchedule.zipWithIndex ++ nextWeeklySchedule.zipWithIndex).map {
      case (date, i) => {
        val rotatedVariants = rotate(weeklyVariants, date._1.weekOfWeekyear().get())
        (date, rotatedVariants.lift(i).getOrElse(weeklyVariantDefault))
      }
    } flatMap { case ((day, hour), (variant, speed)) =>
      scheduleWeekly(speed, variant)(day, hour)
    } filter { _.schedule.at isAfter rightNow }

    //yearly tournaments 2024
    val yearly2024Tournaments = List(
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.Standard), Blitz32)(
        new DateTime(2024, 1, 5, 0, 0)
      ),
      scheduleYearly24hr(Variant.Draughts(strategygames.draughts.variant.Antidraughts), Blitz32)(
        new DateTime(2024, 1, 12, 0, 0)
      ),
      scheduleYearly24hr(Variant.FairySF(strategygames.fairysf.variant.MiniShogi), Byoyomi35)(
        new DateTime(2024, 1, 19, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.Atomic), Blitz32)(
        new DateTime(2024, 1, 26, 0, 0)
      ),
      scheduleYearly24hr(Variant.Draughts(strategygames.draughts.variant.Breakthrough), Blitz32)(
        new DateTime(2024, 2, 2, 0, 0)
      ),
      scheduleYearly24hr(Variant.Go(strategygames.go.variant.Go13x13), Blitz53)(
        new DateTime(2024, 2, 9, 0, 0)
      ),
      scheduleYearly24hr(Variant.FairySF(strategygames.fairysf.variant.Flipello), Blitz32)(
        new DateTime(2024, 2, 16, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.Crazyhouse), Blitz32)(
        new DateTime(2024, 2, 23, 0, 0)
      ),
      scheduleYearly24hr(Variant.Draughts(strategygames.draughts.variant.Pool), Blitz32)(
        new DateTime(2024, 3, 1, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.LinesOfAction), Blitz32)(
        new DateTime(2024, 3, 8, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.FiveCheck), Blitz32)(
        new DateTime(2024, 3, 15, 0, 0)
      ),
      scheduleYearly24hr(Variant.Draughts(strategygames.draughts.variant.Frysk), Blitz32)(
        new DateTime(2024, 3, 22, 0, 0)
      ),
      scheduleYearly24hr(Variant.FairySF(strategygames.fairysf.variant.Amazons), Blitz32)(
        new DateTime(2024, 3, 29, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.Horde), Blitz32)(
        new DateTime(2024, 4, 5, 0, 0)
      ),
      scheduleYearly24hr(Variant.Draughts(strategygames.draughts.variant.Portuguese), Blitz32)(
        new DateTime(2024, 4, 12, 0, 0)
      ),
      scheduleYearly24hr(Variant.Samurai(strategygames.samurai.variant.Oware), Blitz32)(
        new DateTime(2024, 4, 19, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.Antichess), Blitz32)(
        new DateTime(2024, 4, 26, 0, 0)
      ),
      scheduleYearly24hr(Variant.Draughts(strategygames.draughts.variant.Standard), Blitz32)(
        new DateTime(2024, 5, 3, 0, 0)
      ),
      scheduleYearly24hr(Variant.FairySF(strategygames.fairysf.variant.Xiangqi), Blitz53)(
        new DateTime(2024, 5, 10, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.KingOfTheHill), Blitz32)(
        new DateTime(2024, 5, 17, 0, 0)
      ),
      scheduleYearly24hr(Variant.Go(strategygames.go.variant.Go19x19), Blitz53)(
        new DateTime(2024, 5, 24, 0, 0)
      ),
      scheduleYearly24hr(Variant.Draughts(strategygames.draughts.variant.Brazilian), Blitz32)(
        new DateTime(2024, 5, 31, 0, 0)
      ),
      scheduleYearly24hr(Variant.FairySF(strategygames.fairysf.variant.Shogi), Byoyomi510)(
        new DateTime(2024, 6, 7, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.RacingKings), Blitz32)(
        new DateTime(2024, 6, 14, 0, 0)
      ),
      scheduleYearly24hr(Variant.Draughts(strategygames.draughts.variant.Russian), Blitz32)(
        new DateTime(2024, 6, 21, 0, 0)
      ),
      scheduleYearly24hr(Variant.FairySF(strategygames.fairysf.variant.Flipello10), Blitz32)(
        new DateTime(2024, 6, 28, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.NoCastling), Blitz32)(
        new DateTime(2024, 7, 5, 0, 0)
      ),
      scheduleYearly24hr(Variant.Draughts(strategygames.draughts.variant.Frisian), Blitz32)(
        new DateTime(2024, 7, 12, 0, 0)
      ),
      //skip 19th July 2024 for Birthday tournament...
      scheduleYearly24hr(Variant.Togyzkumalak(strategygames.togyzkumalak.variant.Togyzkumalak), Blitz53)(
        new DateTime(2024, 7, 26, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.Chess960), Blitz32)(
        new DateTime(2024, 8, 2, 0, 0)
      ),
      scheduleYearly24hr(Variant.Draughts(strategygames.draughts.variant.English), Blitz32)(
        new DateTime(2024, 8, 9, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.ScrambledEggs), Blitz32)(
        new DateTime(2024, 8, 16, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.ThreeCheck), Blitz32)(
        new DateTime(2024, 8, 23, 0, 0)
      ),
      scheduleYearly24hr(Variant.FairySF(strategygames.fairysf.variant.MiniXiangqi), Blitz32)(
        new DateTime(2024, 8, 30, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.Monster), Blitz32)(
        new DateTime(2024, 9, 6, 0, 0)
      ),
      scheduleYearly24hr(Variant.Go(strategygames.go.variant.Go9x9), Blitz32)(
        new DateTime(2024, 9, 13, 0, 0)
      ),
      scheduleYearly24hr(Variant.Backgammon(strategygames.backgammon.variant.Backgammon), Delay212)(
        new DateTime(2024, 9, 20, 0, 0)
      )
      //TODO seperate out both backgammon variants
      // scheduleYearly24hr(Variant.Backgammon(strategygames.backgammon.variant.Nackgammon), Delay212)(
      //   new DateTime(2024, 9, 27, 0, 0)
      // )
      //Fri 27th is the end of year medley
    ).flatten filter { _.schedule.at isAfter rightNow }

    //order matters for pruning weekly/yearly tournaments
    yearly2024Tournaments ::: thisWeekMedleyShields ::: nextWeekMedleyShields ::: thisMonthShields ::: nextMonthShields ::: weeklyTourmaments

//          List( // shield tournaments!
//            month.firstWeek.withDayOfWeek(MONDAY)    -> Bullet,
//            month.firstWeek.withDayOfWeek(TUESDAY)   -> SuperBlitz,
//            month.firstWeek.withDayOfWeek(WEDNESDAY) -> Blitz,
//            month.firstWeek.withDayOfWeek(THURSDAY)  -> Rapid,
//            month.firstWeek.withDayOfWeek(FRIDAY)    -> Classical,
//            month.firstWeek.withDayOfWeek(SATURDAY)  -> HyperBullet,
//            month.firstWeek.withDayOfWeek(SUNDAY)    -> UltraBullet
//          ).flatMap { case (day, speed) =>
//            at(day, 16) map { date =>
//              Schedule(Shield, speed, Standard, none, date) plan {
//                _.copy(
//                  name = s"${speed.toString} Shield",
//                  spotlight = Some(TournamentShield spotlight speed.toString)
//                )
//              }
//            }
//          },
//          List( // shield variant tournaments!
//            month.secondWeek.withDayOfWeek(SUNDAY)   -> Chess960,
//            month.thirdWeek.withDayOfWeek(MONDAY)    -> Crazyhouse,
//            month.thirdWeek.withDayOfWeek(TUESDAY)   -> KingOfTheHill,
//            month.thirdWeek.withDayOfWeek(WEDNESDAY) -> RacingKings,
//            month.thirdWeek.withDayOfWeek(THURSDAY)  -> Antichess,
//            month.thirdWeek.withDayOfWeek(FRIDAY)    -> Atomic,
//            month.thirdWeek.withDayOfWeek(SATURDAY)  -> Horde,
//            month.thirdWeek.withDayOfWeek(SUNDAY)    -> ThreeCheck
//          ).flatMap { case (day, variant) =>
//            at(day, 16) map { date =>
//              Schedule(Shield, Blitz, variant, none, date) plan {
//                _.copy(
//                  name = s"${VariantKeys.variantName(variant)} Shield",
//                  spotlight = Some(TournamentShield spotlight VariantKeys.variantName(variant))
//                )
//              }
//            }
//          }

    /*// all dates UTC
    List(
      //Pre MSO schedule
      scheduleUnique(13, Blitz32, Variant.Chess(strategygames.chess.variant.Horde), 30)(
        new DateTime(2021, 8, 3, 0, 0)
      ),
      scheduleUnique(20, Blitz32, Variant.Chess(strategygames.chess.variant.Standard), 30)(
        new DateTime(2021, 8, 3, 0, 0)
      ),
      scheduleUnique(13, Blitz32, Variant.Chess(strategygames.chess.variant.LinesOfAction), 30)(
        new DateTime(2021, 8, 4, 0, 0)
      ),
      scheduleUnique(20, Blitz32, Variant.Chess(strategygames.chess.variant.Crazyhouse), 30)(
        new DateTime(2021, 8, 4, 0, 0)
      ),
      scheduleUnique(13, Blitz32, Variant.Chess(strategygames.chess.variant.RacingKings), 30)(
        new DateTime(2021, 8, 5, 0, 0)
      ),
      scheduleUnique(20, Blitz32, Variant.Chess(strategygames.chess.variant.ThreeCheck), 30)(
        new DateTime(2021, 8, 5, 0, 0)
      ),
      scheduleUnique(13, Blitz32, Variant.Chess(strategygames.chess.variant.Antichess), 30)(
        new DateTime(2021, 8, 6, 0, 0)
      ),
      scheduleUnique(20, Blitz32, Variant.Chess(strategygames.chess.variant.Chess960), 30)(
        new DateTime(2021, 8, 6, 0, 0)
      ),
      scheduleUnique(13, Blitz32, Variant.Chess(strategygames.chess.variant.Standard), 30)(
        new DateTime(2021, 8, 7, 0, 0)
      ),
      scheduleUnique(20, Blitz32, Variant.Chess(strategygames.chess.variant.LinesOfAction), 30)(
        new DateTime(2021, 8, 7, 0, 0)
      ),
      scheduleUnique(13, Blitz32, Variant.Chess(strategygames.chess.variant.Crazyhouse), 30)(
        new DateTime(2021, 8, 8, 0, 0)
      ),
      scheduleUnique(20, Blitz32, Variant.Chess(strategygames.chess.variant.Chess960), 30)(
        new DateTime(2021, 8, 8, 0, 0)
      ),
      scheduleUnique(13, Blitz32, Variant.Chess(strategygames.chess.variant.KingOfTheHill), 30)(
        new DateTime(2021, 8, 9, 0, 0)
      ),
      scheduleUnique(20, Blitz32, Variant.Chess(strategygames.chess.variant.Atomic), 30)(
        new DateTime(2021, 8, 9, 0, 0)
      ),
      scheduleUnique(13, Blitz32, Variant.Chess(strategygames.chess.variant.RacingKings), 30)(
        new DateTime(2021, 8, 10, 0, 0)
      ),
      scheduleUnique(20, Blitz32, Variant.Chess(strategygames.chess.variant.ThreeCheck), 30)(
        new DateTime(2021, 8, 10, 0, 0)
      ),
      scheduleUnique(13, Blitz32, Variant.Chess(strategygames.chess.variant.LinesOfAction), 30)(
        new DateTime(2021, 8, 11, 0, 0)
      ),
      scheduleUnique(20, Blitz32, Variant.Chess(strategygames.chess.variant.Chess960), 30)(
        new DateTime(2021, 8, 11, 0, 0)
      ),
      scheduleUnique(13, Blitz32, Variant.Chess(strategygames.chess.variant.Standard), 30)(
        new DateTime(2021, 8, 12, 0, 0)
      ),
      scheduleUnique(20, Blitz32, Variant.Chess(strategygames.chess.variant.Crazyhouse), 30)(
        new DateTime(2021, 8, 12, 0, 0)
      ),
      //MSO arena schedule
      scheduleUnique(19, Blitz32, Variant.Chess(strategygames.chess.variant.Standard), 180)(
        new DateTime(2021, 8, 15, 0, 0)
      ),
      scheduleUnique(19, Blitz32, Variant.Chess(strategygames.chess.variant.KingOfTheHill), 120)(
        new DateTime(2021, 8, 18, 0, 0)
      ),
      scheduleUnique(19, Blitz35, Variant.Chess(strategygames.chess.variant.Horde), 120)(
        new DateTime(2021, 8, 22, 0, 0)
      ),
      scheduleUnique(19, Blitz32, Variant.Chess(strategygames.chess.variant.RacingKings), 60)(
        new DateTime(2021, 8, 25, 0, 0)
      ),
      scheduleUnique(19, Blitz32, Variant.Chess(strategygames.chess.variant.Crazyhouse), 120)(
        new DateTime(2021, 8, 26, 0, 0)
      ),
      scheduleUnique(19, Blitz32, Variant.Chess(strategygames.chess.variant.Atomic), 120)(
        new DateTime(2021, 8, 27, 0, 0)
      ),
      scheduleUnique(19, Blitz32, Variant.Chess(strategygames.chess.variant.Antichess), 120)(
        new DateTime(2021, 8, 29, 0, 0)
      ),
      scheduleUnique(19, Bullet, Variant.Chess(strategygames.chess.variant.Standard), 60)(
        new DateTime(2021, 8, 30, 0, 0)
      )

      //Pre UKGE schedule
      mwfs.flatMap(schedule32(16, KingOfTheHill)), // MWFS KoTH @ 17:00 UK
      mwfs.flatMap(schedule32(17, Antichess)),     // MWFS Anti @ 18:00 UK
      mwfs.flatMap(schedule32(18, Standard)),      // MWFS Chess @ 19:00 UK
      mwfs.flatMap(schedule51(19, LinesOfAction)), // MWFS LOA @ 20:00 UK
      mwfs.flatMap(schedule32(20, Horde)),         // MWFS Horde @ 21:00 UK
      mwfs.flatMap(schedule32(21, RacingKings)),   // MWFS Racing Kings @ 22:00 UK
      tts.flatMap(schedule32(16, Atomic)),         // TTF Atomic @ 17:00 UK
      tts.flatMap(schedule32(17, ThreeCheck)),     // TTF 3+ @ 18:00 UK
      tts.flatMap(schedule32(18, Crazyhouse)),     // TTF ZH @ 19:00 UK
      tts.flatMap(schedule32(19, Chess960)),       // TTF 960 @ 20:00 UK
      tts.flatMap(schedule32(20, LinesOfAction)),  // TTF LOA @ 21:00 UK
      tts.flatMap(schedule10(21, Standard)),       // TTF Bullet @ 22:00 UK
      fss.flatMap(schedule32(9, RacingKings)),     // FSS Racing Kings @ 10:00 UK
      fss.flatMap(schedule51(10, LinesOfAction)),  // FSS LOA @ 11:00 UK
      fss.flatMap(schedule32(11, Standard)),       // FSS Chess @ 12:00 UK
      fss.flatMap(schedule32(12, Crazyhouse)),     // FSS ZH @ 13:00 UK
      fss.flatMap(schedule51(13, LinesOfAction)),  // FSS LOA @ 14:00 UK
      fss.flatMap(schedule32(14, Standard)),       // FSS Chess @ 15:00 UK
      fss.flatMap(schedule32(15, Chess960))        // FSS 960 @ 16:00 UK
     */

    /*List( // legendary tournaments!
        at(birthday.withYear(today.getYear), 12) map orNextYear map { date =>
          val yo = date.getYear - 2021
          Schedule(Unique, Rapid, Standard, none, date) plan {
            _.copy(
              name = s"${date.getYear} PlayStrategy Anniversary",
              minutes = 12 * 60,
              spotlight = Spotlight(
                headline = s"$yo years of free strategy games!",
                description = s"""
We've had $yo great years together!

Thank you all, you rock!"""
              ).some
            )
          }
        }
      ).flatten,*/
    /*List( // yearly tournaments!
        secondWeekOf(JANUARY).withDayOfWeek(MONDAY)      -> Bullet,
        secondWeekOf(FEBRUARY).withDayOfWeek(TUESDAY)    -> SuperBlitz,
        secondWeekOf(MARCH).withDayOfWeek(WEDNESDAY)     -> Blitz,
        secondWeekOf(APRIL).withDayOfWeek(THURSDAY)      -> Rapid,
        secondWeekOf(MAY).withDayOfWeek(FRIDAY)          -> Classical,
        secondWeekOf(JUNE).withDayOfWeek(SATURDAY)       -> HyperBullet,
        secondWeekOf(JULY).withDayOfWeek(MONDAY)         -> Bullet,
        secondWeekOf(AUGUST).withDayOfWeek(TUESDAY)      -> SuperBlitz,
        secondWeekOf(SEPTEMBER).withDayOfWeek(WEDNESDAY) -> Blitz,
        secondWeekOf(OCTOBER).withDayOfWeek(THURSDAY)    -> Rapid,
        secondWeekOf(NOVEMBER).withDayOfWeek(FRIDAY)     -> Classical,
        secondWeekOf(DECEMBER).withDayOfWeek(SATURDAY)   -> HyperBullet
      ).flatMap { case (day, speed) =>
        at(day, 17) filter farFuture.isAfter map { date =>
          Schedule(Yearly, speed, Standard, none, date).plan
        }
      },*/
    /*List( // yearly variant tournaments!
        secondWeekOf(JANUARY).withDayOfWeek(WEDNESDAY) -> Chess960,
        secondWeekOf(FEBRUARY).withDayOfWeek(THURSDAY) -> Crazyhouse,
        secondWeekOf(MARCH).withDayOfWeek(FRIDAY)      -> KingOfTheHill,
        secondWeekOf(APRIL).withDayOfWeek(SATURDAY)    -> RacingKings,
        secondWeekOf(MAY).withDayOfWeek(MONDAY)        -> Antichess,
        secondWeekOf(JUNE).withDayOfWeek(TUESDAY)      -> Atomic,
        secondWeekOf(JULY).withDayOfWeek(WEDNESDAY)    -> Horde,
        secondWeekOf(AUGUST).withDayOfWeek(THURSDAY)   -> ThreeCheck
      ).flatMap { case (day, variant) =>
        at(day, 17) filter farFuture.isAfter map { date =>
          Schedule(Yearly, SuperBlitz, variant, none, date).plan
        }
      },*/
    /*List(thisMonth, nextMonth).flatMap { month =>
        List(
          List( // monthly standard tournaments!
            month.lastWeek.withDayOfWeek(MONDAY)    -> Bullet,
            month.lastWeek.withDayOfWeek(TUESDAY)   -> SuperBlitz,
            month.lastWeek.withDayOfWeek(WEDNESDAY) -> Blitz,
            month.lastWeek.withDayOfWeek(THURSDAY)  -> Rapid,
            month.lastWeek.withDayOfWeek(FRIDAY)    -> Classical,
            month.lastWeek.withDayOfWeek(SATURDAY)  -> HyperBullet,
            month.lastWeek.withDayOfWeek(SUNDAY)    -> UltraBullet
          ).flatMap { case (day, speed) =>
            at(day, 17) map { date =>
              Schedule(Monthly, speed, Standard, none, date).plan
            }
          },
          List( // monthly variant tournaments!
            month.lastWeek.withDayOfWeek(MONDAY)    -> Chess960,
            month.lastWeek.withDayOfWeek(TUESDAY)   -> Crazyhouse,
            month.lastWeek.withDayOfWeek(WEDNESDAY) -> KingOfTheHill,
            month.lastWeek.withDayOfWeek(THURSDAY)  -> RacingKings,
            month.lastWeek.withDayOfWeek(FRIDAY)    -> Antichess,
            month.lastWeek.withDayOfWeek(SATURDAY)  -> Atomic,
            month.lastWeek.withDayOfWeek(SUNDAY)    -> Horde
          ).flatMap { case (day, variant) =>
            at(day, 19) map { date =>
              Schedule(
                Monthly,
                if (variant == Chess960 || variant == Crazyhouse) Blitz else SuperBlitz,
                variant,
                none,
                date
              ).plan
            }
          },
          List( // shield tournaments!
            month.firstWeek.withDayOfWeek(MONDAY)    -> Bullet,
            month.firstWeek.withDayOfWeek(TUESDAY)   -> SuperBlitz,
            month.firstWeek.withDayOfWeek(WEDNESDAY) -> Blitz,
            month.firstWeek.withDayOfWeek(THURSDAY)  -> Rapid,
            month.firstWeek.withDayOfWeek(FRIDAY)    -> Classical,
            month.firstWeek.withDayOfWeek(SATURDAY)  -> HyperBullet,
            month.firstWeek.withDayOfWeek(SUNDAY)    -> UltraBullet
          ).flatMap { case (day, speed) =>
            at(day, 16) map { date =>
              Schedule(Shield, speed, Standard, none, date) plan {
                _.copy(
                  name = s"${speed.toString} Shield",
                  spotlight = Some(TournamentShield spotlight speed.toString)
                )
              }
            }
          },
          List( // shield variant tournaments!
            month.secondWeek.withDayOfWeek(SUNDAY)   -> Chess960,
            month.thirdWeek.withDayOfWeek(MONDAY)    -> Crazyhouse,
            month.thirdWeek.withDayOfWeek(TUESDAY)   -> KingOfTheHill,
            month.thirdWeek.withDayOfWeek(WEDNESDAY) -> RacingKings,
            month.thirdWeek.withDayOfWeek(THURSDAY)  -> Antichess,
            month.thirdWeek.withDayOfWeek(FRIDAY)    -> Atomic,
            month.thirdWeek.withDayOfWeek(SATURDAY)  -> Horde,
            month.thirdWeek.withDayOfWeek(SUNDAY)    -> ThreeCheck
          ).flatMap { case (day, variant) =>
            at(day, 16) map { date =>
              Schedule(Shield, Blitz, variant, none, date) plan {
                _.copy(
                  name = s"${VariantKeys.variantName(variant)} Shield",
                  spotlight = Some(TournamentShield spotlight VariantKeys.variantName(variant))
                )
              }
            }
          }
        ).flatten
      },*/
    /*List( // weekly standard tournaments!
        nextMonday    -> Bullet,
        nextTuesday   -> SuperBlitz,
        nextWednesday -> Blitz,
        nextThursday  -> Rapid,
        nextFriday    -> Classical,
        nextSaturday  -> HyperBullet
      ).flatMap { case (day, speed) =>
        at(day, 17) map { date =>
          Schedule(Weekly, speed, Standard, none, date pipe orNextWeek).plan
        }
      },*/
    /*List( // weekly variant tournaments!
        nextMonday    -> ThreeCheck,
        nextTuesday   -> Crazyhouse,
        nextWednesday -> KingOfTheHill,
        nextThursday  -> RacingKings,
        nextFriday    -> Antichess,
        nextSaturday  -> Atomic,
        nextSunday    -> Horde,
        nextSunday    -> Chess960
      ).flatMap { case (day, variant) =>
        at(day, 19) map { date =>
          Schedule(
            Weekly,
            if (variant == Chess960 || variant == Crazyhouse) Blitz else SuperBlitz,
            variant,
            none,
            date pipe orNextWeek
          ).plan
        }
      },*/
    /*List( // week-end elite tournaments!
        nextSaturday -> SuperBlitz,
        nextSunday   -> Bullet
      ).flatMap { case (day, speed) =>
        at(day, 17) map { date =>
          Schedule(Weekend, speed, Standard, none, date pipe orNextWeek).plan
        }
      },*/
    /*List( // daily tournaments!
        at(today, 16) map { date =>
          Schedule(Daily, Bullet, Standard, none, date pipe orTomorrow).plan
        },
        at(today, 17) map { date =>
          Schedule(Daily, SuperBlitz, Standard, none, date pipe orTomorrow).plan
        },
        at(today, 18) map { date =>
          Schedule(Daily, Blitz, Standard, none, date pipe orTomorrow).plan
        },
        at(today, 19) map { date =>
          Schedule(Daily, Rapid, Standard, none, date pipe orTomorrow).plan
        },
        at(today, 20) map { date =>
          Schedule(Daily, HyperBullet, Standard, none, date pipe orTomorrow).plan
        },
        at(today, 21) map { date =>
          Schedule(Daily, UltraBullet, Standard, none, date pipe orTomorrow).plan
        }
      ).flatten,*/
    /*List( // daily variant tournaments!
        at(today, 20) map { date =>
          Schedule(Daily, Blitz, Crazyhouse, none, date pipe orTomorrow).plan
        },
        at(today, 21) map { date =>
          Schedule(Daily, Blitz, Chess960, none, date pipe orTomorrow).plan
        },
        at(today, 22) map { date =>
          Schedule(Daily, SuperBlitz, KingOfTheHill, none, date pipe orTomorrow).plan
        },
        at(today, 23) map { date =>
          Schedule(Daily, SuperBlitz, Atomic, none, date pipe orTomorrow).plan
        },
        at(today, 0) map { date =>
          Schedule(Daily, SuperBlitz, Antichess, none, date pipe orTomorrow).plan
        },
        at(tomorrow, 1) map { date =>
          Schedule(Daily, SuperBlitz, ThreeCheck, none, date).plan
        },
        at(tomorrow, 2) map { date =>
          Schedule(Daily, SuperBlitz, Horde, none, date).plan
        },
        at(tomorrow, 3) map { date =>
          Schedule(Daily, SuperBlitz, RacingKings, none, date).plan
        }
      ).flatten,*/
    /*List( // eastern tournaments!
        at(today, 4) map { date =>
          Schedule(Eastern, Bullet, Standard, none, date pipe orTomorrow).plan
        },
        at(today, 5) map { date =>
          Schedule(Eastern, SuperBlitz, Standard, none, date pipe orTomorrow).plan
        },
        at(today, 6) map { date =>
          Schedule(Eastern, Blitz, Standard, none, date pipe orTomorrow).plan
        },
        at(today, 7) map { date =>
          Schedule(Eastern, Rapid, Standard, none, date pipe orTomorrow).plan
        }
      ).flatten,*/
    /*(if (isHalloween) // replace more thematic tournaments on halloween
         List(
           1  -> StartingPosition.presets.halloween,
           5  -> StartingPosition.presets.frankenstein,
           9  -> StartingPosition.presets.halloween,
           13 -> StartingPosition.presets.frankenstein,
           17 -> StartingPosition.presets.halloween,
           21 -> StartingPosition.presets.frankenstein
         )
       else
         List( // random opening replaces hourly 3 times a day
           3  -> opening(offset = 2),
           11 -> opening(offset = 1),
           19 -> opening(offset = 0)
         )).flatMap { case (hour, opening) =>
        List(
          at(today, hour) map { date =>
            Schedule(Hourly, Bullet, Standard, opening.fen.some, date pipe orTomorrow).plan
          },
          at(today, hour + 1) map { date =>
            Schedule(Hourly, SuperBlitz, Standard, opening.fen.some, date pipe orTomorrow).plan
          },
          at(today, hour + 2) map { date =>
            Schedule(Hourly, Blitz, Standard, opening.fen.some, date pipe orTomorrow).plan
          },
          at(today, hour + 3) map { date =>
            Schedule(Hourly, Rapid, Standard, opening.fen.some, date pipe orTomorrow).plan
          }
        ).flatten
      },*/
    /*// hourly standard tournaments!
      (-1 to 6).toList.flatMap { hourDelta =>
        val date = rightNow plusHours hourDelta
        val hour = date.getHourOfDay
        List(
          at(date, hour) map { date =>
            Schedule(Hourly, HyperBullet, Standard, none, date).plan
          },
          at(date, hour, 30) map { date =>
            Schedule(Hourly, UltraBullet, Standard, none, date).plan
          },
          at(date, hour) map { date =>
            Schedule(Hourly, Bullet, Standard, none, date).plan
          },
          at(date, hour, 30) map { date =>
            Schedule(Hourly, Bullet, Standard, none, date).plan
          },
          at(date, hour) map { date =>
            Schedule(Hourly, SuperBlitz, Standard, none, date).plan
          },
          at(date, hour) map { date =>
            Schedule(Hourly, Blitz, Standard, none, date).plan
          },
          at(date, hour) collect {
            case date if hour % 2 == 0 => Schedule(Hourly, Rapid, Standard, none, date).plan
          }
        ).flatten
      },*/
    /*// hourly limited tournaments!
      (-1 to 6).toList
        .flatMap { hourDelta =>
          val date = rightNow plusHours hourDelta
          val hour = date.getHourOfDay
          val speed = hour % 4 match {
            case 0 => Bullet
            case 1 => SuperBlitz
            case 2 => Blitz
            case _ => Rapid
          }
          List(1300, 1500, 1700, 2000).zipWithIndex.flatMap { case (rating, hourDelay) =>
            val perf = Schedule.Speed toPerfType speed
            val conditions = Condition.All(
              nbRatedGame = Condition.NbRatedGame(perf.some, 20).some,
              maxRating = Condition.MaxRating(perf, rating).some,
              minRating = none,
              titled = none,
              teamMember = none
            )
            at(date, hour) ?? { date =>
              val finalDate = date plusHours hourDelay
              if (speed == Bullet)
                List(
                  Schedule(Hourly, speed, Standard, none, finalDate, conditions).plan,
                  Schedule(Hourly, speed, Standard, none, finalDate plusMinutes 30, conditions)
                    .plan(_.copy(clock = strategygames.Clock.Config(60, 1)))
                )
              else
                List(
                  Schedule(Hourly, speed, Standard, none, finalDate, conditions).plan
                )
            }
          }
        }
        .map {
          // No berserk for rating-limited tournaments
          // Because berserking lowers the player rating
          _ map { _.copy(noBerserk = true) }
        },
      // hourly crazyhouse/chess960/KingOfTheHill tournaments!
      (0 to 6).toList.flatMap { hourDelta =>
        val date = rightNow plusHours hourDelta
        val hour = date.getHourOfDay
        val speed = hour % 7 match {
          case 0     => HippoBullet
          case 1 | 4 => Bullet
          case 2 | 5 => SuperBlitz
          case 3 | 6 => Blitz
        }
        val variant = hour % 3 match {
          case 0 => Chess960
          case 1 => KingOfTheHill
          case _ => Crazyhouse
        }
        List(
          at(date, hour) map { date =>
            Schedule(Hourly, speed, variant, none, date).plan
          },
          at(date, hour, 30) collect {
            case date if speed == Bullet =>
              Schedule(Hourly, if (hour == 17) HyperBullet else Bullet, variant, none, date).plan
          }
        ).flatten
      },
      // hourly atomic/antichess variant tournaments!
      (0 to 6).toList.flatMap { hourDelta =>
        val date = rightNow plusHours hourDelta
        val hour = date.getHourOfDay
        val speed = hour % 7 match {
          case 0 | 4 => Blitz
          case 1     => HippoBullet
          case 2 | 5 => Bullet
          case 3 | 6 => SuperBlitz
        }
        val variant = if (hour % 2 == 0) Atomic else Antichess
        List(
          at(date, hour) map { date =>
            Schedule(Hourly, speed, variant, none, date).plan
          },
          at(date, hour, 30) collect {
            case date if speed == Bullet =>
              Schedule(Hourly, if (hour == 18) HyperBullet else Bullet, variant, none, date).plan
          }
        ).flatten
      },
      // hourly threecheck/horde/racing variant tournaments!
      (0 to 6).toList.flatMap { hourDelta =>
        val date = rightNow plusHours hourDelta
        val hour = date.getHourOfDay
        val speed = hour % 7 match {
          case 0 | 4 => SuperBlitz
          case 1 | 5 => Blitz
          case 2     => HippoBullet
          case 3 | 6 => Bullet
        }
        val variant = hour % 3 match {
          case 0 => ThreeCheck
          case 1 => Horde
          case _ => RacingKings
        }
        List(
          at(date, hour) map { date =>
            Schedule(Hourly, speed, variant, none, date).plan
          },
          at(date, hour, 30) collect {
            case date if speed == Bullet =>
              Schedule(Hourly, if (hour == 19) HyperBullet else Bullet, variant, none, date).plan
          }
        ).flatten
      }*/
    //).flatten filter { _.schedule.at isAfter rightNow }
  }

  private[tournament] def pruneConflicts(scheds: List[Tournament], newTourns: List[Tournament]) =
    newTourns
      .foldLeft(List[Tournament]()) { case (tourns, t) =>
        if (overlaps(t, tourns) || overlaps(t, scheds)) tourns
        else t :: tourns
      }
      .reverse

  private case class ScheduleNowWith(dbScheds: List[Tournament])

  private def overlaps(t: Tournament, ts: List[Tournament]): Boolean =
    t.schedule exists { s =>
      ts exists { t2 =>
        t.variant == t2.variant && t2.schedule.?? {
          // prevent daily && weekly on the same day - we don't care about this.
          // case s2 if s.freq.isDailyOrBetter && s2.freq.isDailyOrBetter && s.sameSpeed(s2) => s sameDay s2
          // dont let yearly's block shields and vice versa
          case s2 if s.freq == Shield || s.freq == MedleyShield   => s2.freq == s.freq && (s sameDay s2)
          case s2 if s2.freq == Shield || s2.freq == MedleyShield => false
          case s2 =>
            (
              t.variant.exotic ||  // overlapping exotic variant
                s.hasMaxRating ||  // overlapping same rating limit
                s.similarSpeed(s2) // overlapping similar
            ) && s.similarConditions(s2) && t.overlaps(t2)
        }
      }
    }

  private def at(day: DateTime, hour: Int, minute: Int = 0): Option[DateTime] =
    try {
      Some(day.withTimeAtStartOfDay plusHours hour plusMinutes minute)
    } catch {
      case e: Exception =>
        logger.error(s"failed to schedule one: ${e.getMessage}")
        None
    }

  def receive = {

    case TournamentScheduler.ScheduleNow =>
      tournamentRepo.scheduledUnfinished dforeach { tourneys =>
        self ! ScheduleNowWith(tourneys)
      }

    case ScheduleNowWith(dbScheds) =>
      try {
        val newTourns = allWithConflicts(DateTime.now).map(_.build)
        val pruned    = pruneConflicts(dbScheds, newTourns)
        (tournamentRepo
          .insert(pruned)
          .logFailure(logger) >>
          api.subscribeBotsToArenas).unit
      } catch {
        case e: org.joda.time.IllegalInstantException =>
          logger.error(s"failed to schedule all: ${e.getMessage}")
      }
  }
}

private object TournamentScheduler {

  case object ScheduleNow
}
