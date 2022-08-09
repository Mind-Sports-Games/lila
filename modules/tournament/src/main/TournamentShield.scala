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
import strategygames.GameFamily

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
      Category.all map { categ =>
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
      val generateVariants: List[Variant] => List[Variant],
      val dayOfWeek: Int,
      val hour: Int,
      val arenaMinutes: Int,
      val arenaMedleyMinutes: Int,
      val swissFormat: String,
      val arenaFormat: String,
      val arenaDescription: String
  ) {
    def hasAllVariants  = eligibleVariants == Variant.all
    def medleyName      = s"${name} Medley Shield"
    def url             = s"https://playstrategy.org/tournament/medley-shield/${key}"
    def arenaFormatFull = s"${arenaFormat} Each variant is active for ${arenaMedleyMinutes} minutes."
    def arenaDescriptionFull =
      s"${arenaDescription}\r\n\r\nWin the tournament, win the shield... until next week!\r\n\r\nMore info here: ${url}"
  }

  object MedleyShield {

    private def playStrategyMedleyGeneration(variants: List[Variant]) = {
      val thisOrder =
        Random.shuffle(variants)
      val onePerGameFamily =
        Random.shuffle(GameFamily.all.map(gf => thisOrder.filter(_.gameFamily == gf).head))
      onePerGameFamily ::: thisOrder.filterNot(onePerGameFamily.contains(_))
    }
    private val playStrategyRounds = 7

    case object PlayStrategyMedley
        extends MedleyShield(
          "shieldPlayStrategyMedley",
          "PlayStrategy",
          Condition.TeamMember("playstrategy-medleys", "PlayStrategy Medleys"),
          Variant.all.filterNot(_.fromPositionVariant),
          playStrategyMedleyGeneration,
          7,
          19,
          105,
          15,
          s"${playStrategyRounds} round Swiss with one game from each of the ${GameFamily.all.length} Game Families picked: ${GameFamily.all.map(VariantKeys.gameFamilyName).sorted.mkString(", ")}.",
          s"${playStrategyRounds} variant Arena with one game from each of the ${GameFamily.all.length} Game Families picked: ${GameFamily.all.map(VariantKeys.gameFamilyName).sorted.mkString(", ")}.",
          s"PlayStrategy Medley Arena with one game from each of the ${GameFamily.all.length} Game Families picked: ${GameFamily.all.map(VariantKeys.gameFamilyName).sorted.mkString(", ")}."
        )

    private def randomVariantOrder(variants: List[Variant]) = Random.shuffle(variants)

    private val chessVariantOptions = Variant.all.filter(_.exoticChessVariant)
    private val chessVariantRounds  = 5

    case object ChessVariantsMedley
        extends MedleyShield(
          "shieldChessMedley",
          "Chess Variants",
          Condition.TeamMember("playstrategy-chess-variants", "PlayStrategy Chess Variants"),
          chessVariantOptions,
          randomVariantOrder,
          6,
          19,
          100,
          20,
          s"${chessVariantRounds} round Swiss using micro-match rounds (each pairing plays twice, once each as the start player). ${chessVariantRounds} from the ${chessVariantOptions.length} listed chess variants will be picked.",
          s"${chessVariantRounds} variant Arena where ${chessVariantRounds} from the ${chessVariantOptions.length} listed chess variants are picked.",
          s"Chess Variants Medley Arena, where ${chessVariantRounds} from the following ${chessVariantOptions.length} chess variants are picked: ${chessVariantOptions.map(VariantKeys.variantName).mkString(", ")}."
        )

    private val draughtsVariantOptions =
      Variant.all.filter(_.gameFamily == GameFamily.Draughts()).filterNot(_.fromPositionVariant)
    private val draughtsRounds = 7

    case object DraughtsMedley
        extends MedleyShield(
          "shieldDraughtsMedley",
          "Draughts",
          Condition.TeamMember("playstrategy-draughts", "PlayStrategy Draughts"),
          draughtsVariantOptions,
          randomVariantOrder,
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

    def byKey(k: String): Option[MedleyShield] = all.find(_.key == k)

    private val medleyStartDate = new DateTime(2022, 6, 10, 0, 0)
    val arenaMedleyStartDate    = new DateTime(2022, 8, 7, 22, 0)

    def weeksSinceStart = Weeks.weeksBetween(medleyStartDate, DateTime.now()).getWeeks()

    def makeName(baseName: String) = s"${baseName} ${weeksSinceStart + 1}"
  }

  sealed abstract class Category(
      val variant: Variant
  ) {
    def key                       = variant.key
    def name                      = VariantKeys.variantName(variant)
    def iconChar                  = variant.perfIcon
    def matches(tour: Tournament) = Some(variant).has(tour.variant)
  }

  object Category {

    case object Chess
        extends Category(
          Variant.Chess(strategygames.chess.variant.Standard)
        )

    case object Chess960
        extends Category(
          Variant.Chess(strategygames.chess.variant.Chess960)
        )

    case object KingOfTheHill
        extends Category(
          Variant.Chess(strategygames.chess.variant.KingOfTheHill)
        )

    case object Antichess
        extends Category(
          Variant.Chess(strategygames.chess.variant.Antichess)
        )

    case object Atomic
        extends Category(
          Variant.Chess(strategygames.chess.variant.Atomic)
        )

    case object ThreeCheck
        extends Category(
          Variant.Chess(strategygames.chess.variant.ThreeCheck)
        )

    case object FiveCheck
        extends Category(
          Variant.Chess(strategygames.chess.variant.FiveCheck)
        )

    case object Horde
        extends Category(
          Variant.Chess(strategygames.chess.variant.Horde)
        )

    case object RacingKings
        extends Category(
          Variant.Chess(strategygames.chess.variant.RacingKings)
        )

    case object Crazyhouse
        extends Category(
          Variant.Chess(strategygames.chess.variant.Crazyhouse)
        )

    case object NoCastling
        extends Category(
          Variant.Chess(strategygames.chess.variant.NoCastling)
        )

    case object LinesOfAction
        extends Category(
          Variant.Chess(strategygames.chess.variant.LinesOfAction)
        )

    case object ScrambledEggs
        extends Category(
          Variant.Chess(strategygames.chess.variant.ScrambledEggs)
        )

    case object International
        extends Category(
          Variant.Draughts(strategygames.draughts.variant.Standard)
        )

    case object Frisian
        extends Category(
          Variant.Draughts(strategygames.draughts.variant.Frisian)
        )

    case object Frysk
        extends Category(
          Variant.Draughts(strategygames.draughts.variant.Frysk)
        )

    case object Antidraughts
        extends Category(
          Variant.Draughts(strategygames.draughts.variant.Antidraughts)
        )

    case object Breakthrough
        extends Category(
          Variant.Draughts(strategygames.draughts.variant.Breakthrough)
        )

    case object Russian
        extends Category(
          Variant.Draughts(strategygames.draughts.variant.Russian)
        )

    case object Brazilian
        extends Category(
          Variant.Draughts(strategygames.draughts.variant.Brazilian)
        )

    case object Pool
        extends Category(
          Variant.Draughts(strategygames.draughts.variant.Pool)
        )

    case object Shogi
        extends Category(
          Variant.FairySF(strategygames.fairysf.variant.Shogi)
        )

    case object Xiangqi
        extends Category(
          Variant.FairySF(strategygames.fairysf.variant.Xiangqi)
        )

    case object MiniShogi
        extends Category(
          Variant.FairySF(strategygames.fairysf.variant.MiniShogi)
        )

    case object MiniXiangqi
        extends Category(
          Variant.FairySF(strategygames.fairysf.variant.MiniXiangqi)
        )

    case object Flipello
        extends Category(
          Variant.FairySF(strategygames.fairysf.variant.Flipello)
        )

    case object Flipello10
        extends Category(
          Variant.FairySF(strategygames.fairysf.variant.Flipello10)
        )

    case object Oware
        extends Category(
          Variant.Mancala(strategygames.mancala.variant.Oware)
        )

    val all: List[Category] = List(
      Chess,
      Chess960,
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
      Shogi,
      Xiangqi,
      MiniShogi,
      MiniXiangqi,
      Flipello,
      Flipello10,
      Oware
    )

    def of(t: Tournament): Option[Category] = all.find(_ matches t)

    def byKey(k: String): Option[Category] = all.find(_.key == k)
  }

  def spotlight(name: String) =
    Spotlight(
      iconFont = "5".some,
      headline = s"Battle for the $name Shield",
      description = s"""This [Shield trophy] is unique.
The winner keeps it for one month,
then must defend it during the next $name Shield tournament!""",
      homepageHours = 6.some
    )
}
