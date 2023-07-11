package lila.history

import org.joda.time.{ DateTime, Days }
import reactivemongo.api.ReadPreference
import reactivemongo.api.bson._
import scala.concurrent.duration._

import strategygames.Speed
import lila.db.dsl._
import lila.game.Game
import lila.rating.{ Perf, PerfType }
import lila.user.{ Perfs, User, UserRepo }

final class HistoryApi(coll: Coll, userRepo: UserRepo, cacheApi: lila.memo.CacheApi)(implicit
    ec: scala.concurrent.ExecutionContext
) {

  import History._

  def addPuzzle(user: User, completedAt: DateTime, perf: Perf): Funit = {
    val days = daysBetween(user.createdAt, completedAt)
    coll.update
      .one(
        $id(user.id),
        $set(s"puzzle.$days" -> $int(perf.intRating)),
        upsert = true
      )
      .void
  }

  def add(user: User, game: Game, perfs: Perfs): Funit = {
    val isStd = game.ratingVariant.standard
    val changes = List(
      isStd.option("standard"                                               -> perfs.standard),
      game.ratingVariant.chess960.option("chess960"                         -> perfs.chess960),
      game.ratingVariant.kingOfTheHill.option("kingOfTheHill"               -> perfs.kingOfTheHill),
      game.ratingVariant.threeCheck.option("threeCheck"                     -> perfs.threeCheck),
      game.ratingVariant.fiveCheck.option("fiveCheck"                       -> perfs.fiveCheck),
      game.ratingVariant.antichess.option("antichess"                       -> perfs.antichess),
      game.ratingVariant.atomic.option("atomic"                             -> perfs.atomic),
      game.ratingVariant.horde.option("horde"                               -> perfs.horde),
      game.ratingVariant.racingKings.option("racingKings"                   -> perfs.racingKings),
      game.ratingVariant.crazyhouse.option("crazyhouse"                     -> perfs.crazyhouse),
      game.ratingVariant.noCastling.option("noCastling"                     -> perfs.noCastling),
      game.ratingVariant.linesOfAction.option("linesOfAction"               -> perfs.linesOfAction),
      game.ratingVariant.scrambledEggs.option("scrambledEggs"               -> perfs.scrambledEggs),
      game.ratingVariant.draughtsStandard.option("international"            -> perfs.international),
      game.ratingVariant.frisian.option("frisian"                           -> perfs.frisian),
      game.ratingVariant.frysk.option("frysk"                               -> perfs.frysk),
      game.ratingVariant.antidraughts.option("antidraughts"                 -> perfs.antidraughts),
      game.ratingVariant.breakthrough.option("breakthrough"                 -> perfs.breakthrough),
      game.ratingVariant.russian.option("russian"                           -> perfs.russian),
      game.ratingVariant.brazilian.option("brazilian"                       -> perfs.brazilian),
      game.ratingVariant.pool.option("pool"                                 -> perfs.pool),
      game.ratingVariant.portuguese.option("portuguese"                     -> perfs.portuguese),
      game.ratingVariant.english.option("english"                           -> perfs.english),
      game.ratingVariant.shogi.option("shogi"                               -> perfs.shogi),
      game.ratingVariant.xiangqi.option("xiangqi"                           -> perfs.xiangqi),
      game.ratingVariant.minishogi.option("minishogi"                       -> perfs.minishogi),
      game.ratingVariant.minixiangqi.option("minixiangqi"                   -> perfs.minixiangqi),
      game.ratingVariant.flipello.option("flipello"                         -> perfs.flipello),
      game.ratingVariant.flipello10.option("flipello10"                     -> perfs.flipello10),
      game.ratingVariant.amazons.option("amazons"                           -> perfs.amazons),
      game.ratingVariant.oware.option("oware"                               -> perfs.oware),
      game.ratingVariant.togyzkumalak.option("togyzkumalak"                 -> perfs.togyzkumalak),
      (isStd && game.speed == Speed.UltraBullet).option("ultraBullet"       -> perfs.ultraBullet),
      (isStd && game.speed == Speed.Bullet).option("bullet"                 -> perfs.bullet),
      (isStd && game.speed == Speed.Blitz).option("blitz"                   -> perfs.blitz),
      (isStd && game.speed == Speed.Rapid).option("rapid"                   -> perfs.rapid),
      (isStd && game.speed == Speed.Classical).option("classical"           -> perfs.classical),
      (isStd && game.speed == Speed.Correspondence).option("correspondence" -> perfs.correspondence)
    ).flatten.map { case (k, p) =>
      k -> p.intRating
    }
    val days = daysBetween(user.createdAt, game.updatedAt)
    coll.update
      .one(
        $id(user.id),
        $doc("$set" -> $doc(changes.map { case (perf, rating) =>
          (s"$perf.$days", $int(rating))
        })),
        upsert = true
      )
      .void
  }

  // used for rating refunds
  def setPerfRating(user: User, perf: PerfType, rating: Int): Funit = {
    val days = daysBetween(user.createdAt, DateTime.now)
    coll.update
      .one(
        $id(user.id),
        $set(s"${perf.key}.$days" -> $int(rating))
      )
      .void
  }

  private def daysBetween(from: DateTime, to: DateTime): Int =
    Days.daysBetween(from.withTimeAtStartOfDay, to.withTimeAtStartOfDay).getDays

  def get(userId: String): Fu[Option[History]] = coll.one[History]($id(userId))

  def ratingsMap(user: User, perf: PerfType): Fu[RatingsMap] =
    coll.primitiveOne[RatingsMap]($id(user.id), perf.key) dmap (~_)

  def progresses(users: List[User], perfType: PerfType, days: Int): Fu[List[(Int, Int)]] =
    coll.optionsByOrderedIds[Bdoc, User.ID](
      users.map(_.id),
      $doc(perfType.key -> true).some,
      ReadPreference.secondaryPreferred
    )(~_.string("_id")) map { hists =>
      users zip hists map { case (user, doc) =>
        val current      = user.perfs(perfType).intRating
        val previousDate = daysBetween(user.createdAt, DateTime.now minusDays days)
        val previous =
          doc.flatMap(_ child perfType.key).flatMap(RatingsMapReader.readOpt).fold(current) { hist =>
            hist.foldLeft(hist.headOption.fold(current)(_._2)) {
              case (_, (d, r)) if d < previousDate => r
              case (acc, _)                        => acc
            }
          }
        previous -> current
      }
    }

  object lastWeekTopRating {

    def apply(user: User, perf: PerfType): Fu[Int] = cache.get(user.id -> perf)

    private val cache = cacheApi[(User.ID, PerfType), Int](1024, "lastWeekTopRating") {
      _.expireAfterAccess(20 minutes)
        .buildAsyncFuture { case (userId, perf) =>
          userRepo.byId(userId) orFail s"No such user: $userId" flatMap { user =>
            val currentRating = user.perfs(perf).intRating
            val firstDay      = daysBetween(user.createdAt, DateTime.now minusWeeks 1)
            val days          = firstDay to (firstDay + 6) toList
            val project = BSONDocument {
              ("_id" -> BSONBoolean(false)) :: days.map { d =>
                s"${perf.key}.$d" -> BSONBoolean(true)
              }
            }
            coll.find($id(user.id), project.some).one[Bdoc](ReadPreference.secondaryPreferred).map {
              _.flatMap {
                _.child(perf.key) map {
                  _.elements.foldLeft(currentRating) {
                    case (max, BSONElement(_, BSONInteger(v))) if v > max => v
                    case (max, _)                                         => max
                  }
                }
              }
            } dmap { _ | currentRating }
          }
        }
    }
  }
}
