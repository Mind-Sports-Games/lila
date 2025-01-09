package lila.tournament

import org.joda.time.{ DateTime, Months, Weeks }
import reactivemongo.api.ReadPreference
import scala.concurrent.duration._
import scala.util.Random

import lila.db.dsl._
import lila.user.User
import lila.memo.CacheApi._
import lila.i18n.VariantKeys

import Schedule.Speed._

import strategygames.variant.Variant
import strategygames.{ GameFamily, GameGroup }

final class TournamentShieldApi(
    tournamentRepo: TournamentRepo,
    cacheApi: lila.memo.CacheApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  import TournamentShield._
  import BSONHandlers._

  def active(u: User): Fu[List[Award]] =
    cache.getUnit dmap {
      _.value.values.flatMap(_.headOption.filter(_.owner.value == u.id)).toList
    }

  def history(maxPerCateg: Option[Int]): Fu[History] =
    cache.getUnit dmap { h =>
      maxPerCateg.fold(h)(h.take)
    }

  def byCategKey(k: String): Fu[Option[(Category, List[Award])]] =
    Category.byKey(k) ?? { categ =>
      cache.getUnit dmap {
        _.value get categ map {
          categ -> _
        }
      }
    }

  def byMedleyKey(k: String): Option[MedleyShield] = MedleyShield.byKey(k)

  def currentOwner(tour: Tournament): Fu[Option[OwnerId]] =
    tour.isShield ?? {
      Category.of(tour) ?? { cat =>
        history(none).map(_.current(cat).map(_.owner))
      }
    }

  private[tournament] def clear(): Unit = cache.invalidateUnit().unit

  private[tournament] def clearAfterMarking(userId: User.ID): Funit = cache.getUnit map { hist =>
    import cats.implicits._
    if (hist.value.exists(_._2.exists(_.owner.value === userId))) clear()
  }

  private val cache = cacheApi.unit[History] {
    _.refreshAfterWrite(1 day)
      .buildAsyncFuture { _ =>
        tournamentRepo.coll
          .find(
            $doc(
              "schedule.freq" -> scheduleFreqHandler.writeTry(Schedule.Freq.Shield).get,
              "status"        -> statusBSONHandler.writeTry(Status.Finished).get
            )
          )
          .sort($sort asc "startsAt")
          .cursor[Tournament](ReadPreference.secondaryPreferred)
          .list() map { tours =>
          for {
            tour   <- tours
            categ  <- Category of tour
            winner <- tour.winnerId
          } yield Award(
            categ = categ,
            owner = OwnerId(winner),
            date = tour.finishesAt,
            tourId = tour.id
          )
        } map {
          _.foldLeft(Map.empty[Category, List[Award]]) { case (hist, entry) =>
            hist + (entry.categ -> hist.get(entry.categ).fold(List(entry))(entry :: _))
          }
        } dmap History.apply
      }
  }
}

object TournamentShield {

  case class OwnerId(value: String) extends AnyVal

  case class Award(
      categ: Category,
      owner: OwnerId,
      date: DateTime,
      tourId: Tournament.ID
  )
  // newer entry first
  case class History(value: Map[Category, List[Award]]) {

    def sorted: List[(Category, List[Award])] =
      Category.all.sortBy(_.dayOfMonth) map { categ =>
        categ -> ~(value get categ)
      }

    def userIds: List[User.ID] = value.values.flatMap(_.map(_.owner.value)).toList

    def current(cat: Category): Option[Award] = value get cat flatMap (_.headOption)

    def take(max: Int) =
      copy(
        value = value.view.mapValues(_ take max).toMap
      )
  }

  sealed abstract class MedleyShield(
      val key: String,
      val leaderboardKey: String,
      val name: String,
      val teamOwner: Condition.TeamMember,
      val variants: List[Variant],
      val generateVariants: List[Variant] => List[(Variant, Int)],
      val speed: Schedule.Speed,
      val weekOfMonth: Option[Int],
      val dayOfWeek: Int,
      val hour: Int,
      val arenaMinutes: Int,
      val medleyRounds: Int,
      val swissFormat: String,
      val arenaFormat: String,
      val arenaDescription: String,
      val countOffset: Int = 0
  ) {
    def eligibleVariants = variants.distinct
    def hasAllVariants   = eligibleVariants == Variant.all.filterNot(_.fromPositionVariant)
    def medleyName       = s"${name} Medley Shield"
    def url              = s"https://playstrategy.org/tournament/medley-shield/${key}"
    def medleyMinutes    = arenaMinutes / medleyRounds
    def balancedFormat =
      if (weekOfMonth.nonEmpty) ""
      else
        " Each variant is active for a short period of the tournament. The length each variant gets is variable but balanced so that quicker/slower variants have shorter/longer interval times."
    def intervalStr     = if (weekOfMonth.isEmpty) "week" else "month"
    def arenaFormatFull = s"${arenaFormat} ${balancedFormat}"
    def arenaDescriptionFull =
      s"${arenaDescription}\r\n\r\nWin the tournament, win the shield... until next ${intervalStr}!\r\n\r\nMore info here: ${url}"
    def useStatusScoring = variants.map(_.gameFamily).toSet.size == 1 && variants
      .map(_.gameFamily)
      .headOption
      .getOrElse(GameFamily.Chess()) == GameFamily.Backgammon()
  }

  object MedleyShield {

    private def generateOnePerGameGroup(variants: List[Variant], gameGroups: List[GameGroup]) =
      Random.shuffle(
        gameGroups.map(gg =>
          Random
            .shuffle(
              gg.variants.filter(v => variants.contains(v))
            )
            .head
        )
      )

    private def playStrategyMedleyGeneration(variants: List[Variant]) = {
      val thisOrder       = Random.shuffle(variants)
      val gameGroups      = GameGroup.medley.filter(gg => gg.variants.exists(thisOrder.contains(_)))
      val onePerGameGroup = generateOnePerGameGroup(thisOrder, gameGroups)
      val newOrder        = onePerGameGroup ::: thisOrder.filterNot(onePerGameGroup.contains(_))
      TournamentMedleyUtil.medleyVariantsAndIntervals(
        newOrder,
        5 * 60,
        playStrategyMinutes,
        playStrategyRounds,
        true
      )
    }
    private val playStrategyMinutes = 120
    private val playStrategyRounds  = GameGroup.medley.length

    case object PlayStrategyMedley
        extends MedleyShield(
          "shieldPlayStrategyMedley",
          "spm",
          "PlayStrategy",
          Condition.TeamMember("playstrategy-medleys", "PlayStrategy Medleys"),
          Variant.all.filterNot(_.fromPositionVariant).filterNot(_.key == "go19x19"),
          playStrategyMedleyGeneration,
          Blitz55,
          None,
          7,
          20,
          playStrategyMinutes,
          playStrategyRounds,
          s"${playStrategyRounds} round Swiss with one game from each of the ${GameGroup.medley.length} Game Families picked: ${GameGroup.medley.map(VariantKeys.gameGroupName).sorted.mkString(", ")}.",
          s"${playStrategyRounds} variant Arena with one game from each of the ${GameGroup.medley.length} Game Families picked: ${GameGroup.medley.map(VariantKeys.gameGroupName).sorted.mkString(", ")}.",
          s"PlayStrategy Medley Arena with one game from each of the ${GameGroup.medley.length} Game Families picked: ${GameGroup.medley.map(VariantKeys.gameGroupName).sorted.mkString(", ")}."
        )

    private def randomChessVariantOrder(variants: List[Variant]) =
      TournamentMedleyUtil.medleyVariantsAndIntervals(
        Random.shuffle(variants),
        5 * 60,
        chessVariantMinutes,
        chessVariantRounds,
        true
      )
    private val chessVariantOptions = Variant.all.filter(_.exoticChessVariant)
    private val chessVariantMinutes = 90
    private val chessVariantRounds  = 6

    case object ChessVariantsMedley
        extends MedleyShield(
          "shieldChessMedley",
          "scm",
          "Chess Variants",
          Condition.TeamMember("playstrategy-chess-variants", "PlayStrategy Chess Variants"),
          chessVariantOptions,
          randomChessVariantOrder,
          Blitz53,
          None,
          6,
          20,
          chessVariantMinutes,
          chessVariantRounds,
          s"${chessVariantRounds} round Swiss using micro-match rounds (each pairing plays twice, once each as the start player). ${chessVariantRounds} from the ${chessVariantOptions.length} listed chess variants will be picked.",
          s"${chessVariantRounds} variant Arena where ${chessVariantRounds} from the ${chessVariantOptions.length} listed chess variants are picked.",
          s"Chess Variants Medley Arena, where ${chessVariantRounds} from the following ${chessVariantOptions.length} chess variants are picked: ${chessVariantOptions.map(VariantKeys.variantName).mkString(", ")}."
        )

    private def randomDraughtsVariantOrder(variants: List[Variant]) =
      TournamentMedleyUtil.medleyVariantsAndIntervals(
        Random.shuffle(variants),
        5 * 60,
        draughtsVariantMinutes,
        draughtsRounds,
        true
      )
    private val draughtsVariantOptions =
      Variant.all.filter(_.gameFamily == GameFamily.Draughts()).filterNot(_.fromPositionVariant)
    private val draughtsVariantMinutes = 90
    private val draughtsRounds         = 6

    case object DraughtsMedley
        extends MedleyShield(
          "shieldDraughtsMedley",
          "sdm",
          VariantKeys.gameFamilyName(GameFamily.Draughts()),
          Condition.TeamMember("playstrategy-draughts", "PlayStrategy Draughts"),
          draughtsVariantOptions,
          randomDraughtsVariantOrder,
          Blitz53,
          None,
          6,
          14,
          draughtsVariantMinutes,
          draughtsRounds,
          s"${draughtsRounds} round Swiss where ${draughtsRounds} from the ${draughtsVariantOptions.length} listed draughts variants will be picked.",
          s"${draughtsRounds} variant Arena where ${draughtsRounds} from the ${draughtsVariantOptions.length} listed draughts variants are picked.",
          s"Draughts Medley Arena, where ${draughtsRounds} from the following ${draughtsVariantOptions.length} Draughts variants are picked: ${draughtsVariantOptions.map(VariantKeys.variantName).mkString(", ")}."
        )

    private def loaVariantOrder(variants: List[Variant]) =
      TournamentMedleyUtil.medleyVariantsAndIntervals(
        variants,
        5 * 60,
        loaVariantMinutes,
        loaRounds,
        false
      )
    private val loaVariants = List(
      Variant.wrap(strategygames.chess.variant.LinesOfAction),
      Variant.wrap(strategygames.chess.variant.ScrambledEggs),
      Variant.wrap(strategygames.chess.variant.LinesOfAction)
    )

    private val loaVariantMinutes = 90
    private val loaRounds         = loaVariants.size

    case object LinesOfActionMedley
        extends MedleyShield(
          "shieldLinesOfActionMedley",
          "slm",
          VariantKeys.gameFamilyName(GameFamily.LinesOfAction()),
          Condition.TeamMember("playstrategy-lines-of-action", "PlayStrategy Lines Of Action"),
          loaVariants,
          loaVariantOrder,
          Blitz53,
          Some(2),
          6,
          16,
          loaVariantMinutes,
          loaRounds,
          "",
          s"An Arena which is divided into ${loaRounds} equal length periods of ${loaVariants.init
            .map(VariantKeys.variantName)
            .mkString(", ")} and ${VariantKeys.variantName(loaVariants.last)} again.",
          s"Welcome to the ${VariantKeys.gameFamilyName(GameFamily.LinesOfAction())} Medley Arena!"
        )

    private def shogiVariantOrder(variants: List[Variant]) =
      TournamentMedleyUtil.medleyVariantsAndIntervals(
        variants,
        5 * 60,
        shogiVariantMinutes,
        shogiRounds,
        false
      )
    private val shogiVariants = List(
      Variant.wrap(strategygames.fairysf.variant.Shogi),
      Variant.wrap(strategygames.fairysf.variant.MiniShogi),
      Variant.wrap(strategygames.fairysf.variant.Shogi)
    )

    private val shogiVariantMinutes = 90
    private val shogiRounds         = shogiVariants.size

    case object ShogiMedley
        extends MedleyShield(
          "shieldShogiMedley",
          "ssm",
          VariantKeys.gameFamilyName(GameFamily.Shogi()),
          Condition.TeamMember("playstrategy-shogi", "PlayStrategy Shogi"),
          shogiVariants,
          shogiVariantOrder,
          Byoyomi510,
          Some(3),
          7,
          14,
          shogiVariantMinutes,
          shogiRounds,
          "",
          s"An Arena which is divided into ${shogiRounds} equal length periods of ${shogiVariants.init
            .map(VariantKeys.variantName)
            .mkString(", ")} and ${VariantKeys.variantName(shogiVariants.last)} again.",
          s"Welcome to the ${VariantKeys.gameFamilyName(GameFamily.Shogi())} Medley Arena!"
        )

    private def xiangqiVariantOrder(variants: List[Variant]) =
      TournamentMedleyUtil.medleyVariantsAndIntervals(
        variants,
        5 * 60,
        xiangqiVariantMinutes,
        xiangqiRounds,
        false
      )

    private val xiangqiVariants = List(
      Variant.wrap(strategygames.fairysf.variant.Xiangqi),
      Variant.wrap(strategygames.fairysf.variant.MiniXiangqi),
      Variant.wrap(strategygames.fairysf.variant.Xiangqi)
    )

    private val xiangqiVariantMinutes = 90
    private val xiangqiRounds         = xiangqiVariants.size

    case object XiangqiMedley
        extends MedleyShield(
          "shieldXiangqiMedley",
          "sxm",
          VariantKeys.gameFamilyName(GameFamily.Xiangqi()),
          Condition.TeamMember("playstrategy-xiangqi", "PlayStrategy Xiangqi"),
          xiangqiVariants,
          xiangqiVariantOrder,
          Blitz53,
          Some(2),
          7,
          14,
          xiangqiVariantMinutes,
          xiangqiRounds,
          "",
          s"An Arena which is divided into ${xiangqiRounds} equal length periods of ${xiangqiVariants.init
            .map(VariantKeys.variantName)
            .mkString(", ")} and ${VariantKeys.variantName(xiangqiVariants.last)} again.",
          s"Welcome to the ${VariantKeys.gameFamilyName(GameFamily.Xiangqi())} Medley Arena!"
        )

    private def othelloVariantOrder(variants: List[Variant]) = {
      TournamentMedleyUtil.medleyVariantsAndIntervals(
        variants,
        5 * 60,
        othelloVariantMinutes,
        othelloRounds,
        false
      )
    }

    private val othelloVariants = List(
      Variant.wrap(strategygames.fairysf.variant.Flipello),
      Variant.wrap(strategygames.fairysf.variant.Flipello10),
      Variant.wrap(strategygames.fairysf.variant.Flipello)
    )

    private val othelloVariantMinutes = 90
    private val othelloRounds         = othelloVariants.size

    case object OthelloMedley
        extends MedleyShield(
          "shieldOthelloMedley",
          "som",
          VariantKeys.gameFamilyName(GameFamily.Flipello()),
          Condition.TeamMember("playstrategy-flipello", "PlayStrategy Othello"),
          othelloVariants,
          othelloVariantOrder,
          Blitz53,
          Some(4),
          7,
          14,
          othelloVariantMinutes,
          othelloRounds,
          "",
          s"An Arena which is divided into ${othelloRounds} equal length periods of ${othelloVariants.init
            .map(VariantKeys.variantName)
            .mkString(", ")} and ${VariantKeys.variantName(othelloVariants.last)} again.",
          s"Welcome to the ${VariantKeys.gameFamilyName(GameFamily.Flipello())} Medley Arena!"
        )

    private def mancalaVariantOrder(variants: List[Variant]) = {
      TournamentMedleyUtil.medleyVariantsAndIntervals(
        Random.shuffle(variants),
        5 * 60,
        mancalaVariantMinutes,
        mancalaRounds,
        false
      )
    }

    private val mancalaVariants = List(
      Variant.wrap(strategygames.samurai.variant.Oware),
      Variant.wrap(strategygames.togyzkumalak.variant.Togyzkumalak)
    )

    private val mancalaVariantMinutes = 90
    private val mancalaRounds         = mancalaVariants.size

    case object MancalaMedley
        extends MedleyShield(
          "shieldMancalaMedley",
          "smm",
          VariantKeys.gameGroupName(GameGroup.Mancala()),
          Condition.TeamMember("playstrategy-mancala", "PlayStrategy Mancala"),
          mancalaVariants,
          mancalaVariantOrder,
          Blitz53,
          Some(1),
          7,
          14,
          mancalaVariantMinutes,
          mancalaRounds,
          "",
          s"An Arena which is divided into ${mancalaRounds} equal length periods of ${mancalaVariants.init
            .map(VariantKeys.variantName)
            .mkString(", ")} and ${VariantKeys.variantName(mancalaVariants.last)}.",
          s"Welcome to the ${VariantKeys.gameGroupName(GameGroup.Mancala())} Medley Arena!"
        )

    private def togyzkumalakVariantOrder(variants: List[Variant]) = {
      TournamentMedleyUtil.medleyVariantsAndIntervals(
        variants,
        5 * 60,
        togyzkumalakVariantMinutes,
        togyzkumalakRounds,
        false
      )
    }

    private val togyzkumalakVariants = List(
      Variant.wrap(strategygames.togyzkumalak.variant.Togyzkumalak),
      Variant.wrap(strategygames.togyzkumalak.variant.Bestemshe),
      Variant.wrap(strategygames.togyzkumalak.variant.Togyzkumalak)
    )

    private val togyzkumalakVariantMinutes = 90
    private val togyzkumalakRounds         = togyzkumalakVariants.size

    case object TogyzkumalakMedley
        extends MedleyShield(
          "shieldTogyzkumalakMedley",
          "stm",
          VariantKeys.gameFamilyName(GameFamily.Togyzkumalak()),
          Condition.TeamMember("playstrategy-mancala", "PlayStrategy Mancala"),
          togyzkumalakVariants,
          togyzkumalakVariantOrder,
          Blitz53,
          Some(3),
          6,
          16,
          togyzkumalakVariantMinutes,
          togyzkumalakRounds,
          "",
          s"An Arena which is divided into ${togyzkumalakRounds} equal length periods of ${togyzkumalakVariants.init
            .map(VariantKeys.variantName)
            .mkString(", ")} and ${VariantKeys.variantName(togyzkumalakVariants.last)} again.",
          s"Welcome to the ${VariantKeys.gameFamilyName(GameFamily.Togyzkumalak())} Medley Arena!",
          6
        )

    private def backgammonVariantOrder(variants: List[Variant]) = {
      TournamentMedleyUtil.medleyVariantsAndIntervals(
        variants,
        3 * 60,
        backgammonVariantMinutes,
        backgammonRounds,
        false
      )
    }

    private val backgammonVariants = List(
      Variant.wrap(strategygames.backgammon.variant.Backgammon),
      Variant.wrap(strategygames.backgammon.variant.Nackgammon),
      Variant.wrap(strategygames.backgammon.variant.Hyper)
    )

    private val backgammonVariantMinutes = 90
    private val backgammonRounds         = backgammonVariants.size

    case object BackgammonMedley
        extends MedleyShield(
          "shieldBackgammonMedley",
          "sbm",
          VariantKeys.gameFamilyName(GameFamily.Backgammon()),
          Condition.TeamMember("playstrategy-backgammon", "PlayStrategy Backgammon"),
          backgammonVariants,
          backgammonVariantOrder,
          Delay310,
          Some(1),
          6,
          16,
          backgammonVariantMinutes,
          backgammonRounds,
          "",
          s"An Arena which is divided into ${backgammonRounds} equal length periods of ${backgammonVariants.init
            .map(VariantKeys.variantName)
            .mkString(", ")} and ${VariantKeys.variantName(backgammonVariants.last)}.",
          s"Welcome to the ${VariantKeys.gameFamilyName(GameFamily.Backgammon())} Medley Arena!"
        )

    private def breakthroughVariantOrder(variants: List[Variant]) = {
      TournamentMedleyUtil.medleyVariantsAndIntervals(
        variants,
        5 * 60,
        breakthroughVariantMinutes,
        breakthroughRounds,
        false
      )
    }

    private val breakthroughVariants = List(
      Variant.wrap(strategygames.fairysf.variant.BreakthroughTroyka),
      Variant.wrap(strategygames.fairysf.variant.MiniBreakthroughTroyka),
      Variant.wrap(strategygames.fairysf.variant.BreakthroughTroyka)
    )

    private val breakthroughVariantMinutes = 90
    private val breakthroughRounds         = breakthroughVariants.size

    case object BreakthroughMedley
        extends MedleyShield(
          "shieldBreakthroughMedley",
          "sbtm",
          VariantKeys.gameFamilyName(GameFamily.BreakthroughTroyka()),
          Condition.TeamMember("playstrategy-breakthrough", "PlayStrategy Breakthrough"),
          breakthroughVariants,
          breakthroughVariantOrder,
          Blitz53,
          Some(4),
          6,
          16,
          breakthroughVariantMinutes,
          breakthroughRounds,
          "",
          s"An Arena which is divided into ${breakthroughRounds} equal length periods of ${breakthroughVariants.init
            .map(VariantKeys.variantName)
            .mkString(", ")} and ${VariantKeys.variantName(breakthroughVariants.last)} again.",
          s"Welcome to the ${VariantKeys.gameFamilyName(GameFamily.BreakthroughTroyka())} Medley Arena!",
          5
        )

    //all the order permuations which doesnt put two chess or two backgammon next to each other
    private val chessgammonVariantPermuations = List(
      List(0, 1, 2, 3),
      List(2, 1, 0, 3),
      List(0, 3, 2, 1),
      List(2, 3, 0, 1),
      List(3, 2, 1, 0),
      List(3, 0, 1, 2),
      List(1, 2, 3, 0),
      List(1, 0, 3, 2)
    )

    private def chessgammonMedleyGeneration(variants: List[Variant]) = {
      TournamentMedleyUtil.medleyVariantsAndIntervals(
        Random.shuffle(chessgammonVariantPermuations).head.map(i => variants(i)),
        3 * 60,
        chessgammonMinutes,
        chessgammonRounds,
        false
      )
    }
    private val chessgammonVariants = List(
      Variant.wrap(strategygames.chess.variant.Standard),
      Variant.wrap(strategygames.backgammon.variant.Backgammon),
      Variant.wrap(strategygames.chess.variant.Chess960),
      Variant.wrap(strategygames.backgammon.variant.Nackgammon)
    )

    private val chessgammonMinutes = 80
    private val chessgammonRounds  = chessgammonVariants.size

    case object ChessgammonMedley
        extends MedleyShield(
          "shieldChessgammonMedley",
          "scgm",
          "Chessgammon",
          Condition.TeamMember("playstrategy-medleys", "PlayStrategy Medleys"),
          chessgammonVariants,
          chessgammonMedleyGeneration,
          Delay310,
          Some(1),
          7,
          16,
          chessgammonMinutes,
          chessgammonRounds,
          "",
          s"An Arena which is divided into ${chessgammonRounds} equal length periods of ${chessgammonVariants
            .map(VariantKeys.variantName)
            .mkString(", ")}.",
          s"Welcome to the Chessgammon Medley Arena!",
          2
        )

    val all = List(
      PlayStrategyMedley,  //Weekly - Sun evenings
      ChessVariantsMedley, //Weekly - Sat evenings
      DraughtsMedley,      //Weekly - Sat lunchtime
      LinesOfActionMedley, //Monthly - 2nd Sat afternoon
      ShogiMedley,         //Monthly - 3rd Sun lunchtime
      XiangqiMedley,       //Monthly - 2nd Sun lunchtime
      OthelloMedley,       //Monthly - 4th Sun lunchtime
      MancalaMedley,       //Monthly - 1st Sun lunchtime
      TogyzkumalakMedley,  //Monthly - 3rd Sat lunchtime
      BackgammonMedley,    //Monthly - 1st Sat afternoon
      BreakthroughMedley,  //Monthly - 4rd Sat afternoon
      ChessgammonMedley    //Monthly - 1st Sun afternoon
    )

    val allWeekly  = all.filter(_.weekOfMonth.isEmpty)
    val allMonthly = all.filter(_.weekOfMonth.nonEmpty)

    val medleyTeamIDs = all.map(_.teamOwner.teamId)

    def byKey(k: String): Option[MedleyShield] = all.find(_.key == k)

    private val medleyStartDate              = new DateTime(2022, 6, 11, 0, 0)
    private val arenaMedleyStartDate         = new DateTime(2022, 8, 7, 22, 0)
    private val monthlyMedleyShieldStartDate = new DateTime(2024, 4, 1, 0, 0)

    def weeksSinceStart(startsAt: DateTime) =
      Weeks.weeksBetween(medleyStartDate, startsAt).getWeeks()

    def monthsSinceStart(startsAt: DateTime) =
      Months.monthsBetween(monthlyMedleyShieldStartDate, startsAt).getMonths()

    def countSinceStart(startsAt: DateTime, isWeekly: Boolean) =
      if (isWeekly) weeksSinceStart(startsAt)
      else monthsSinceStart(startsAt)

    def makeName(baseName: String, startsAt: DateTime, isWeekly: Boolean, countOffset: Int) =
      s"${baseName} ${countSinceStart(startsAt, isWeekly) - countOffset + 1}"
  }

  sealed abstract class Category(
      val variant: Variant,
      val speed: Schedule.Speed,
      val dayOfMonth: Int,
      val group: Int = 0
  ) {
    def key                       = variant.key
    def name                      = VariantKeys.variantName(variant)
    def iconChar                  = variant.perfIcon
    def matches(tour: Tournament) = Some(variant).has(tour.variant)

    private def hoursList(month: Int) =
      if (month % 2 == 0) TournamentShield.defaultShieldHours
      else TournamentShield.alternateShieldHours

    def scheduleHour(month: Int) =
      hoursList(month).lift(group).getOrElse(TournamentShield.defaultShieldHours(0))
  }

  object Category {

    case object Chess
        extends Category(
          Variant.Chess(strategygames.chess.variant.Standard),
          Blitz32,
          18
        )

    case object Chess960
        extends Category(
          Variant.Chess(strategygames.chess.variant.Chess960),
          Blitz32,
          2
        )

    case object KingOfTheHill
        extends Category(
          Variant.Chess(strategygames.chess.variant.KingOfTheHill),
          Blitz32,
          14
        )

    case object Antichess
        extends Category(
          Variant.Chess(strategygames.chess.variant.Antichess),
          Blitz32,
          24
        )

    case object Atomic
        extends Category(
          Variant.Chess(strategygames.chess.variant.Atomic),
          Blitz32,
          8
        )

    case object ThreeCheck
        extends Category(
          Variant.Chess(strategygames.chess.variant.ThreeCheck),
          Blitz32,
          11
        )

    case object FiveCheck
        extends Category(
          Variant.Chess(strategygames.chess.variant.FiveCheck),
          Blitz32,
          28
        )

    case object Horde
        extends Category(
          Variant.Chess(strategygames.chess.variant.Horde),
          Blitz32,
          16
        )

    case object RacingKings
        extends Category(
          Variant.Chess(strategygames.chess.variant.RacingKings),
          Blitz32,
          5
        )

    case object Crazyhouse
        extends Category(
          Variant.Chess(strategygames.chess.variant.Crazyhouse),
          Blitz32,
          21
        )

    case object NoCastling
        extends Category(
          Variant.Chess(strategygames.chess.variant.NoCastling),
          Blitz32,
          26
        )

    case object Monster
        extends Category(
          Variant.Chess(strategygames.chess.variant.Monster),
          Blitz32,
          23,
          1
        )

    case object LinesOfAction
        extends Category(
          Variant.Chess(strategygames.chess.variant.LinesOfAction),
          Blitz32,
          1
        )

    case object ScrambledEggs
        extends Category(
          Variant.Chess(strategygames.chess.variant.ScrambledEggs),
          Blitz32,
          17
        )

    case object International
        extends Category(
          Variant.Draughts(strategygames.draughts.variant.Standard),
          Blitz32,
          3
        )

    case object Frisian
        extends Category(
          Variant.Draughts(strategygames.draughts.variant.Frisian),
          Blitz32,
          12
        )

    case object Frysk
        extends Category(
          Variant.Draughts(strategygames.draughts.variant.Frysk),
          Blitz32,
          22
        )

    case object Antidraughts
        extends Category(
          Variant.Draughts(strategygames.draughts.variant.Antidraughts),
          Blitz32,
          9
        )

    case object Breakthrough
        extends Category(
          Variant.Draughts(strategygames.draughts.variant.Breakthrough),
          Blitz32,
          19
        )

    case object Russian
        extends Category(
          Variant.Draughts(strategygames.draughts.variant.Russian),
          Blitz32,
          6
        )

    case object Brazilian
        extends Category(
          Variant.Draughts(strategygames.draughts.variant.Brazilian),
          Blitz32,
          15
        )

    case object Pool
        extends Category(
          Variant.Draughts(strategygames.draughts.variant.Pool),
          Blitz32,
          25
        )

    case object Portuguese
        extends Category(
          Variant.Draughts(strategygames.draughts.variant.Portuguese),
          Blitz32,
          27,
          1
        )

    case object English
        extends Category(
          Variant.Draughts(strategygames.draughts.variant.English),
          Blitz32,
          28,
          1
        )

    case object Shogi
        extends Category(
          Variant.FairySF(strategygames.fairysf.variant.Shogi),
          Byoyomi510,
          4
        )

    case object Xiangqi
        extends Category(
          Variant.FairySF(strategygames.fairysf.variant.Xiangqi),
          Blitz53,
          23
        )

    case object MiniShogi
        extends Category(
          Variant.FairySF(strategygames.fairysf.variant.MiniShogi),
          Byoyomi35,
          13
        )

    case object MiniXiangqi
        extends Category(
          Variant.FairySF(strategygames.fairysf.variant.MiniXiangqi),
          Blitz32,
          7
        )

    case object Flipello
        extends Category(
          Variant.FairySF(strategygames.fairysf.variant.Flipello),
          Blitz32,
          10
        )

    case object Flipello10
        extends Category(
          Variant.FairySF(strategygames.fairysf.variant.Flipello10),
          Blitz32,
          27
        )

    case object Amazons
        extends Category(
          Variant.FairySF(strategygames.fairysf.variant.Amazons),
          Blitz32,
          25,
          1
        )

    case object BreakthroughTroyka
        extends Category(
          Variant.FairySF(strategygames.fairysf.variant.BreakthroughTroyka),
          Blitz32,
          21,
          1
        )

    case object MiniBreakthroughTroyka
        extends Category(
          Variant.FairySF(strategygames.fairysf.variant.MiniBreakthroughTroyka),
          Blitz32,
          8,
          1
        )

    case object Oware
        extends Category(
          Variant.Samurai(strategygames.samurai.variant.Oware),
          Blitz32,
          20
        )

    case object Togyzkumalak
        extends Category(
          Variant.Togyzkumalak(strategygames.togyzkumalak.variant.Togyzkumalak),
          Blitz53,
          26,
          1
        )
    case object Bestemshe
        extends Category(
          Variant.Togyzkumalak(strategygames.togyzkumalak.variant.Bestemshe),
          Blitz32,
          10,
          1
        )
    case object Go9x9
        extends Category(
          Variant.Go(strategygames.go.variant.Go9x9),
          Blitz32,
          24,
          1
        )
    case object Go13x13
        extends Category(
          Variant.Go(strategygames.go.variant.Go13x13),
          Blitz53,
          12,
          1
        )
    case object Go19x19
        extends Category(
          Variant.Go(strategygames.go.variant.Go19x19),
          Blitz53,
          1,
          1
        )
    case object Backgammon
        extends Category(
          Variant.Backgammon(strategygames.backgammon.variant.Backgammon),
          Delay310,
          22,
          1
        )
    case object Hyper
        extends Category(
          Variant.Backgammon(strategygames.backgammon.variant.Hyper),
          Delay310,
          17,
          1
        )
    case object Nackgammon
        extends Category(
          Variant.Backgammon(strategygames.backgammon.variant.Nackgammon),
          Delay310,
          7,
          1
        )
    case object Abalone
        extends Category(
          Variant.Abalone(strategygames.abalone.variant.Abalone),
          Blitz53,
          2
        )

    val all: List[Category] = List(
      Chess,
      Chess960,
      Crazyhouse,
      KingOfTheHill,
      ThreeCheck,
      FiveCheck,
      Antichess,
      Atomic,
      Horde,
      RacingKings,
      NoCastling,
      Monster,
      LinesOfAction,
      ScrambledEggs,
      International,
      Frisian,
      Frysk,
      Antidraughts,
      Breakthrough,
      Russian,
      Brazilian,
      Pool,
      Portuguese,
      English,
      Shogi,
      Xiangqi,
      MiniShogi,
      MiniXiangqi,
      Flipello,
      Flipello10,
      Amazons,
      BreakthroughTroyka,
      MiniBreakthroughTroyka,
      Oware,
      Togyzkumalak,
      Bestemshe,
      Go9x9,
      Go13x13,
      Go19x19,
      Backgammon,
      Hyper,
      Nackgammon,
      Abalone
    )

    def of(t: Tournament): Option[Category] = all.find(_ matches t)

    def byKey(k: String): Option[Category] = all.find(_.key == k)
  }

  val defaultShieldHours   = List(18, 12) //UTC
  val alternateShieldHours = defaultShieldHours.reverse

  def spotlight(name: String, icon: Char) =
    Spotlight(
      iconFont = icon.toString.some,
      headline = "Monthly battle for the variant shield",
      description =
        s"The winner keeps the shield trophy for one month, and then must defend it during the next $name Shield tournament!",
      homepageHours = 168.some
    )
}
