package lila.history

import org.joda.time.{ DateTime, Days }
import reactivemongo.api.ReadPreference
import reactivemongo.api.bson._
import scala.concurrent.duration._

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
    val variantPerf = Perfs.variantLens(game.ratingVariant).map(_(perfs))
    val speedPerf =
      if (variantPerf == Some(perfs.standard)) Some(Perfs.speedLens(game.speed)(perfs))
      else None
    val changes = List(
      variantPerf.map(game.ratingVariant.key -> _),
      speedPerf.map(game.speed.key           -> _)
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
