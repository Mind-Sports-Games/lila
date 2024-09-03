package lila.swiss

import strategygames.{ ByoyomiClock, Clock, ClockConfig }
import strategygames.format.FEN
import strategygames.variant.Variant
import strategygames.{ GameFamily, GameGroup, GameLogic }
import org.joda.time.DateTime
import play.api.data._
import play.api.data.Forms._
import play.api.Mode
import scala.concurrent.duration._

import lila.common.Form._
import lila.common.Clock._

final class SwissForm(implicit mode: Mode) {

  import SwissForm._

  def form(minRounds: Int = 3) =
    Form(
      mapping(
        "name" -> optional(eventName(2, 36)),
        "clock" -> clockConfigMappingsSeconds(clockLimits, byoyomiLimits).verifying(
          "Invalid clock",
          _.estimateTotalSeconds > 0
        ),
        "startsAt" -> optional(inTheFuture(ISODateTimeOrTimestamp.isoDateTimeOrTimestamp)),
        "variant" -> optional(
          nonEmptyText.verifying(v =>
            Variant(GameFamily(v.split("_")(0).toInt).gameLogic, v.split("_")(1).toInt).isDefined
          )
        ),
        "medley" -> optional(boolean),
        "medleyDefaults" -> mapping(
          "onePerGameFamily"    -> optional(boolean),
          "exoticChessVariants" -> optional(boolean),
          "draughts64Variants"  -> optional(boolean)
        )(MedleyDefaults.apply)(MedleyDefaults.unapply),
        "medleyGameFamilies" -> mapping(
          "chess"              -> optional(boolean),
          "draughts"           -> optional(boolean),
          "shogi"              -> optional(boolean),
          "xiangqi"            -> optional(boolean),
          "loa"                -> optional(boolean),
          "flipello"           -> optional(boolean),
          "mancala"            -> optional(boolean),
          "amazons"            -> optional(boolean),
          "breakthroughtroyka" -> optional(boolean),
          "go"                 -> optional(boolean),
          "backgammon"         -> optional(boolean)
        )(MedleyGameFamilies.apply)(MedleyGameFamilies.unapply),
        "rated" -> optional(boolean),
        "handicaps" -> mapping(
          "handicapped"        -> optional(boolean),
          "inputPlayerRatings" -> optional(cleanNonEmptyText)
        )(Handicaps.apply)(Handicaps.unapply),
        "xGamesChoice" -> mapping(
          "bestOfX"    -> optional(boolean),
          "playX"      -> optional(boolean),
          "matchScore" -> optional(boolean),
          "nbGamesPerRound" -> number(
            min = SwissBounds.defaultGamesPerRound,
            max = SwissBounds.maxGamesPerRound
          )
        )(XGamesChoice.apply)(XGamesChoice.unapply),
        "nbRounds"                 -> number(min = minRounds, max = SwissBounds.maxRounds),
        "description"              -> optional(cleanNonEmptyText),
        "drawTables"               -> optional(boolean),
        "perPairingDrawTables"     -> optional(boolean),
        "position"                 -> optional(lila.common.Form.fen.playableStrict),
        "chatFor"                  -> optional(numberIn(chatForChoices.map(_._1))),
        "roundInterval"            -> optional(numberIn(roundIntervals)),
        "halfwayBreak"             -> optional(numberIn(halfwayBreakOptions)),
        "password"                 -> optional(cleanNonEmptyText),
        "conditions"               -> SwissCondition.DataForm.all,
        "forbiddenPairings"        -> optional(cleanNonEmptyText),
        "minutesBeforeStartToJoin" -> optional(numberIn(timeBeforeStartToJoinOptions))
      )(SwissData.apply)(SwissData.unapply)
        .verifying("Invalid clock", _.validClock)
        .verifying("15s and 0+1 variant games cannot be rated", _.validRatedVariant)
        .verifying(
          "must have > 1 game per round if using 'Best of X' or 'Play X' options",
          _.validXGamesSetup
        )
        .verifying(
          "Cannot have Match Score with an odd number of games per round in best of X",
          _.validMatchScoreSetup
        )
        .verifying(
          "Best of x or Play x and Match Score can only be used if number of games per round is greater than 1",
          _.validNumberofGames
        )
        .verifying("Hanidcapped mode requires a Go variant, non-rated and non-meldey", _.validHandicapped)
    )

  def create =
    form() fill SwissData(
      name = none,
      clock = Clock.Config(180, 0),
      startsAt = Some(DateTime.now plusSeconds {
        if (mode == Mode.Prod) 60 * 10 else 20
      }),
      variant = s"${GameFamily.Chess().id}_${Variant.default(GameLogic.Chess()).id}".some,
      medley = false.some,
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
        amazons = true.some,
        breakthroughtroyka = true.some,
        go = true.some,
        backgammon = true.some
      ),
      rated = true.some,
      handicaps = Handicaps(
        handicapped = false.some,
        inputPlayerRatings = none
      ),
      xGamesChoice = XGamesChoice(
        bestOfX = false.some,
        playX = false.some,
        matchScore = false.some,
        nbGamesPerRound = 1
      ),
      nbRounds = 7,
      description = none,
      drawTables = false.some,
      perPairingDrawTables = false.some,
      position = none,
      chatFor = Swiss.ChatFor.default.some,
      roundInterval = Swiss.RoundInterval.auto.some,
      halfwayBreak = None,
      password = None,
      conditions = SwissCondition.DataForm.AllSetup.default,
      forbiddenPairings = none,
      minutesBeforeStartToJoin = none
    )

  def edit(s: Swiss) =
    form(s.round.value) fill SwissData(
      name = s.name.some,
      clock = s.clock,
      startsAt = s.startsAt.some,
      variant = s"${s.variant.gameFamily.id}_${s.variant.id}".some,
      medley = s.isMedley.some,
      medleyDefaults = MedleyDefaults(
        onePerGameFamily = onePerGameGroupInMedley(s.settings.medleyVariants).some,
        exoticChessVariants = exoticChessVariants(s.settings.medleyVariants).some,
        draughts64Variants = draughts64Variants(s.settings.medleyVariants).some
      ),
      medleyGameFamilies = MedleyGameFamilies(
        chess = gameGroupInMedley(s.settings.medleyVariants, GameGroup.Chess()).some,
        draughts = gameGroupInMedley(s.settings.medleyVariants, GameGroup.Draughts()).some,
        shogi = gameGroupInMedley(s.settings.medleyVariants, GameGroup.Shogi()).some,
        xiangqi = gameGroupInMedley(s.settings.medleyVariants, GameGroup.Xiangqi()).some,
        loa = gameGroupInMedley(s.settings.medleyVariants, GameGroup.LinesOfAction()).some,
        flipello = gameGroupInMedley(s.settings.medleyVariants, GameGroup.Flipello()).some,
        mancala = gameGroupInMedley(s.settings.medleyVariants, GameGroup.Mancala()).some,
        amazons = gameGroupInMedley(s.settings.medleyVariants, GameGroup.Amazons()).some,
        breakthroughtroyka =
          gameGroupInMedley(s.settings.medleyVariants, GameGroup.BreakthroughTroyka()).some,
        go = gameGroupInMedley(s.settings.medleyVariants, GameGroup.Go()).some,
        backgammon = gameGroupInMedley(s.settings.medleyVariants, GameGroup.Backgammon()).some
      ),
      rated = s.settings.rated.some,
      handicaps = Handicaps(
        handicapped = s.settings.handicapped.some,
        inputPlayerRatings = s.settings.inputPlayerRatings.some.filter(_.nonEmpty)
      ),
      xGamesChoice = XGamesChoice(
        bestOfX = s.settings.isBestOfX.some,
        playX = s.settings.isPlayX.some,
        matchScore = s.settings.isMatchScore.some,
        nbGamesPerRound = s.settings.nbGamesPerRound
      ),
      nbRounds = s.settings.nbRounds,
      description = s.settings.description,
      drawTables = s.settings.useDrawTables.some,
      perPairingDrawTables = s.settings.usePerPairingDrawTables.some,
      position = s.settings.position,
      chatFor = s.settings.chatFor.some,
      roundInterval = s.settings.roundInterval.toSeconds.toInt.some,
      halfwayBreak = s.settings.halfwayBreak.toSeconds.toInt.some,
      password = s.settings.password,
      conditions = SwissCondition.DataForm.AllSetup(s.settings.conditions),
      forbiddenPairings = s.settings.forbiddenPairings.some.filter(_.nonEmpty),
      minutesBeforeStartToJoin = s.settings.minutesBeforeStartToJoin
    )

  def nextRound =
    Form(
      single(
        "date" -> inTheFuture(ISODateTimeOrTimestamp.isoDateTimeOrTimestamp)
      )
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

}

object SwissForm {

  val clockLimits: Seq[Int] = Seq(0, 15, 30, 45, 60, 90) ++ {
    (120 to 420 by 60) ++ (600 to 1800 by 300) ++ (2400 to 10800 by 600)
  }
  val clockLimitChoices = clockTimeChoicesFromSeconds(clockLimits)

  val byoyomiLimits: Seq[Int] = (1 to 9 by 1) ++ (10 to 30 by 5) ++ (30 to 60 by 10)

  val clockByoyomiChoices = options(byoyomiLimits, "%d second{s}")

  val roundIntervals: Seq[Int] =
    Seq(
      Swiss.RoundInterval.auto,
      5,
      10,
      20,
      30,
      45,
      60,
      120,
      180,
      300,
      600,
      900,
      1200,
      1800,
      2700,
      3600,
      24 * 3600,
      2 * 24 * 3600,
      7 * 24 * 3600,
      Swiss.RoundInterval.manual
    )

  val roundIntervalChoices = options(
    roundIntervals,
    s =>
      if (s == Swiss.RoundInterval.auto) s"Automatic"
      else if (s == Swiss.RoundInterval.manual) s"Manually schedule each round"
      else if (s < 60) s"$s seconds"
      else if (s < 3600) s"${s / 60} minute(s)"
      else if (s < 24 * 3600) s"${s / 3600} hour(s)"
      else s"${s / 24 / 3600} days(s)"
  )

  val halfwayBreakOptions: Seq[Int] =
    Seq(
      0,
      30,
      60,
      2 * 60,
      5 * 60,
      10 * 60,
      20 * 60,
      30 * 60,
      45 * 60,
      60 * 60,
      2 * 60 * 60
    )

  val halfwayBreakChoices = options(
    halfwayBreakOptions,
    s =>
      if (s == 0) "No additional interval"
      else if (s < 60) s"$s seconds"
      else if (s < 3600) s"${s / 60} minute${if (s == 60) "" else "s"}"
      else if (s < 24 * 3600) s"${s / 3600} hour${if (s == 60 * 60) "" else "s"}"
      else s"${s / 24 / 3600} day${if (s == 24 * 60 * 60) "" else "s"}"
  )

  val timeBeforeStartToJoinOptions: Seq[Int] =
    Seq(
      Swiss.TimeBeforeStartToJoin.nolimit,
      15,
      30,
      60,
      2 * 60,
      6 * 60,
      12 * 60,
      24 * 60,
      2 * 24 * 60,
      7 * 24 * 60
    )

  val timeBeforeStartToJoinIntervalChoices = options(
    timeBeforeStartToJoinOptions,
    m =>
      if (m == Swiss.TimeBeforeStartToJoin.nolimit) "No Limit"
      else if (m < 60) s"$m minutes"
      else if (m < 24 * 60) s"${m / 60} hour${if (m == 60) "" else "s"}"
      else s"${m / 24 / 60} day${if (m == 24 * 60) "" else "s"}"
  )

  val chatForChoices = List(
    Swiss.ChatFor.NONE    -> "No chat",
    Swiss.ChatFor.LEADERS -> "Team leaders only",
    Swiss.ChatFor.MEMBERS -> "Team members only",
    Swiss.ChatFor.ALL     -> "All PlayStrategy players"
  )

  case class SwissData(
      name: Option[String],
      clock: ClockConfig,
      startsAt: Option[DateTime],
      variant: Option[String],
      medley: Option[Boolean],
      medleyDefaults: MedleyDefaults,
      medleyGameFamilies: MedleyGameFamilies,
      rated: Option[Boolean],
      handicaps: Handicaps,
      xGamesChoice: XGamesChoice,
      nbRounds: Int,
      description: Option[String],
      drawTables: Option[Boolean],
      perPairingDrawTables: Option[Boolean],
      position: Option[FEN],
      chatFor: Option[Int],
      roundInterval: Option[Int],
      halfwayBreak: Option[Int],
      password: Option[String],
      conditions: SwissCondition.DataForm.AllSetup,
      forbiddenPairings: Option[String],
      minutesBeforeStartToJoin: Option[Int]
  ) {
    def gameLogic = variant match {
      case Some(v) => GameFamily(v.split("_")(0).toInt).gameLogic
      case None    => GameLogic.Chess()
    }
    def realVariant = variant flatMap { v =>
      Variant.apply(gameLogic, v.split("_")(1).toInt)
    } getOrElse Variant.default(gameLogic)
    def realStartsAt = startsAt | DateTime.now.plusMinutes(10)
    def realChatFor  = chatFor | Swiss.ChatFor.default
    def realRoundInterval = {
      (roundInterval | Swiss.RoundInterval.auto) match {
        case Swiss.RoundInterval.auto =>
          import strategygames.Speed._
          strategygames.Speed(clock) match {
            case UltraBullet                               => 5
            case Bullet                                    => 10
            case Blitz if clock.estimateTotalSeconds < 300 => 20
            case Blitz                                     => 30
            case Rapid                                     => 60
            case _                                         => 300
          }
        case i => i
      }
    }.seconds
    def realHalfwayBreak = halfwayBreak.fold(0)(i => i).seconds
    def realMinutesBeforeStartToJoin: Option[Int] =
      minutesBeforeStartToJoin match {
        case Some(Swiss.TimeBeforeStartToJoin.nolimit) => None
        case Some(mbs)                                 => Some(mbs)
        case _                                         => None
      }
    def useDrawTables           = drawTables | false
    def usePerPairingDrawTables = perPairingDrawTables | false
    def realPosition            = position ifTrue realVariant.standardVariant

    def isRated            = rated | true
    def isHandicapped      = handicaps.handicapped | false
    def inputPlayerRatings = if (isHandicapped) handicaps.inputPlayerRatings else None
    def isMatchScore       = xGamesChoice.matchScore | false
    def isBestOfX          = xGamesChoice.bestOfX | false
    def isPlayX            = xGamesChoice.playX | false
    def nbGamesPerRound    = xGamesChoice.nbGamesPerRound
    def validXGamesSetup =
      ((!isBestOfX && !isPlayX) || nbGamesPerRound > 1) && !(isBestOfX && isPlayX)
    def validMatchScoreSetup = !isMatchScore || !(isBestOfX && nbGamesPerRound % 2 == 1)
    def validNumberofGames =
      (nbGamesPerRound > 1 && (isBestOfX || isPlayX)) || (nbGamesPerRound == 1 && !isMatchScore)
    def validRatedVariant =
      !isRated ||
        lila.game.Game.allowRated(realVariant, clock.some)
    def validHandicapped = !isHandicapped || (gameLogic == GameLogic.Go() && !isMedley && !isRated)

    def validClock = clock match {
      case fc: Clock.Config             => (fc.limitSeconds + fc.incrementSeconds) > 0
      case bc: Clock.BronsteinConfig    => (bc.limitSeconds + bc.delaySeconds) > 0 && bc.delaySeconds > 0
      case udc: Clock.SimpleDelayConfig => (udc.limitSeconds + udc.delaySeconds) > 0 && udc.delaySeconds > 0
      case bc: ByoyomiClock.Config =>
        (bc.limitSeconds + bc.incrementSeconds) > 0 || (bc.limitSeconds + bc.byoyomiSeconds) > 0
    }

    def isMedley = (medley | false) && medleyGameFamilies.ggList.nonEmpty

    //shuffle all variants from the selected game groups
    private lazy val generateNoDefaultsMedleyVariants: List[Variant] =
      scala.util.Random
        .shuffle(
          medleyGameFamilies.ggList.flatMap(_.variants).filter(v => !v.fromPositionVariant)
        )

    private def generateMedleyVariants: List[Variant] =
      if (medleyDefaults.onePerGameFamily.getOrElse(false)) {
        //take a shuffled list of all variants and pull the first for each game group to the front
        val onePerGameGroupVariantList = scala.util.Random.shuffle(
          medleyGameFamilies.ggList
            .map(gg => scala.util.Random.shuffle(gg.variants).head)
        )
        onePerGameGroupVariantList ::: generateNoDefaultsMedleyVariants.filterNot(
          onePerGameGroupVariantList.contains(_)
        )
      } else if (medleyDefaults.exoticChessVariants.getOrElse(false))
        scala.util.Random.shuffle(Variant.all.filter(_.exoticChessVariant))
      else if (medleyDefaults.draughts64Variants.getOrElse(false))
        scala.util.Random.shuffle(Variant.all.filter(_.draughts64Variant))
      else generateNoDefaultsMedleyVariants

    def medleyVariants: Option[List[Variant]] =
      if (isMedley) {
        val medleyList     = generateMedleyVariants
        var fullMedleyList = medleyList
        while (fullMedleyList.size < nbRounds) fullMedleyList = fullMedleyList ::: medleyList
        fullMedleyList.some
      } else None

  }

  case class Handicaps(
      handicapped: Option[Boolean],
      inputPlayerRatings: Option[String]
  )

  case class XGamesChoice(
      bestOfX: Option[Boolean],
      playX: Option[Boolean],
      matchScore: Option[Boolean],
      nbGamesPerRound: Int
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
      amazons: Option[Boolean],
      breakthroughtroyka: Option[Boolean],
      go: Option[Boolean],
      backgammon: Option[Boolean]
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
      .filterNot(gg =>
        if (!breakthroughtroyka.getOrElse(false)) gg == GameGroup.BreakthroughTroyka() else false
      )
      .filterNot(gg => if (!go.getOrElse(false)) gg == GameGroup.Go() else false)
      .filterNot(gg => if (!backgammon.getOrElse(false)) gg == GameGroup.Backgammon() else false)

  }
}
