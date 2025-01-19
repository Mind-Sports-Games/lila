package lila.swiss

import ornicar.scalalib.Zero

import strategygames.format.FEN
import strategygames.{ ByoyomiClock, Clock, ClockConfig, GameFamily, GameGroup, Speed }
import strategygames.variant.Variant
import org.joda.time.DateTime
import scala.concurrent.duration._

import lila.hub.LightTeam.TeamID
import lila.rating.PerfType
import lila.user.User
import lila.game.Handicaps
import lila.i18n.VariantKeys

case class Swiss(
    _id: Swiss.Id,
    name: String,
    clock: ClockConfig,
    variant: Variant,
    round: SwissRound.Number, // ongoing round
    nbPlayers: Int,
    nbOngoing: Int,
    createdAt: DateTime,
    createdBy: User.ID,
    teamId: TeamID,
    startsAt: DateTime,
    settings: Swiss.Settings,
    nextRoundAt: Option[DateTime],
    finishedAt: Option[DateTime],
    winnerId: Option[User.ID] = None,
    trophy1st: Option[String] = None,
    trophy2nd: Option[String] = None,
    trophy3rd: Option[String] = None,
    trophyExpiryDays: Option[Int] = None
) {
  def id = _id

  def isCreated          = round.value == 0
  def isStarted          = !isCreated && !isFinished
  def isFinished         = finishedAt.isDefined
  def isNotFinished      = !isFinished
  def isNowOrSoon        = startsAt.isBefore(DateTime.now plusMinutes 15) && !isFinished
  def isRecentlyFinished = finishedAt.exists(f => (nowSeconds - f.getSeconds) < 30 * 60)
  def isEnterable =
    isNotFinished && round.value <= settings.nbRounds / 2 && nbPlayers < Swiss.maxPlayers && settings.minutesBeforeStartToJoin
      .fold(true)(mbs =>
        DateTime.now
          .isAfter(startsAt minusMinutes mbs)
      )
  def isHalfway = round.value == (settings.nbRounds + 1) / 2
  def isMedley  = settings.medleyVariants.nonEmpty

  def allRounds: List[SwissRound.Number]      = (1 to round.value).toList.map(SwissRound.Number.apply)
  def finishedRounds: List[SwissRound.Number] = (1 until round.value).toList.map(SwissRound.Number.apply)
  def tieBreakRounds: List[SwissRound.Number] = if (isFinished) allRounds
  else (1 until ((round.value + 1) atMost settings.nbRounds)).toList.map(SwissRound.Number.apply)
  def allAcceleratedRounds: List[SwissRound.Number] = if (isFinished) allRounds
  else (1 to ((round.value + 1) atMost settings.nbRounds)).toList.map(SwissRound.Number.apply)

  def actualNbRounds = if (isFinished) round.value else settings.nbRounds

  def startRound =
    copy(
      round = SwissRound.Number(round.value + 1),
      nextRoundAt = none
    )

  def speed = Speed(clock)

  def perfType: PerfType = PerfType(variant, speed)

  def estimatedDuration: FiniteDuration = clock match {
    case bc: ByoyomiClock.Config => {
      ((bc.limitSeconds + bc.incrementSeconds * 80 + bc.byoyomiSeconds * 20 * bc.periodsTotal + 10) * settings.nbRounds).toInt.seconds
    }
    case bc: Clock.BronsteinConfig => {
      ((bc.limitSeconds + bc.delaySeconds * 80 + 10) * settings.nbRounds).toInt.seconds
    }
    case udc: Clock.SimpleDelayConfig => {
      ((udc.limitSeconds + udc.delaySeconds * 80 + 10) * settings.nbRounds).toInt.seconds
    }
    case fc: Clock.Config => {
      ((fc.limitSeconds + fc.incrementSeconds * 80 + 10) * settings.nbRounds).toInt.seconds
    }
  }

  def estimatedDurationString = {
    val minutes = estimatedDuration.toMinutes
    if (minutes < 60) s"${minutes}m"
    else s"${minutes / 60}h" + (if (minutes % 60 != 0) s" ${minutes % 60}m" else "")
  }

  def roundInfo = Swiss.RoundInfo(teamId, settings.chatFor)

  def betweenRounds = nextRoundAt.nonEmpty

  def roundVariant = variantForRound(round.value + (if (betweenRounds) 1 else 0))

  def variantForRound(roundIndex: Int) =
    settings.medleyVariants.getOrElse(List()).lift(roundIndex - 1).getOrElse(variant)

  def roundPerfType: PerfType = PerfType(roundVariant, speed)

  def medleyGameGroups: Option[List[GameGroup]] =
    settings.medleyVariants.map(mvList =>
      GameGroup.medley.filter(gg => gg.variants.exists(mvList.contains(_))).distinct.sortWith(_.name < _.name)
    )

  def medleyGameFamilies: Option[List[GameFamily]] =
    settings.medleyVariants.map(
      _.map(_.gameFamily).distinct.sortWith(_.name < _.name)
    )

  def medleyGameGroupsString: Option[String] =
    medleyGameGroups.map(_.map(VariantKeys.gameGroupName).mkString(", "))

  def mainGameFamily: Option[GameFamily] =
    if (isMedley) {
      val firstGameFamily = medleyGameFamilies.flatMap(_.headOption)
      if (firstGameFamily.toList.some == medleyGameFamilies) firstGameFamily
      else None
    } else variant.gameFamily.some

  def withConditions(conditions: SwissCondition.All) = copy(
    settings = settings.copy(conditions = conditions)
  )

  def unrealisticSettings =
    !settings.manualRounds &&
      settings.dailyInterval.isEmpty &&
      clock.estimateTotalSeconds * 2 * settings.nbRounds > 3600 * 8

  lazy val looksLikePrize = lila.common.String.looksLikePrize(s"$name ${~settings.description}")

  lazy val trophies = List(trophy1st, trophy2nd, trophy3rd)
}

object Swiss {

  val maxPlayers = 4000

  case class Id(value: String) extends AnyVal with StringValue
  case class Round(value: Int) extends AnyVal with IntValue

  case class Points(double: Int) extends AnyVal {
    def value: Float = double / 2f
    def +(p: Points) = Points(double + p.double)
    def -(p: Points) = Points(double - p.double)
  }
  trait TieBreak extends Any {
    def value: Double
  }
  case class SonnenbornBerger(val value: Double) extends AnyVal with TieBreak
  case class Buchholz(val value: Double)         extends AnyVal with TieBreak
  case class Performance(value: Float)           extends AnyVal
  case class Score(value: Long)                  extends AnyVal

  implicit val SonnenbornBergerZero: Zero[SonnenbornBerger] = Zero.instance(SonnenbornBerger(0))
  implicit val BuchholzZero: Zero[Buchholz]                 = Zero.instance(Buchholz(0))

  case class IdName(_id: Id, name: String) {
    def id = _id
  }

  case class Settings(
      nbRounds: Int,
      rated: Boolean,
      mcmahon: Boolean,
      mcmahonCutoff: String,
      handicapped: Boolean,
      backgammonPoints: Option[Int] = None,
      inputPlayerRatings: String,
      isMatchScore: Boolean,
      isBestOfX: Boolean,
      isPlayX: Boolean,
      nbGamesPerRound: Int,
      description: Option[String] = None,
      useDrawTables: Boolean,
      usePerPairingDrawTables: Boolean,
      position: Option[FEN],
      chatFor: ChatFor = ChatFor.default,
      password: Option[String] = None,
      conditions: SwissCondition.All,
      roundInterval: FiniteDuration,
      halfwayBreak: FiniteDuration,
      forbiddenPairings: String,
      medleyVariants: Option[List[Variant]] = None,
      minutesBeforeStartToJoin: Option[Int] = None
  ) {
    lazy val intervalSeconds = roundInterval.toSeconds.toInt
    lazy val timeBeforeStartToJoin: Option[String] = minutesBeforeStartToJoin.map(m =>
      if (m < 60) s"$m minutes"
      else if (m < 24 * 60) s"${m / 60} hour${if (m == 60) "" else "s"}"
      else s"${m / 24 / 60} day${if (m == 24 * 60) "" else "s"}"
    )
    lazy val halfwayBreakText: Option[String] = (halfwayBreak.toSeconds.toInt match {
      case 0                  => None
      case s if s < 60        => Some(s"$s seconds")
      case s if s < 3600      => Some(s"${s / 60} minute${if (s == 60) "" else "s"}")
      case s if s < 24 * 3600 => Some(s"${s / 3600} hour${if (s == 60 * 60) "" else "s"}")
      case s                  => Some(s"${s / 24 / 3600} day${if (s == 24 * 60 * 60) "" else "s"}")
    }).map(s => s"${s} break after round ${halfwayBreakRound}")
    lazy val halfwayBreakRound  = (nbRounds + 1) / 2
    def manualRounds            = intervalSeconds == Swiss.RoundInterval.manual
    def dailyInterval           = (!manualRounds && intervalSeconds >= 24 * 3600) option intervalSeconds / 3600 / 24
    def usingDrawTables         = useDrawTables || usePerPairingDrawTables
    def mcmahonCutoffGrade: Int = Handicaps.playerRatingFromInput(mcmahonCutoff).getOrElse(1500)
  }

  type ChatFor = Int
  object ChatFor {
    val NONE    = 0
    val LEADERS = 10
    val MEMBERS = 20
    val ALL     = 30
    val default = MEMBERS
  }

  object RoundInterval {
    val auto   = -1
    val manual = 99999999
  }

  object TimeBeforeStartToJoin {
    val nolimit = -1
  }

  def makeScore(
      points: Points,
      buchholz: Buchholz,
      sonnenbornBerger: SonnenbornBerger,
      perf: Performance
  ) = {
    val b  = SwissBounds
    val wb = b.WithBounds
    Score(
      b.encodeIntoLong(
        wb.performance(perf.value),
        wb.sonnenbornBerger(sonnenbornBerger.value),
        wb.buchholz(buchholz.value),
        wb.score(points.double)
      )
    )
  }

  def makeId = Id(lila.common.ThreadLocalRandom nextString 8)

  case class PastAndNext(past: List[Swiss], next: List[Swiss])

  case class RoundInfo(
      teamId: TeamID,
      chatFor: ChatFor
  )

  def swissUrl(swissId: Swiss.Id): String = s"https://playstrategy.org/swiss/${swissId.value}"

}
