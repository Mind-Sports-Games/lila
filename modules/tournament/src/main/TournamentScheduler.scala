package lila.tournament

import akka.actor.*
import strategygames.variant.Variant
import org.joda.time.DateTime
import lila.i18n.VariantKeys
import scala.concurrent.ExecutionContextExecutor

final private class TournamentScheduler(
    api: TournamentApi,
    tournamentRepo: TournamentRepo
) extends Actor {

  import Schedule.Freq.*
  import Schedule.Speed.*
  import Schedule.Plan

  implicit def ec: ExecutionContextExecutor = context.dispatcher

  private val dailyCycleDaysAhead = 14

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
    val today = rightNow.withTimeAtStartOfDay
    //val tomorrow    = rightNow plusDays 1
    //val startOfYear = today.dayOfYear.withMinimumValue

    class OfMonth(fromNow: Int) {
      val firstDay = today.plusMonths(fromNow).dayOfMonth.withMinimumValue
      val lastDay  = firstDay.dayOfMonth.withMaximumValue

      val firstWeek  = firstDay.plusDays(7 - (firstDay.getDayOfWeek - 1) % 7)
      val secondWeek = firstWeek.plusDays(7)
      val thirdWeek  = secondWeek.plusDays(7)
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

    def monthOfWithWeekAndDayOfWeek(month: OfMonth, weekOfMonth: Int, dayOfWeek: Int) =
      month.firstDay
        .plusDays(
          if (month.firstDay.getDayOfWeek <= dayOfWeek) dayOfWeek - month.firstDay.getDayOfWeek
          else 7 - month.firstDay.getDayOfWeek + dayOfWeek
        )
        .plusDays(7 * (weekOfMonth - 1))

    def thisMonthWeekAndDayOfWeek(weekOfMonth: Int, dayOfWeek: Int) =
      monthOfWithWeekAndDayOfWeek(thisMonth, weekOfMonth, dayOfWeek)

    def nextMonthWeekAndDayOfWeek(weekOfMonth: Int, dayOfWeek: Int) =
      monthOfWithWeekAndDayOfWeek(nextMonth, weekOfMonth, dayOfWeek)

    // def secondWeekOf(month: Int) = {
    //   val start = orNextYear(startOfYear.withMonthOfYear(month))
    //   start.plusDays(15 - start.getDayOfWeek)
    // }

    // def orTomorrow(date: DateTime) = if (date isBefore rightNow) date plusDays 1 else date
    // def orNextWeek(date: DateTime) = if (date isBefore rightNow) date plusWeeks 1 else date
    // def orNextYear(date: DateTime) = if (date isBefore rightNow) date plusYears 1 else date

    // val isHalloween = today.getDayOfMonth == 31 && today.getMonthOfYear == OCTOBER

    // def opening(offset: Int) = {
    //   val positions = StartingPosition.featurable
    //   positions((today.getDayOfYear + offset) % positions.size)
    // }

    // val farFuture = today plusMonths 7

    // val birthday = new DateTime(2021, 7, 21, 12, 0, 0)

    // val fss  = List(nextFriday, nextSaturday, nextSunday)
    // val mwfs = List(nextMonday, nextWednesday, nextFriday, nextSunday)
    // val tts  = List(nextTuesday, nextThursday, nextSaturday)

    // def schedule10(hour: Int, v: Variant)(day: DateTime) =
    //   at(day, hour) map { date =>
    //     Schedule(Weekly, Bullet, v, none, date).plan
    //   }

    // def schedule32(hour: Int, v: Variant)(day: DateTime) =
    //   at(day, hour) map { date =>
    //     Schedule(Weekly, Blitz32, v, none, date).plan
    //   }

    // def schedule51(hour: Int, v: Variant)(day: DateTime) =
    //   at(day, hour) map { date =>
    //     Schedule(Weekly, Blitz51, v, none, date).plan
    //   }

    // def scheduleUnique(hour: Int, speed: Schedule.Speed, variant: Variant, duration: Int)(
    //     day: DateTime
    // ) =
    //   at(day, hour) map { date =>
    //     Schedule(Unique, speed, variant, none, date, Some(duration)).plan
    //   }

    def scheduleMedleyShield(medleyShield: TournamentShield.MedleyShield)(
        day: DateTime
    ) =
      at(day, medleyShield.hour) map { date =>
        Schedule(
          MedleyShield,
          medleyShield.speed,
          medleyShield.variants.head,
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
          ),
          medleyShield.useStatusScoring
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
          Some(60 * 24),
          statusScoring = variant.key == "backgammon" || variant.key == "nackgammon"
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

    def scheduleDailyCycle(slot: TournamentDailyCycle.Slot)(day: DateTime) =
      at(day, slot.hour) map { date =>
        Schedule(
          DailyCycle,
          slot.speed,
          slot.variant,
          none,
          date,
          Some(TournamentDailyCycle.arenaMinutes),
          statusScoring = slot.variant.key == "backgammon" || slot.variant.key == "nackgammon"
        ).plan {
          _.copy(
            spotlight = Some(
              Spotlight(
                iconFont = slot.variant.perfIcon.toString.some,
                headline = "",
                description = s"A daily tournament for ${VariantKeys.variantName(slot.variant)}",
                homepageHours = 1.some
              )
            )
          )
        }
      }

    // schedule this week
    val thisWeekMedleyShields = TournamentShield.MedleyShield.allWeekly
      .map(ms =>
        scheduleMedleyShield(ms)(
          nextDayOfWeek(ms.dayOfWeek)
        )
      )
      .flatten filter { _.schedule.at.isAfter(rightNow) }

    // and schedule two weeks in advance
    val nextWeekMedleyShields = TournamentShield.MedleyShield.allWeekly
      .map(ms =>
        scheduleMedleyShield(ms)(
          nextDayOfFortnight(ms.dayOfWeek + 7)
        )
      )
      .flatten filter { _.schedule.at.isAfter(rightNow) }

    // schedule this month
    val thisMonthMedleyShields = TournamentShield.MedleyShield.allMonthly
      .map(ms =>
        scheduleMedleyShield(ms)(
          thisMonthWeekAndDayOfWeek(ms.weekOfMonth.getOrElse(1), ms.dayOfWeek)
        )
      )
      .flatten filter { _.schedule.at.isAfter(rightNow) }

    // and schedule two months in advance
    val nextMonthMedleyShields = TournamentShield.MedleyShield.allMonthly
      .map(ms =>
        scheduleMedleyShield(ms)(
          nextMonthWeekAndDayOfWeek(ms.weekOfMonth.getOrElse(1), ms.dayOfWeek)
        )
      )
      .flatten filter { _.schedule.at.isAfter(rightNow) }

    val shieldDuration = Some(TournamentShield.arenaMinutes)

    // schedule this months shields
    val thisMonthShields = TournamentShield.Category.all
      .map(shield =>
        at(thisMonthWithDay(shield.dayOfMonth), shield.scheduleHour(thisMonth.index)) map { date =>
          Schedule(
            Shield,
            shield.speed,
            shield.variant,
            none,
            date,
            shieldDuration,
            statusScoring = shield.variant.key == "backgammon" || shield.variant.key == "nackgammon"
          ) plan {
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
      .flatten filter { _.schedule.at.isAfter(rightNow) }

    // and schedule next month
    val nextMonthShields = TournamentShield.Category.all
      .map(shield =>
        at(nextMonthWithDay(shield.dayOfMonth), shield.scheduleHour(nextMonth.index)) map { date =>
          Schedule(
            Shield,
            shield.speed,
            shield.variant,
            none,
            date,
            shieldDuration,
            statusScoring = shield.variant.key == "backgammon" || shield.variant.key == "nackgammon"
          ) plan {
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
      .flatten filter { _.schedule.at.isAfter(rightNow) }

    // yearly tournaments 2026
    val yearly2026Tournaments = List(
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.Standard), Blitz32)(
        new DateTime(2026, 1, 2, 0, 0)
      ),
      scheduleYearly24hr(Variant.Draughts(strategygames.draughts.variant.Antidraughts), Blitz53)(
        new DateTime(2026, 1, 9, 0, 0)
      ),
      scheduleYearly24hr(Variant.FairySF(strategygames.fairysf.variant.MiniShogi), Byoyomi35)(
        new DateTime(2026, 1, 16, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.Atomic), Blitz32)(
        new DateTime(2026, 1, 23, 0, 0)
      ),
      scheduleYearly24hr(Variant.Backgammon(strategygames.backgammon.variant.Hyper), Delay110)(
        new DateTime(2026, 1, 30, 0, 0)
      ),
      scheduleYearly24hr(Variant.Draughts(strategygames.draughts.variant.Breakthrough), Blitz53)(
        new DateTime(2026, 2, 6, 0, 0)
      ),
      scheduleYearly24hr(Variant.Go(strategygames.go.variant.Go13x13), Byoyomi310x5)(
        new DateTime(2026, 2, 13, 0, 0)
      ),
      scheduleYearly24hr(Variant.FairySF(strategygames.fairysf.variant.Flipello), Blitz)(
        new DateTime(2026, 2, 20, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.Crazyhouse), Blitz32)(
        new DateTime(2026, 2, 27, 0, 0)
      ),
      scheduleYearly24hr(Variant.Draughts(strategygames.draughts.variant.Pool), Blitz32)(
        new DateTime(2026, 3, 6, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.LinesOfAction), Blitz32)(
        new DateTime(2026, 3, 13, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.FiveCheck), Blitz32)(
        new DateTime(2026, 3, 20, 0, 0)
      ),
      scheduleYearly24hr(Variant.Draughts(strategygames.draughts.variant.Frysk), Blitz21)(
        new DateTime(2026, 3, 27, 0, 0)
      ),
      scheduleYearly24hr(Variant.FairySF(strategygames.fairysf.variant.Amazons), Blitz35)(
        new DateTime(2026, 4, 3, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.Horde), Blitz53)(
        new DateTime(2026, 4, 10, 0, 0)
      ),
      scheduleYearly24hr(Variant.Draughts(strategygames.draughts.variant.Portuguese), Blitz32)(
        new DateTime(2026, 4, 17, 0, 0)
      ),
      scheduleYearly24hr(Variant.Samurai(strategygames.samurai.variant.Oware), Blitz32)(
        new DateTime(2026, 4, 24, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.Antichess), Blitz32)(
        new DateTime(2026, 5, 1, 0, 0)
      ),
      scheduleYearly24hr(Variant.Backgammon(strategygames.backgammon.variant.Nackgammon), Delay210)(
        new DateTime(2026, 5, 8, 0, 0)
      ),
      scheduleYearly24hr(Variant.FairySF(strategygames.fairysf.variant.Xiangqi), Blitz53)(
        new DateTime(2026, 5, 15, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.KingOfTheHill), Blitz32)(
        new DateTime(2026, 5, 22, 0, 0)
      ),
      // skip 29th May 2026 for UKGE tournaments...
      scheduleYearly24hr(Variant.Go(strategygames.go.variant.Go19x19), Byoyomi510x5)(
        new DateTime(2026, 6, 5, 0, 0)
      ),
      scheduleYearly24hr(Variant.FairySF(strategygames.fairysf.variant.Shogi), Byoyomi510)(
        new DateTime(2026, 6, 12, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.RacingKings), Blitz32)(
        new DateTime(2026, 6, 19, 0, 0)
      ),
      scheduleYearly24hr(Variant.Draughts(strategygames.draughts.variant.Russian), Blitz32)(
        new DateTime(2026, 6, 26, 0, 0)
      ),
      scheduleYearly24hr(Variant.FairySF(strategygames.fairysf.variant.Flipello10), Rapid8)(
        new DateTime(2026, 7, 3, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.NoCastling), Blitz32)(
        new DateTime(2026, 7, 10, 0, 0)
      ),
      scheduleYearly24hr(
        Variant.FairySF(strategygames.fairysf.variant.MiniBreakthroughTroyka),
        Blitz21
      )(
        new DateTime(2026, 7, 17, 0, 0)
      ),
      // no gap for birthday tournament in 2026. Maybe we do it on the actual birthday 21st July.
      scheduleYearly24hr(Variant.Draughts(strategygames.draughts.variant.Frisian), Blitz53)(
        new DateTime(2026, 7, 24, 0, 0)
      ),
      scheduleYearly24hr(Variant.Togyzkumalak(strategygames.togyzkumalak.variant.Togyzkumalak), Blitz52)(
        new DateTime(2026, 7, 31, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.Chess960), Blitz32)(
        new DateTime(2026, 8, 7, 0, 0)
      ),
      scheduleYearly24hr(Variant.Draughts(strategygames.draughts.variant.English), Blitz32)(
        new DateTime(2026, 8, 14, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.ScrambledEggs), Blitz32)(
        new DateTime(2026, 8, 21, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.ThreeCheck), Blitz32)(
        new DateTime(2026, 8, 28, 0, 0)
      ),
      scheduleYearly24hr(Variant.FairySF(strategygames.fairysf.variant.MiniXiangqi), Blitz32)(
        new DateTime(2026, 9, 4, 0, 0)
      ),
      scheduleYearly24hr(Variant.Chess(strategygames.chess.variant.Monster), Blitz32)(
        new DateTime(2026, 9, 11, 0, 0)
      ),
      scheduleYearly24hr(Variant.Abalone(strategygames.abalone.variant.Abalone), Delay62)(
        new DateTime(2026, 9, 18, 0, 0)
      ),
      scheduleYearly24hr(Variant.Backgammon(strategygames.backgammon.variant.Backgammon), Delay1510)(
        new DateTime(2026, 9, 25, 0, 0)
      ),
      scheduleYearly24hr(Variant.Draughts(strategygames.draughts.variant.Standard), Blitz53)(
        new DateTime(2026, 10, 2, 0, 0)
      ),
      scheduleYearly24hr(Variant.Go(strategygames.go.variant.Go9x9), Byoyomi210x5)(
        new DateTime(2026, 10, 9, 0, 0)
      ),
      scheduleYearly24hr(
        Variant.FairySF(strategygames.fairysf.variant.BreakthroughTroyka),
        Blitz32
      )(
        new DateTime(2026, 10, 16, 0, 0)
      ),
      scheduleYearly24hr(Variant.FairySF(strategygames.fairysf.variant.AntiFlipello), Blitz)(
        new DateTime(2026, 10, 23, 0, 0)
      ),
      scheduleYearly24hr(Variant.Togyzkumalak(strategygames.togyzkumalak.variant.Bestemshe), Blitz32)(
        new DateTime(2026, 10, 30, 0, 0)
      ),
      scheduleYearly24hr(Variant.Draughts(strategygames.draughts.variant.Brazilian), Blitz32)(
        new DateTime(2026, 11, 6, 0, 0)
      ),
      scheduleYearly24hr(Variant.FairySF(strategygames.fairysf.variant.OctagonFlipello), Rapid8)(
        new DateTime(2026, 11, 13, 0, 0)
      ),
      scheduleYearly24hr(Variant.Dameo(strategygames.dameo.variant.Dameo), Blitz53)(
        new DateTime(2026, 11, 20, 0, 0)
      ),
      scheduleYearly24hr(Variant.Abalone(strategygames.abalone.variant.GrandAbalone), Delay66)(
        new DateTime(2026, 11, 27, 0, 0)
      )
    ).flatten filter { _.schedule.at.isAfter(rightNow) }

    val scheduledBeforeDailyCycle =
      yearly2026Tournaments :::
        thisWeekMedleyShields :::
        nextWeekMedleyShields :::
        thisMonthMedleyShields :::
        nextMonthMedleyShields :::
        thisMonthShields :::
        nextMonthShields

    // a shield, medley or yearly counts against a day whenever it is running during it,
    // which for the friday 24h yearly means it reaches into saturday's first filler slot
    def runsDuring(day: DateTime)(plan: Plan) = {
      val s = plan.schedule
      s.at.isBefore(day.plusDays(1)) && s.at.plusMinutes(Schedule.durationFor(s)).isAfter(day)
    }

    // daily cycle tournaments, a few weeks in advance
    val dailyCycleTournaments = (0 until dailyCycleDaysAhead).toList.flatMap { daysAhead =>
      val day       = today.plusDays(daysAhead)
      val blocks    = TournamentDailyCycle.blockSlots(day)
      val otherwise = scheduledBeforeDailyCycle.filter(runsDuring(day)).flatMap { p =>
        p.schedule.medleyShield.fold(List(p.schedule.variant))(_.variants)
      }
      val fillers = TournamentDailyCycle.fillerSlots(day, blocks.map(_.variant).toSet ++ otherwise)
      (blocks ::: fillers).flatMap(scheduleDailyCycle(_)(day))
    } filter { _.schedule.at.isAfter(rightNow) }

    // order matters for pruning daily/yearly tournaments
    scheduledBeforeDailyCycle ::: dailyCycleTournaments
  }

//          List( // lichess shield tournaments!
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
            at(date, hour) so { date =>
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
  // ).flatten filter { _.schedule.at isAfter rightNow }

  private[tournament] def pruneConflicts(scheds: List[Tournament], newTourns: List[Tournament]) =
    TournamentScheduler.pruneConflicts(scheds, newTourns)

  private case class ScheduleNowWith(dbScheds: List[Tournament])

  private def at(day: DateTime, hour: Int, minute: Int = 0): Option[DateTime] =
    try
      Some(day.withTimeAtStartOfDay.plusHours(hour).plusMinutes(minute))
    catch {
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
          api.subscribeBotsToArenas).discard
      } catch {
        case e: org.joda.time.IllegalInstantException =>
          logger.error(s"failed to schedule all: ${e.getMessage}")
      }
  }
}

private object TournamentScheduler {

  case object ScheduleNow

  import Schedule.Freq.*

  private[tournament] def pruneConflicts(scheds: List[Tournament], newTourns: List[Tournament]) =
    newTourns
      .foldLeft(List[Tournament]()) { case (tourns, t) =>
        if (overlaps(t, tourns) || overlaps(t, scheds)) tourns
        else t :: tourns
      }
      .reverse

  private[tournament] def overlaps(t: Tournament, ts: List[Tournament]): Boolean =
    t.schedule exists { s =>
      ts exists { t2 =>
        t2.schedule.so { s2 =>
          // Daily cycle and yearly tournaments own their slot outright
          if (s.freq == DailyCycle && s2.freq == DailyCycle) t.overlaps(t2)
          else if (s.freq == Yearly && s2.freq == Yearly) s.sameDay(s2)
          else
            ((!t.isMedley && !t2.isMedley && t.variant == t2.variant) ||
              (t.isMedley && t2.isMedley && t.trophy1st == t2.trophy1st)) && (s2 match {
              // dont let yearly's block shields and vice versa
              case _ if s.freq == Shield || s.freq == MedleyShield   => s2.freq == s.freq && (s.sameDay(s2))
              case _ if s2.freq == Shield || s2.freq == MedleyShield => false
              case _                                                 =>
                (
                  t.variant.exotic ||  // overlapping exotic variant
                    s.hasMaxRating ||  // overlapping same rating limit
                    s.similarSpeed(s2) // overlapping similar
                ) && s.similarConditions(s2) && t.overlaps(t2)
            })
        }
      }
    }
}
