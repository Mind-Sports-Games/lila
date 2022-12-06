package lila.tournament

import strategygames.format.FEN
import strategygames.variant.Variant
import strategygames.GameLogic
import strategygames.Clock
import org.joda.time.DateTime
import play.api.i18n.Lang

import lila.rating.PerfType
import lila.i18n.VariantKeys

case class Schedule(
    freq: Schedule.Freq,
    speed: Schedule.Speed,
    variant: Variant,
    position: Option[FEN],
    at: DateTime,
    duration: Option[Int] = None,
    medleyShield: Option[TournamentShield.MedleyShield] = None,
    conditions: Condition.All = Condition.All.empty
) {

  // Simpler naming for now.
  def name(full: Boolean = true)(implicit lang: Lang): String = {
    s"${VariantKeys.variantName(variant)} Mind Sports Olympiad Warm-up"
    /*
    import Schedule.Freq._
    import Schedule.Speed._
    import lila.i18n.I18nKeys.tourname._
    if (variant.standard && position.isEmpty)
      (conditions.minRating, conditions.maxRating) match {
        case (None, None) =>
          (freq, speed) match {
            case (Hourly, Rapid) if full      => hourlyRapidArena.txt()
            case (Hourly, Rapid)              => hourlyRapid.txt()
            case (Hourly, speed) if full      => hourlyXArena.txt(speed.name)
            case (Hourly, speed)              => hourlyX.txt(speed.name)
            case (Daily, Rapid) if full       => dailyRapidArena.txt()
            case (Daily, Rapid)               => dailyRapid.txt()
            case (Daily, Classical) if full   => dailyClassicalArena.txt()
            case (Daily, Classical)           => dailyClassical.txt()
            case (Daily, speed) if full       => dailyXArena.txt(speed.name)
            case (Daily, speed)               => dailyX.txt(speed.name)
            case (Eastern, Rapid) if full     => easternRapidArena.txt()
            case (Eastern, Rapid)             => easternRapid.txt()
            case (Eastern, Classical) if full => easternClassicalArena.txt()
            case (Eastern, Classical)         => easternClassical.txt()
            case (Eastern, speed) if full     => easternXArena.txt(speed.name)
            case (Eastern, speed)             => easternX.txt(speed.name)
            case (Weekly, Rapid) if full      => weeklyRapidArena.txt()
            case (Weekly, Rapid)              => weeklyRapid.txt()
            case (Weekly, Classical) if full  => weeklyClassicalArena.txt()
            case (Weekly, Classical)          => weeklyClassical.txt()
            case (Weekly, speed) if full      => weeklyXArena.txt(speed.name)
            case (Weekly, speed)              => weeklyX.txt(speed.name)
            case (Monthly, Rapid) if full     => monthlyRapidArena.txt()
            case (Monthly, Rapid)             => monthlyRapid.txt()
            case (Monthly, Classical) if full => monthlyClassicalArena.txt()
            case (Monthly, Classical)         => monthlyClassical.txt()
            case (Monthly, speed) if full     => monthlyXArena.txt(speed.name)
            case (Monthly, speed)             => monthlyX.txt(speed.name)
            case (Yearly, Rapid) if full      => yearlyRapidArena.txt()
            case (Yearly, Rapid)              => yearlyRapid.txt()
            case (Yearly, Classical) if full  => yearlyClassicalArena.txt()
            case (Yearly, Classical)          => yearlyClassical.txt()
            case (Yearly, speed) if full      => yearlyXArena.txt(speed.name)
            case (Yearly, speed)              => yearlyX.txt(speed.name)
            case (Shield, Rapid) if full      => rapidShieldArena.txt()
            case (Shield, Rapid)              => rapidShield.txt()
            case (Shield, Classical) if full  => classicalShieldArena.txt()
            case (Shield, Classical)          => classicalShield.txt()
            case (Shield, speed) if full      => xShieldArena.txt(speed.name)
            case (Shield, speed)              => xShield.txt(speed.name)
            case _ if full                    => xArena.txt(s"${freq.toString} ${speed.name}")
            case _                            => s"${freq.toString} ${speed.name}"
          }
        case (Some(_), _) if full   => eliteXArena.txt(speed.name)
        case (Some(_), _)           => eliteX.txt(speed.name)
        case (_, Some(max)) if full => s"<${max.rating} ${xArena.txt(speed.name)}"
        case (_, Some(max))         => s"<${max.rating} ${speed.name}"
      }
    else if (variant.standard) {
      val n = position.flatMap(Thematic.byFen).fold(speed.name) { pos =>
        s"${pos.shortName} ${speed.name}"
      }
      if (full) xArena.txt(n) else n
    } else
      freq match {
        case Hourly if full  => hourlyXArena.txt(VariantKeys.variantName(variant))
        case Hourly          => hourlyX.txt(VariantKeys.variantName(variant))
        case Daily if full   => dailyXArena.txt(VariantKeys.variantName(variant))
        case Daily           => dailyX.txt(VariantKeys.variantName(variant))
        case Eastern if full => easternXArena.txt(VariantKeys.variantName(variant))
        case Eastern         => easternX.txt(VariantKeys.variantName(variant))
        case Weekly if full  => weeklyXArena.txt(VariantKeys.variantName(variant))
        case Weekly          => weeklyX.txt(VariantKeys.variantName(variant))
        case Monthly if full => monthlyXArena.txt(VariantKeys.variantName(variant))
        case Monthly         => monthlyX.txt(VariantKeys.variantName(variant))
        case Yearly if full  => yearlyXArena.txt(VariantKeys.variantName(variant))
        case Yearly          => yearlyX.txt(VariantKeys.variantName(variant))
        case Shield if full  => xShieldArena.txt(VariantKeys.variantName(variant))
        case Shield          => xShield.txt(VariantKeys.variantName(variant))
        case _ =>
          val n = s"${freq.name} ${VariantKeys.variantName(variant)}"
          if (full) xArena.txt(n) else n
      }*/
  }

  def day = at.withTimeAtStartOfDay

  def sameSpeed(other: Schedule) = speed == other.speed

  def similarSpeed(other: Schedule) = Schedule.Speed.similar(speed, other.speed)

  def sameVariant(other: Schedule) =
    (variant.id == other.variant.id) && (variant.gameLogic.id == other.variant.gameLogic.id)

  def sameVariantAndSpeed(other: Schedule) = sameVariant(other) && sameSpeed(other)

  def sameFreq(other: Schedule) = freq == other.freq

  def sameConditions(other: Schedule) = conditions == other.conditions

  def sameMaxRating(other: Schedule) = conditions sameMaxRating other.conditions

  def similarConditions(other: Schedule) = conditions similar other.conditions

  def sameDay(other: Schedule) = day == other.day

  def hasMaxRating = conditions.maxRating.isDefined

  def similarTo(other: Schedule) =
    similarSpeed(other) && sameVariant(other) && sameFreq(other) && sameConditions(other)

  def perfType = PerfType.byVariant(variant) | Schedule.Speed.toPerfType(speed)

  def plan                                  = Schedule.Plan(this, None)
  def plan(build: Tournament => Tournament) = Schedule.Plan(this, build.some)

  override def toString = s"$freq $variant $speed $conditions $at"
}

object Schedule {

  def uniqueFor(tour: Tournament) =
    Schedule(
      freq = Freq.Unique,
      speed = Speed fromClock tour.clock,
      variant = tour.variant,
      position = tour.position,
      at = tour.startsAt
    )

  case class Plan(schedule: Schedule, buildFunc: Option[Tournament => Tournament]) {

    def build: Tournament = {
      val t = Tournament.scheduleAs(addCondition(schedule), durationFor(schedule))
      buildFunc.foldRight(t) { _(_) }
    }

    def map(f: Tournament => Tournament) =
      copy(
        buildFunc = buildFunc.fold(f)(f.compose).some
      )
  }

  sealed abstract class Freq(val id: Int, val importance: Int) extends Ordered[Freq] {

    val name    = toString.toLowerCase
    val display = toString

    def compare(other: Freq) = Integer.compare(importance, other.importance)

    def isDaily          = this == Schedule.Freq.Daily
    def isDailyOrBetter  = this >= Schedule.Freq.Daily
    def isWeeklyOrBetter = this >= Schedule.Freq.Weekly
  }
  object Freq {
    case object Hourly extends Freq(10, 10)
    case object Daily  extends Freq(20, 20)
    //case object Eastern  extends Freq(30, 15)
    case object Weekly       extends Freq(40, 40)
    case object Weekend      extends Freq(41, 41)
    case object Monthly      extends Freq(50, 50)
    case object Shield       extends Freq(51, 51)
    case object MedleyShield extends Freq(52, 52)
    case object Marathon     extends Freq(60, 60)
    case object ExperimentalMarathon extends Freq(61, 55) { // for DB BC
      override val display = "Experimental Marathon"
    }
    case object Yearly       extends Freq(70, 70)
    case object Introductory extends Freq(80, 65)
    case object Unique       extends Freq(90, 59)
    case object MSOWarmUp extends Freq(120, 41) {
      override val display = "MSO Warm-Up"
    }
    case object MSO21 extends Freq(121, 61) {
      override val display = "MSO 2021"
    }
    case object MSOGP extends Freq(122, 75) {
      override val display = "MSO Grand Prix"
    }

    val all: List[Freq] = List(
      Hourly,
      Daily,
      //Eastern,
      Weekly,
      Weekend,
      Monthly,
      Shield,
      MedleyShield,
      Marathon,
      ExperimentalMarathon,
      Yearly,
      Introductory,
      Unique,
      MSOWarmUp,
      MSO21,
      MSOGP
    )
    val shields: List[Freq] = List(Shield, MedleyShield)

    def apply(name: String) = all.find(_.name == name)
    def byId(id: Int)       = all.find(_.id == id)
  }

  sealed abstract class Speed(val id: Int) {
    val name = s"${toString} Chess"
    val key  = lila.common.String lcfirst name
  }
  object Speed {
    case object UltraBullet extends Speed(5)
    case object HyperBullet extends Speed(10)
    case object Bullet      extends Speed(20)
    case object HippoBullet extends Speed(25)
    case object SuperBlitz  extends Speed(30)
    case object Blitz       extends Speed(40)
    case object Rapid       extends Speed(50)
    case object Classical   extends Speed(60)
    case object Blitz32     extends Speed(70)
    case object Blitz35     extends Speed(75)
    case object Blitz51     extends Speed(80)
    case object Blitz53     extends Speed(85)
    val all: List[Speed] =
      List(
        UltraBullet,
        HyperBullet,
        Bullet,
        HippoBullet,
        SuperBlitz,
        Blitz,
        Blitz32,
        Blitz35,
        Blitz51,
        Blitz53,
        Rapid,
        Classical
      )
    val mostPopular: List[Speed] = List(Bullet, Blitz, Rapid, Classical)
    def apply(key: String)       = all.find(_.key == key) orElse all.find(_.key.toLowerCase == key.toLowerCase)
    def byId(id: Int)            = all find (_.id == id)
    def similar(s1: Speed, s2: Speed) =
      (s1, s2) match {
        case (a, b) if a == b                                        => true
        case (Bullet, HippoBullet) | (HippoBullet, Bullet)           => true
        case (HyperBullet, UltraBullet) | (UltraBullet, HyperBullet) => true
        case _                                                       => false
      }
    def fromClock(clock: Clock.Config) = {
      val time = clock.estimateTotalSeconds
      if (time < 30) UltraBullet
      else if (time < 60) HyperBullet
      else if (time < 120) Bullet
      else if (time < 180) HippoBullet
      else if (time < 480) Blitz
      else if (time < 1500) Rapid
      else Classical
    }
    def toPerfType(speed: Speed) =
      speed match {
        case UltraBullet                        => PerfType.orDefaultSpeed("ultraBullet")
        case HyperBullet | Bullet | HippoBullet => PerfType.orDefaultSpeed("bullet")
        case SuperBlitz | Blitz | Blitz32 | Blitz35 | Blitz51 | Blitz53 =>
          PerfType.orDefaultSpeed("blitz")
        case Rapid     => PerfType.orDefaultSpeed("rapid")
        case Classical => PerfType.orDefaultSpeed("classical")
      }
  }

  sealed trait Season
  object Season {
    case object Spring extends Season
    case object Summer extends Season
    case object Autumn extends Season
    case object Winter extends Season
  }

  private val defaultDuration: Int = 55

  private[tournament] def durationFor(s: Schedule): Int = s.duration match {
    case Some(duration) => duration
    case None           => defaultDuration
  }
  /*{
    import Freq._, Speed._
    import strategygames.chess.variant._

    (s.freq, s.variant, s.speed) match {

      case (Hourly, _, UltraBullet | HyperBullet | Bullet)                   => 27
      case (Hourly, _, HippoBullet | SuperBlitz | Blitz | Blitz32 | Blitz51 | Blitz53) => 57
      case (Hourly, _, Rapid) if s.hasMaxRating                              => 57
      case (Hourly, _, Rapid | Classical)                                    => 117

      case (Daily | Eastern, Standard, SuperBlitz)                => 90
      case (Daily | Eastern, Standard, Blitz | Blitz32 | Blitz51 | Blitz53) => 120
      case (Daily | Eastern, _, Blitz | Blitz32 | Blitz51)        => 90
      case (Daily | Eastern, _, Rapid | Classical)                => 150
      case (Daily | Eastern, _, _)                                => 60

      case (Weekly, _, UltraBullet | HyperBullet | Bullet)                   => 60 * 2
      case (Weekly, _, HippoBullet | SuperBlitz | Blitz | Blitz32 | Blitz51 | Blitz53) => 60 * 3
      case (Weekly, _, Rapid)                                                => 60 * 4
      case (Weekly, _, Classical)                                            => 60 * 5

      case (Weekend, Crazyhouse, _)                         => 60 * 2
      case (Weekend, _, UltraBullet | HyperBullet | Bullet) => 90
      case (Weekend, _, HippoBullet | SuperBlitz)           => 60 * 2
      case (Weekend, _, Blitz | Blitz32 | Blitz51 | Blitz53)          => 60 * 3
      case (Weekend, _, Rapid)                              => 60 * 4
      case (Weekend, _, Classical)                          => 60 * 5

      case (Monthly, _, UltraBullet)               => 60 * 2
      case (Monthly, _, HyperBullet | Bullet)      => 60 * 3
      case (Monthly, _, HippoBullet | SuperBlitz)  => 60 * 3 + 30
      case (Monthly, _, Blitz | Blitz32 | Blitz51 | Blitz53) => 60 * 4
      case (Monthly, _, Rapid)                     => 60 * 5
      case (Monthly, _, Classical)                 => 60 * 6

      case (Shield, _, UltraBullet)               => 60 * 3
      case (Shield, _, HyperBullet | Bullet)      => 60 * 4
      case (Shield, _, HippoBullet | SuperBlitz)  => 60 * 5
      case (Shield, _, Blitz | Blitz32 | Blitz51 | Blitz53) => 60 * 6
      case (Shield, _, Rapid)                     => 60 * 8
      case (Shield, _, Classical)                 => 60 * 10

      case (Yearly, _, UltraBullet | HyperBullet | Bullet) => 60 * 4
      case (Yearly, _, HippoBullet | SuperBlitz)           => 60 * 5
      case (Yearly, _, Blitz | Blitz32 | Blitz51 | Blitz53)          => 60 * 6
      case (Yearly, _, Rapid)                              => 60 * 8
      case (Yearly, _, Classical)                          => 60 * 10

      case (Marathon, _, _)             => 60 * 24 // lol
      case (ExperimentalMarathon, _, _) => 60 * 4

      case (Unique, _, _) => 60 * 6
    }
  }*/

  private val standardIncHours         = Set(1, 7, 13, 19)
  private def standardInc(s: Schedule) = standardIncHours(s.at.getHourOfDay)
  private def zhInc(s: Schedule)       = s.at.getHourOfDay % 2 == 0

  private def zhEliteTc(s: Schedule) = {
    val TC = Clock.Config
    s.at.getDayOfMonth / 7 match {
      case 0 => TC(3 * 60, 0)
      case 1 => TC(1 * 60, 1)
      case 2 => TC(3 * 60, 2)
      case 3 => TC(1 * 60, 0)
      case _ => TC(2 * 60, 0) // for the sporadic 5th Saturday
    }
  }

  private[tournament] def clockFor(s: Schedule) = {
    import Freq._, Speed._
    import strategygames.chess.variant._

    val TC = Clock.Config

    (s.freq, s.variant, s.speed) match {
      // Special cases.
      case (Weekend, strategygames.variant.Variant.Chess(Crazyhouse), Blitz)                 => zhEliteTc(s)
      case (Hourly, strategygames.variant.Variant.Chess(Crazyhouse), SuperBlitz) if zhInc(s) => TC(3 * 60, 1)
      case (Hourly, strategygames.variant.Variant.Chess(Crazyhouse), Blitz) if zhInc(s)      => TC(4 * 60, 2)
      case (Hourly, strategygames.variant.Variant.Chess(Standard), Blitz) if standardInc(s)  => TC(3 * 60, 2)

      case (Shield, variant, Blitz) if variant.exotic => TC(3 * 60, 2)

      case (_, _, UltraBullet) => TC(15, 0)
      case (_, _, HyperBullet) => TC(30, 0)
      case (_, _, Bullet)      => TC(60, 0)
      case (_, _, HippoBullet) => TC(2 * 60, 0)
      case (_, _, SuperBlitz)  => TC(3 * 60, 0)
      case (_, _, Blitz)       => TC(5 * 60, 0)
      case (_, _, Blitz32)     => TC(3 * 60, 2)
      case (_, _, Blitz35)     => TC(3 * 60, 5)
      case (_, _, Blitz51)     => TC(5 * 60, 1)
      case (_, _, Blitz53)     => TC(5 * 60, 3)
      case (_, _, Rapid)       => TC(10 * 60, 0)
      case (_, _, Classical)   => TC(20 * 60, 10)
    }
  }
  private[tournament] def addCondition(s: Schedule) =
    s.copy(conditions = conditionFor(s))

  private[tournament] def conditionFor(s: Schedule) =
    if (s.conditions.relevant) s.conditions
    else {
      import Freq._, Speed._

      // No rated games required, because no-one has them.
      val nbRatedGame = 0 /*(s.freq, s.speed) match {

        case (Hourly | Daily | Eastern, HyperBullet | Bullet)             => 20
        case (Hourly | Daily | Eastern, HippoBullet | SuperBlitz | Blitz) => 15
        case (Hourly | Daily | Eastern, Rapid)                            => 10

        case (Weekly | Weekend | Monthly | Shield, HyperBullet | Bullet)             => 30
        case (Weekly | Weekend | Monthly | Shield, HippoBullet | SuperBlitz | Blitz) => 20
        case (Weekly | Weekend | Monthly | Shield, Rapid)                            => 15

        case _ => 0
      }*/

      // No min rating for the same reason.
      val minRating = 0 /*(s.freq, s.variant) match {
        case (Weekend, strategygames.chess.variant.Crazyhouse) => 2100
        case (Weekend, _)                        => 2200
        case _                                   => 0
      }*/

      Condition.All(
        nbRatedGame = nbRatedGame.some.filter(0 <).map {
          Condition.NbRatedGame(s.perfType.some, _)
        },
        minRating = minRating.some.filter(0 <).map {
          Condition.MinRating(s.perfType, _)
        },
        maxRating = none,
        titled = none,
        teamMember = none
      )
    }
}
