package lila.swiss

import strategygames.{ ByoyomiClock, ClockConfig, FischerClock }
import strategygames.format.FEN
import strategygames.variant.Variant
import strategygames.{ GameFamily, GameLogic }
import org.joda.time.DateTime
import play.api.data._
import play.api.data.Forms._
import play.api.Mode
import scala.concurrent.duration._

import lila.common.Form._

final class SwissForm(implicit mode: Mode) {

  import SwissForm._

  // Yes, I know this is kinda gross. :'(
  private def valuesFromClockConfig(c: ClockConfig): Option[(Boolean, Int, Int, Option[Int], Option[Int])] =
    c match {
      case fc: FischerClock.Config => {
        FischerClock.Config.unapply(fc).map(t => (false, t._1, t._2, None, None))
      }
      case bc: ByoyomiClock.Config => {
        ByoyomiClock.Config.unapply(bc).map(t => (true, t._1, t._2, Some(t._3), Some(t._4)))
      }
    }

  // Yes, I know this is kinda gross. :'(
  private def clockConfigFromValues(
      useByoyomi: Boolean,
      limit: Int,
      increment: Int,
      byoyomi: Option[Int],
      periods: Option[Int]
  ): ClockConfig =
    (useByoyomi, byoyomi, periods) match {
      case (true, Some(byoyomi), Some(periods)) =>
        ByoyomiClock.Config(limit, increment, byoyomi, periods)
      case _ =>
        FischerClock.Config(limit, increment)
    }

  def form(minRounds: Int = 3) =
    Form(
      mapping(
        "name" -> optional(eventName(2, 36)),
        "clock" -> mapping[ClockConfig, Boolean, Int, Int, Option[Int], Option[Int]](
          "useByoyomi" -> boolean,
          "limit"      -> number.verifying(clockLimits.contains _),
          "increment"  -> number(min = 0, max = 120),
          "byoyomi"    -> optional(number.verifying(byoyomiLimits.contains _)),
          "periods"    -> optional(number(min = 0, max = 5))
        )(clockConfigFromValues)(valuesFromClockConfig)
          .verifying("Invalid clock", _.estimateTotalSeconds > 0),
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
          "chess"    -> optional(boolean),
          "draughts" -> optional(boolean),
          "shogi"    -> optional(boolean),
          "xiangqi"  -> optional(boolean),
          "loa"      -> optional(boolean),
          "flipello" -> optional(boolean),
          "mancala"  -> optional(boolean)
        )(MedleyGameFamilies.apply)(MedleyGameFamilies.unapply),
        "rated" -> optional(boolean),
        "xGamesChoice" -> mapping(
          "bestOfX"    -> optional(boolean),
          "playX"      -> optional(boolean),
          "matchScore" -> optional(boolean),
          "nbGamesPerRound" -> number(
            min = SwissBounds.defaultGamesPerRound,
            max = SwissBounds.maxGamesPerRound
          )
        )(XGamesChoice.apply)(XGamesChoice.unapply),
        "nbRounds"             -> number(min = minRounds, max = SwissBounds.maxRounds),
        "description"          -> optional(cleanNonEmptyText),
        "drawTables"           -> optional(boolean),
        "perPairingDrawTables" -> optional(boolean),
        "position"             -> optional(lila.common.Form.fen.playableStrict),
        "chatFor"              -> optional(numberIn(chatForChoices.map(_._1))),
        "roundInterval"        -> optional(numberIn(roundIntervals)),
        "password"             -> optional(cleanNonEmptyText),
        "conditions"           -> SwissCondition.DataForm.all,
        "forbiddenPairings"    -> optional(cleanNonEmptyText)
      )(SwissData.apply)(SwissData.unapply)
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
    )

  def create =
    form() fill SwissData(
      name = none,
      clock = FischerClock.Config(180, 0),
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
        mancala = true.some
      ),
      rated = true.some,
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
      password = None,
      conditions = SwissCondition.DataForm.AllSetup.default,
      forbiddenPairings = none
    )

  def edit(s: Swiss) =
    form(s.round.value) fill SwissData(
      name = s.name.some,
      clock = s.clock,
      startsAt = s.startsAt.some,
      variant = s"${s.variant.gameFamily.id}_${s.variant.id}".some,
      medley = s.isMedley.some,
      medleyDefaults = MedleyDefaults(
        onePerGameFamily = onePerGameFamilyInMedley(s.settings.medleyVariants).some,
        exoticChessVariants = exoticChessVariants(s.settings.medleyVariants).some,
        draughts64Variants = draughts64Variants(s.settings.medleyVariants).some
      ),
      medleyGameFamilies = MedleyGameFamilies(
        chess = gameFamilyInMedley(s.settings.medleyVariants, GameFamily.Chess()).some,
        draughts = gameFamilyInMedley(s.settings.medleyVariants, GameFamily.Draughts()).some,
        shogi = gameFamilyInMedley(s.settings.medleyVariants, GameFamily.Shogi()).some,
        xiangqi = gameFamilyInMedley(s.settings.medleyVariants, GameFamily.Xiangqi()).some,
        loa = gameFamilyInMedley(s.settings.medleyVariants, GameFamily.LinesOfAction()).some,
        flipello = gameFamilyInMedley(s.settings.medleyVariants, GameFamily.Flipello()).some,
        mancala = gameFamilyInMedley(s.settings.medleyVariants, GameFamily.Mancala()).some
      ),
      rated = s.settings.rated.some,
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
      password = s.settings.password,
      conditions = SwissCondition.DataForm.AllSetup(s.settings.conditions),
      forbiddenPairings = s.settings.forbiddenPairings.some.filter(_.nonEmpty)
    )

  def nextRound =
    Form(
      single(
        "date" -> inTheFuture(ISODateTimeOrTimestamp.isoDateTimeOrTimestamp)
      )
    )

  private def medleyVariantsList(medleyVariants: Option[List[Variant]]) =
    medleyVariants.getOrElse(List[Variant]())

  private def gameFamilyInMedley(medleyVariants: Option[List[Variant]], gf: GameFamily) =
    medleyVariantsList(medleyVariants).map(v => v.gameFamily).contains(gf)

  private def onePerGameFamilyInMedley(medleyVariants: Option[List[Variant]]) = {
    val mvList       = medleyVariantsList(medleyVariants)
    val gameFamilies = mvList.map(_.gameFamily).distinct
    mvList.map(_.gameFamily).take(gameFamilies.size) == gameFamilies && gameFamilies.size > 1
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

  val byoyomiLimits: Seq[Int] = (1 to 9 by 1) ++ (10 to 30 by 5) ++ (30 to 60 by 10)

  val clockByoyomiChoices = options(byoyomiLimits, "%d second{s}")

  val clockLimitChoices = options(
    clockLimits,
    l => s"${strategygames.FischerClock.Config(l, 0).limitString}${if (l <= 1) " minute" else " minutes"}"
  )

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
      xGamesChoice: XGamesChoice,
      nbRounds: Int,
      description: Option[String],
      drawTables: Option[Boolean],
      perPairingDrawTables: Option[Boolean],
      position: Option[FEN],
      chatFor: Option[Int],
      roundInterval: Option[Int],
      password: Option[String],
      conditions: SwissCondition.DataForm.AllSetup,
      forbiddenPairings: Option[String]
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
    def useDrawTables           = drawTables | false
    def usePerPairingDrawTables = perPairingDrawTables | false
    def realPosition            = position ifTrue realVariant.standardVariant

    def isRated         = rated | true
    def isMatchScore    = xGamesChoice.matchScore | false
    def isBestOfX       = xGamesChoice.bestOfX | false
    def isPlayX         = xGamesChoice.playX | false
    def nbGamesPerRound = xGamesChoice.nbGamesPerRound
    def validXGamesSetup =
      ((!isBestOfX && !isPlayX) || nbGamesPerRound > 1) && !(isBestOfX && isPlayX)
    def validMatchScoreSetup = !isMatchScore || !(isBestOfX && nbGamesPerRound % 2 == 1)
    def validNumberofGames =
      (nbGamesPerRound > 1 && (isBestOfX || isPlayX)) || (nbGamesPerRound == 1 && !isMatchScore)
    def validRatedVariant =
      !isRated ||
        lila.game.Game.allowRated(realVariant, clock.some)

    def isMedley = (medley | false) && medleyGameFamilies.gfList.nonEmpty

    //shuffle all variants from the selected game families
    private lazy val generateNoDefaultsMedleyVariants: List[Variant] =
      scala.util.Random
        .shuffle(
          Variant.all.filter(v => medleyGameFamilies.gfList.contains(v.gameFamily) && !v.fromPositionVariant)
        )

    private def generateMedleyVariants: List[Variant] =
      if (medleyDefaults.onePerGameFamily.getOrElse(false)) {
        //take a shuffled list of all variants and pull the first for each game family to the front
        val onePerGameFamilyVariantList = scala.util.Random.shuffle(
          medleyGameFamilies.gfList.map(gf =>
            generateNoDefaultsMedleyVariants.filter(_.gameFamily == gf).head
          )
        )
        onePerGameFamilyVariantList ::: generateNoDefaultsMedleyVariants.filterNot(
          onePerGameFamilyVariantList.contains(_)
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
      mancala: Option[Boolean]
  ) {

    lazy val gfList: List[GameFamily] = GameFamily.all
      .filterNot(gf => if (!chess.getOrElse(false)) gf == GameFamily.Chess() else false)
      .filterNot(gf => if (!draughts.getOrElse(false)) gf == GameFamily.Draughts() else false)
      .filterNot(gf => if (!shogi.getOrElse(false)) gf == GameFamily.Shogi() else false)
      .filterNot(gf => if (!xiangqi.getOrElse(false)) gf == GameFamily.Xiangqi() else false)
      .filterNot(gf => if (!loa.getOrElse(false)) gf == GameFamily.LinesOfAction() else false)
      .filterNot(gf => if (!flipello.getOrElse(false)) gf == GameFamily.Flipello() else false)
      .filterNot(gf => if (!mancala.getOrElse(false)) gf == GameFamily.Mancala() else false)

  }
}
