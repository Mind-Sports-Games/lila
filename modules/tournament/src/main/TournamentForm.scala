package lila.tournament

import cats.implicits._
import strategygames.format.FEN
import strategygames.chess.{ StartingPosition }
import strategygames.{ ByoyomiClock, ClockConfig, FischerClock, GameFamily, GameGroup, GameLogic, Mode }
import strategygames.variant.Variant
import org.joda.time.DateTime
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation
import play.api.data.validation.Constraint

import lila.common.Form._
import lila.hub.LeaderTeam
import lila.hub.LightTeam._
import lila.user.User

final class TournamentForm {

  import TournamentForm._

  def create(user: User, leaderTeams: List[LeaderTeam], teamBattleId: Option[TeamID] = None) =
    form(user, leaderTeams) fill TournamentSetup(
      name = teamBattleId.isEmpty option user.titleUsername,
      clock = FischerClock.Config(180, 0),
      minutes = minuteDefault,
      waitMinutes = waitMinuteDefault.some,
      startDate = none,
      variant = s"${GameFamily.Chess().id}_${Variant.default(GameLogic.Chess()).id}".some,
      medley = false.some,
      medleyIntervalOptions = MedleyIntervalOptions(
        medleyMinutes = medleyMinutesDefault.some,
        balanceIntervals = true.some,
        numIntervals = Some(5)
      ),
      medleyDefaults = MedleyDefaults(
        onePerGameFamily = false.some,
        exoticChessVariants = false.some,
        draughts64Variants = false.some
      ),
      medleyGameFamilies = MedleyGameFamilies(
        chess = true.some,
        draughts = true.some,
        shogi = true.some,
        xiangqi = true.some,
        loa = true.some,
        flipello = true.some,
        mancala = true.some,
        amazons = true.some
      ),
      position = None,
      password = None,
      mode = none,
      rated = true.some,
      conditions = Condition.DataForm.AllSetup.default,
      teamBattleByTeam = teamBattleId,
      berserkable = true.some,
      streakable = true.some,
      description = none,
      hasChat = true.some
    )

  def edit(user: User, leaderTeams: List[LeaderTeam], tour: Tournament) =
    form(user, leaderTeams) fill TournamentSetup(
      name = tour.name.some,
      clock = tour.clock,
      minutes = if (tour.isMedley) tour.medleyDurationMinutes else tour.minutes,
      waitMinutes = none,
      startDate = tour.startsAt.some,
      variant = s"${tour.variant.gameFamily.id}_${tour.variant.id}".some,
      medley = tour.isMedley.some,
      medleyIntervalOptions = MedleyIntervalOptions(
        medleyMinutes = tour.medleyMinutes,
        balanceIntervals = tour.medleyIsBalanced,
        numIntervals = tour.medleyNumIntervals
      ),
      medleyDefaults = MedleyDefaults(
        onePerGameFamily = onePerGameGroupInMedley(tour.medleyVariants).some,
        exoticChessVariants = exoticChessVariants(tour.medleyVariants).some,
        draughts64Variants = draughts64Variants(tour.medleyVariants).some
      ),
      medleyGameFamilies = MedleyGameFamilies(
        chess = gameGroupInMedley(tour.medleyVariants, GameGroup.Chess()).some,
        draughts = gameGroupInMedley(tour.medleyVariants, GameGroup.Draughts()).some,
        shogi = gameGroupInMedley(tour.medleyVariants, GameGroup.Shogi()).some,
        xiangqi = gameGroupInMedley(tour.medleyVariants, GameGroup.Xiangqi()).some,
        loa = gameGroupInMedley(tour.medleyVariants, GameGroup.LinesOfAction()).some,
        flipello = gameGroupInMedley(tour.medleyVariants, GameGroup.Flipello()).some,
        mancala = gameGroupInMedley(tour.medleyVariants, GameGroup.Mancala()).some,
        amazons = gameGroupInMedley(tour.medleyVariants, GameGroup.Amazons()).some
      ),
      position = tour.position,
      mode = none,
      rated = tour.mode.rated.some,
      password = tour.password,
      conditions = Condition.DataForm.AllSetup(tour.conditions),
      teamBattleByTeam = none,
      berserkable = tour.berserkable.some,
      streakable = tour.streakable.some,
      description = tour.description,
      hasChat = tour.hasChat.some
    )

  private val blockList = List("playstrategy", "lichess")

  private def nameType(user: User) = eventName(2, 36).verifying(
    Constraint[String] { (t: String) =>
      if (blockList.exists(t.toLowerCase.contains) && !user.isVerified && !user.isAdmin)
        validation.Invalid(validation.ValidationError("Must not contain \"playstrategy\""))
      else validation.Valid
    }
  )

  private def medleyVariantsList(medleyVariants: Option[List[Variant]]) =
    medleyVariants.getOrElse(List[Variant]())

  private def gameGroupInMedley(medleyVariants: Option[List[Variant]], gg: GameGroup) =
    gg.variants.exists(medleyVariantsList(medleyVariants).contains(_))

  private def onePerGameGroupInMedley(medleyVariants: Option[List[Variant]]) = {
    val mvList               = medleyVariantsList(medleyVariants)
    val gameGroups           = GameGroup.medley.filter(gg => gg.variants.exists(mvList.contains(_)))
    val selectedMVList       = mvList.take(gameGroups.size)
    val gameGroupsInSelected = gameGroups.filter(gg => gg.variants.exists(selectedMVList.contains(_)))
    gameGroups.size > 1 && gameGroups.size == gameGroupsInSelected.size
  }

  private def exoticChessVariants(medleyVariants: Option[List[Variant]]) =
    medleyVariantsList(medleyVariants).filterNot(_.exoticChessVariant).isEmpty

  private def draughts64Variants(medleyVariants: Option[List[Variant]]) =
    medleyVariantsList(medleyVariants).filterNot(_.draughts64Variant).isEmpty

  // Yes, I know this is kinda gross. :'(
  private def valuesFromClockConfig(
      c: ClockConfig
  ): Option[(Boolean, Double, Int, Option[Int], Option[Int])] =
    c match {
      case fc: FischerClock.Config => {
        FischerClock.Config.unapply(fc).map(t => (false, t._1 / 4d, t._2, None, None))
      }
      case bc: ByoyomiClock.Config => {
        ByoyomiClock.Config.unapply(bc).map(t => (true, t._1 / 4d, t._2, Some(t._3), Some(t._4)))
      }
    }

  // Yes, I know this is kinda gross. :'(
  private def clockConfigFromValues(
      useByoyomi: Boolean,
      limit: Double,
      increment: Int,
      byoyomi: Option[Int],
      periods: Option[Int]
  ): ClockConfig =
    (useByoyomi, byoyomi, periods) match {
      case (true, Some(byoyomi), Some(periods)) =>
        ByoyomiClock.Config((limit * 60).toInt, increment, byoyomi, periods)
      case _ =>
        FischerClock.Config((limit * 60).toInt, increment)
    }

  private def form(user: User, leaderTeams: List[LeaderTeam]) =
    Form(
      mapping(
        "name" -> optional(nameType(user)),
        "clock" -> mapping[ClockConfig, Boolean, Double, Int, Option[Int], Option[Int]](
          "useByoyomi" -> boolean,
          "limit"      -> numberInDouble(clockTimeChoices),
          "increment"  -> numberIn(clockIncrementChoices),
          "byoyomi"    -> optional(numberIn(clockByoyomiChoices)),
          "periods"    -> optional(numberIn(periodsChoices))
        )(clockConfigFromValues)(valuesFromClockConfig)
          .verifying("Invalid clock", _.estimateTotalSeconds > 0),
        "minutes" -> {
          if (lila.security.Granter(_.ManageTournament)(user)) number
          else numberIn(minuteChoices)
        },
        "waitMinutes" -> optional(numberIn(waitMinuteChoices)),
        "startDate"   -> optional(inTheFuture(ISODateTimeOrTimestamp.isoDateTimeOrTimestamp)),
        "variant" -> optional(
          nonEmptyText.verifying(v =>
            Variant(GameFamily(v.split("_")(0).toInt).gameLogic, v.split("_")(1).toInt).isDefined
          )
        ),
        "medley" -> optional(boolean),
        "medleyIntervalOptions" -> mapping(
          "medleyMinutes"    -> optional(numberIn(medleyMinutes)),
          "balanceIntervals" -> optional(boolean),
          "numIntervals"     -> optional(number(min = 2, max = maxMedleyIntervals))
        )(MedleyIntervalOptions.apply)(MedleyIntervalOptions.unapply),
        "medleyDefaults" -> mapping(
          "onePerGameFamily"    -> optional(boolean),
          "exoticChessVariants" -> optional(boolean),
          "draughts64Variants"  -> optional(boolean)
        )(MedleyDefaults.apply)(MedleyDefaults.unapply),
        "medleyGameFamilies" -> mapping(
          "chess"    -> optional(boolean),
          "draughts" -> optional(boolean),
          "shogi"    -> optional(boolean),
          "xiangqi"  -> optional(boolean),
          "loa"      -> optional(boolean),
          "flipello" -> optional(boolean),
          "mancala"  -> optional(boolean),
          "amazons"  -> optional(boolean)
        )(MedleyGameFamilies.apply)(MedleyGameFamilies.unapply),
        "position"         -> optional(lila.common.Form.fen.playableStrict),
        "mode"             -> optional(number.verifying(Mode.all.map(_.id) contains _)), // deprecated, use rated
        "rated"            -> optional(boolean),
        "password"         -> optional(cleanNonEmptyText),
        "conditions"       -> Condition.DataForm.all(leaderTeams),
        "teamBattleByTeam" -> optional(nonEmptyText.verifying(id => leaderTeams.exists(_.id == id))),
        "berserkable"      -> optional(boolean),
        "streakable"       -> optional(boolean),
        "description"      -> optional(cleanNonEmptyText),
        "hasChat"          -> optional(boolean)
      )(TournamentSetup.apply)(TournamentSetup.unapply)
        .verifying("Invalid clock", _.validClock)
        .verifying("15s and 0+1 variant games cannot be rated", _.validRatedVariant)
        .verifying("Increase tournament duration, or decrease game clock", _.sufficientDuration)
        .verifying("Reduce tournament duration, or increase game clock", _.excessiveDuration)
        .verifying("Must have more than 1 game type for medley tournaments", _.validMedleySetup)
    )
}

object TournamentForm {

  val clockTimes: Seq[Double] = Seq(0d, 1 / 4d, 1 / 2d, 3 / 4d, 1d, 3 / 2d) ++ {
    (2 to 7 by 1) ++ (10 to 30 by 5) ++ (40 to 60 by 10)
  }.map(_.toDouble)
  val clockTimeDefault = 2d
  private def formatLimit(l: Double) =
    FischerClock.Config(l * 60 toInt, 0).limitString + {
      if (l <= 1) " minute" else " minutes"
    }
  val clockTimeChoices = optionsDouble(clockTimes, formatLimit)

  val clockIncrements       = (0 to 2 by 1) ++ (3 to 7) ++ (10 to 30 by 5) ++ (40 to 60 by 10)
  val clockIncrementDefault = 0
  val clockIncrementChoices = options(clockIncrements, "%d second{s}")

  val clockByoyomi        = (1 to 9 by 1) ++ (10 to 30 by 5) ++ (40 to 60 by 10)
  val clockByoyomiDefault = 10
  val clockByoyomiChoices = options(clockByoyomi, "%d second{s}")

  val periods        = 1 to 5
  val periodsDefault = 1
  val periodsChoices = options(periods, "%d period{s}")

  val minutes       = (20 to 60 by 5) ++ (70 to 120 by 10) ++ (150 to 360 by 30) ++ (420 to 600 by 60) :+ 720
  val minuteDefault = 45
  val minuteChoices = options(minutes, "%d minute{s}")

  val medleyMinutes        = (5 to 30 by 5) ++ (40 to 40) ++ (45 to 60 by 15)
  val medleyMinuteChoices  = options(medleyMinutes, "%d minute{s}")
  val medleyMinutesDefault = 10

  val waitMinutes       = Seq(1, 2, 3, 5, 10, 15, 20, 30, 45, 60)
  val waitMinuteChoices = options(waitMinutes, "%d minute{s}")
  val waitMinuteDefault = 5

  val positions = StartingPosition.allWithInitial.map(_.fen)
  val positionChoices = StartingPosition.allWithInitial.map { p =>
    p.fen -> p.fullName
  }
  val positionDefault = StartingPosition.initial.fen

  val validVariants = Variant.all.filter(!_.fromPositionVariant)

  val maxMedleyIntervals: Int = validVariants.size

  def guessVariant(from: String): Option[Variant] =
    validVariants.find { v =>
      v.key == from || from.toIntOption.exists(v.id ==)
    }

  val joinForm =
    Form(
      mapping(
        "team"     -> optional(nonEmptyText),
        "password" -> optional(nonEmptyText)
      )(TournamentJoin.apply)(TournamentJoin.unapply)
    )

  case class TournamentJoin(team: Option[String], password: Option[String])
}

private[tournament] case class TournamentSetup(
    name: Option[String],
    clock: ClockConfig,
    minutes: Int,
    waitMinutes: Option[Int],
    startDate: Option[DateTime],
    variant: Option[String],
    medley: Option[Boolean],
    medleyIntervalOptions: MedleyIntervalOptions,
    medleyDefaults: MedleyDefaults,
    medleyGameFamilies: MedleyGameFamilies,
    position: Option[FEN],
    mode: Option[Int], // deprecated, use rated
    rated: Option[Boolean],
    password: Option[String],
    conditions: Condition.DataForm.AllSetup,
    teamBattleByTeam: Option[String],
    berserkable: Option[Boolean],
    streakable: Option[Boolean],
    description: Option[String],
    hasChat: Option[Boolean]
) {

  def validClock = clock match {
    case fc: FischerClock.Config => (fc.limitSeconds + fc.incrementSeconds) > 0
    case bc: ByoyomiClock.Config =>
      (bc.limitSeconds + bc.incrementSeconds) > 0 || (bc.limitSeconds + bc.byoyomiSeconds) > 0
  }

  def realMode =
    if (realPosition.isDefined) Mode.Casual
    else Mode(rated.orElse(mode.map(Mode.Rated.id ===)) | true)

  def gameLogic = variant match {
    case Some(v) => GameFamily(v.split("_")(0).toInt).gameLogic
    case None    => GameLogic.Chess()
  }

  def realVariant = variant flatMap { v =>
    Variant.apply(gameLogic, v.split("_")(1).toInt)
  } getOrElse Variant.default(gameLogic)

  def realPosition = position ifTrue realVariant.standardVariant

  def validRatedVariant =
    realMode == Mode.Casual ||
      lila.game.Game.allowRated(realVariant, clock.some)

  def sufficientDuration = estimateNumberOfGamesOneCanPlay >= 3
  def excessiveDuration  = estimateNumberOfGamesOneCanPlay <= 150

  def validMedleySetup = !isMedley || generateMedleyVariants.size > 1

  def isPrivate = password.isDefined || conditions.teamMember.isDefined

  // update all fields and use default values for missing fields
  // meant for HTML form updates
  def updateAll(old: Tournament): Tournament = {
    val newVariant = if (old.isCreated && variant.isDefined) realVariant else old.variant
    old
      .copy(
        name = name | old.name,
        clock = if (old.isCreated) clock else old.clock,
        minutes = if (isMedley) medleyDuration else minutes,
        mode = realMode,
        variant = newVariant,
        medleyVariantsAndIntervals =
          if (
            old.medleyGameGroups != medleyGameFamilies.ggList
              .sortWith(_.name < _.name)
              .some
            || old.medleyMinutes != medleyIntervalOptions.medleyMinutes
            || old.medleyIsBalanced != medleyIntervalOptions.balanceIntervals.fold(Some(false))(x => Some(x))
            || old.medleyNumIntervals != medleyIntervalOptions.numIntervals
          ) medleyVariantsAndIntervals
          else old.medleyVariantsAndIntervals,
        medleyMinutes = medleyIntervalOptions.medleyMinutes,
        startsAt = startDate | old.startsAt,
        password = password,
        position = newVariant.standardVariant ?? {
          if (old.isCreated || old.position.isDefined) realPosition
          else old.position
        },
        noBerserk = !(~berserkable),
        noStreak = !(~streakable),
        teamBattle = old.teamBattle,
        description = description,
        hasChat = hasChat | true
      )
  }

  // update only fields that are specified
  // meant for API updates
  def updatePresent(old: Tournament): Tournament = {
    val newVariant = if (old.isCreated) realVariant else old.variant
    old
      .copy(
        name = name | old.name,
        clock = if (old.isCreated) clock else old.clock,
        minutes = minutes,
        mode = if (rated.isDefined) realMode else old.mode,
        variant = newVariant,
        startsAt = startDate | old.startsAt,
        password = password.fold(old.password)(_.some.filter(_.nonEmpty)),
        position = newVariant.standardVariant ?? {
          if (position.isDefined && (old.isCreated || old.position.isDefined)) realPosition
          else old.position
        },
        noBerserk = berserkable.fold(old.noBerserk)(!_),
        noStreak = streakable.fold(old.noStreak)(!_),
        teamBattle = old.teamBattle,
        description = description.fold(old.description)(_.some.filter(_.nonEmpty)),
        hasChat = hasChat | old.hasChat
      )
  }

  private def estimateNumberOfGamesOneCanPlay: Double =
    ((if (isMedley) medleyDuration else minutes) * 60) / estimatedGameSeconds

  // There are 2 players, and they don't always use all their time (0.8)
  // add 15 seconds for pairing delay
  private def estimatedGameSeconds: Double = clock match {
    case bc: ByoyomiClock.Config =>
      {
        (bc.limitSeconds + 30 * bc.incrementSeconds + bc.byoyomiSeconds * 20 * bc.periodsTotal) * 2 * 0.8
      } + 15
    case fc: FischerClock.Config =>
      {
        (fc.limitSeconds + 30 * fc.incrementSeconds) * 2 * 0.8
      } + 15
  }

  def isMedley = (medley | false) && medleyGameFamilies.ggList.nonEmpty

  def medleyDuration: Int =
    medleyIntervalOptions.medleyMinutes.getOrElse(0) * medleyIntervalOptions.numIntervals.getOrElse(0)

  //We have to account for old medleys and their setup
  def maxMedleyRounds = medleyIntervalOptions.numIntervals.fold(
    medleyIntervalOptions.medleyMinutes.map(mm => Math.ceil(minutes.toDouble / mm).toInt)
  )(Some(_))

  //shuffle all variants from the selected game families
  private lazy val generateNoDefaultsMedleyVariants: List[Variant] =
    scala.util.Random
      .shuffle(
        medleyGameFamilies.ggList.flatMap(_.variants).filter(v => !v.fromPositionVariant)
      )

  private def generateMedleyVariants: List[Variant] =
    if (medleyDefaults.onePerGameFamily.getOrElse(false)) {
      //take a shuffled list of all variants and pull the first for each game family to the front
      val onePerGameGroupVariantList = scala.util.Random.shuffle(
        medleyGameFamilies.ggList.map(gg => scala.util.Random.shuffle(gg.variants).head)
      )
      onePerGameGroupVariantList ::: generateNoDefaultsMedleyVariants.filterNot(
        onePerGameGroupVariantList.contains(_)
      )
    } else if (medleyDefaults.exoticChessVariants.getOrElse(false))
      scala.util.Random.shuffle(Variant.all.filter(_.exoticChessVariant))
    else if (medleyDefaults.draughts64Variants.getOrElse(false))
      scala.util.Random.shuffle(Variant.all.filter(_.draughts64Variant))
    else generateNoDefaultsMedleyVariants

  def medleyVariantsAndIntervals: Option[List[(Variant, Int)]] =
    if (isMedley) {
      val medleyList     = generateMedleyVariants
      var fullMedleyList = medleyList
      while (fullMedleyList.size < maxMedleyRounds.getOrElse(0))
        fullMedleyList = fullMedleyList ::: medleyList
      TournamentMedleyUtil
        .medleyVariantsAndIntervals(
          fullMedleyList,
          clock.limitSeconds,
          medleyDuration,
          medleyIntervalOptions.medleyMinutes.getOrElse(0),
          medleyIntervalOptions.numIntervals.getOrElse(fullMedleyList.length),
          medleyIntervalOptions.balanceIntervals.getOrElse(false)
        )
        .some
    } else None

}

case class MedleyIntervalOptions(
    medleyMinutes: Option[Int],
    balanceIntervals: Option[Boolean],
    numIntervals: Option[Int]
)

case class MedleyDefaults(
    onePerGameFamily: Option[Boolean],
    exoticChessVariants: Option[Boolean],
    draughts64Variants: Option[Boolean]
)

case class MedleyGameFamilies(
    chess: Option[Boolean],
    draughts: Option[Boolean],
    shogi: Option[Boolean],
    xiangqi: Option[Boolean],
    loa: Option[Boolean],
    flipello: Option[Boolean],
    mancala: Option[Boolean],
    amazons: Option[Boolean]
) {

  lazy val ggList: List[GameGroup] = GameGroup.medley
    .filterNot(gg => if (!chess.getOrElse(false)) gg == GameGroup.Chess() else false)
    .filterNot(gg => if (!draughts.getOrElse(false)) gg == GameGroup.Draughts() else false)
    .filterNot(gg => if (!shogi.getOrElse(false)) gg == GameGroup.Shogi() else false)
    .filterNot(gg => if (!xiangqi.getOrElse(false)) gg == GameGroup.Xiangqi() else false)
    .filterNot(gg => if (!loa.getOrElse(false)) gg == GameGroup.LinesOfAction() else false)
    .filterNot(gg => if (!flipello.getOrElse(false)) gg == GameGroup.Flipello() else false)
    .filterNot(gg => if (!mancala.getOrElse(false)) gg == GameGroup.Mancala() else false)
    .filterNot(gg => if (!amazons.getOrElse(false)) gg == GameGroup.Amazons() else false)

}
