package lila.tournament

import org.joda.time.{ DateTime, Weeks }
import reactivemongo.api.ReadPreference
import scala.concurrent.duration._
import scala.util.Random

import lila.db.dsl._
import lila.user.User
import lila.memo.CacheApi._
import lila.i18n.VariantKeys

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
      val name: String,
      val teamOwner: Condition.TeamMember,
      val eligibleVariants: List[Variant],
      val generateVariants: List[Variant] => List[(Variant, Int)],
      val dayOfWeek: Int,
      val hour: Int,
      val arenaMinutes: Int,
      val arenaMedleyMinutes: Int,
      val swissFormat: String,
      val arenaFormat: String,
      val arenaDescription: String
  ) {
    def hasAllVariants  = eligibleVariants == Variant.all.filterNot(_.fromPositionVariant)
    def medleyName      = s"${name} Medley Shield"
    def url             = s"https://playstrategy.org/tournament/medley-shield/${key}"
    def arenaFormatFull = s"${arenaFormat} Each variant is active for ${arenaMedleyMinutes} minutes."
    def arenaDescriptionFull =
      s"${arenaDescription}\r\n\r\nWin the tournament, win the shield... until next week!\r\n\r\nMore info here: ${url}"
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
        playStrategymMinutes,
        playStrategyRounds,
        true
      )
    }
    private val playStrategyMinutes  = 120
    private val playStrategymMinutes = 15
    private val playStrategyRounds   = 8

    case object PlayStrategyMedley
        extends MedleyShield(
          "shieldPlayStrategyMedley",
          "PlayStrategy",
          Condition.TeamMember("playstrategy-medleys", "PlayStrategy Medleys"),
          Variant.all.filterNot(_.fromPositionVariant),
          playStrategyMedleyGeneration,
          7,
          19,
          playStrategyMinutes,
          playStrategymMinutes,
          s"${playStrategyRounds} round Swiss with one game from each of the ${GameGroup.medley.length} Game Families picked: ${GameGroup.medley.map(VariantKeys.gameGroupName).sorted.mkString(", ")}.",
          s"${playStrategyRounds} variant Arena with one game from each of the ${GameGroup.medley.length} Game Families picked: ${GameGroup.medley.map(VariantKeys.gameGroupName).sorted.mkString(", ")}.",
          s"PlayStrategy Medley Arena with one game from each of the ${GameGroup.medley.length} Game Families picked: ${GameGroup.medley.map(VariantKeys.gameGroupName).sorted.mkString(", ")}."
        )

    private def randomChessVariantOrder(variants: List[Variant]) = {
      val orderedVariants = Random.shuffle(variants)
      TournamentMedleyUtil.medleyVariantsAndIntervals(
        orderedVariants,
        5 * 60,
        100,
        20,
        5,
        true
      )
    }

    private val chessVariantOptions = Variant.all.filter(_.exoticChessVariant)
    private val chessVariantRounds  = 5

    case object ChessVariantsMedley
        extends MedleyShield(
          "shieldChessMedley",
          "Chess Variants",
          Condition.TeamMember("playstrategy-chess-variants", "PlayStrategy Chess Variants"),
          chessVariantOptions,
          randomChessVariantOrder,
          6,
          19,
          100,
          20,
          s"${chessVariantRounds} round Swiss using micro-match rounds (each pairing plays twice, once each as the start player). ${chessVariantRounds} from the ${chessVariantOptions.length} listed chess variants will be picked.",
          s"${chessVariantRounds} variant Arena where ${chessVariantRounds} from the ${chessVariantOptions.length} listed chess variants are picked.",
          s"Chess Variants Medley Arena, where ${chessVariantRounds} from the following ${chessVariantOptions.length} chess variants are picked: ${chessVariantOptions.map(VariantKeys.variantName).mkString(", ")}."
        )

    private def randomDraughtsVariantOrder(variants: List[Variant]) = {
      val orderedVariants = Random.shuffle(variants)
      TournamentMedleyUtil.medleyVariantsAndIntervals(
        orderedVariants,
        5 * 60,
        105,
        15,
        7,
        true
      )
    }
    private val draughtsVariantOptions =
      Variant.all.filter(_.gameFamily == GameFamily.Draughts()).filterNot(_.fromPositionVariant)
    private val draughtsRounds = 7

    case object DraughtsMedley
        extends MedleyShield(
          "shieldDraughtsMedley",
          "Draughts",
          Condition.TeamMember("playstrategy-draughts", "PlayStrategy Draughts"),
          draughtsVariantOptions,
          randomDraughtsVariantOrder,
          6,
          13,
          105,
          15,
          s"${draughtsRounds} round Swiss where ${draughtsRounds} from the ${draughtsVariantOptions.length} listed draughts variants will be picked.",
          s"${draughtsRounds} variant Arena where ${draughtsRounds} from the ${draughtsVariantOptions.length} listed draughts variants are picked.",
          s"Draughts Medley Arena, where ${draughtsRounds} from the following ${draughtsVariantOptions.length} Draughts variants are picked: ${draughtsVariantOptions.map(VariantKeys.variantName).mkString(", ")}."
        )

    val all = List(
      PlayStrategyMedley,
      ChessVariantsMedley,
      DraughtsMedley
    )

    val medleyTeamIDs = all.map(_.teamOwner.teamId)

    def byKey(k: String): Option[MedleyShield] = all.find(_.key == k)

    private val medleyStartDate = new DateTime(2022, 6, 11, 0, 0)
    val arenaMedleyStartDate    = new DateTime(2022, 8, 7, 22, 0)

    def weeksSinceStart(startsAt: DateTime) =
      Weeks.weeksBetween(medleyStartDate, startsAt).getWeeks()

    def makeName(baseName: String, startsAt: DateTime) =
      s"${baseName} ${weeksSinceStart(startsAt) + 1}"
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

    import Schedule.Speed._

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
      Oware,
      Togyzkumalak
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
