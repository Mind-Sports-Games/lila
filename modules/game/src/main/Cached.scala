package lila.game

import com.github.blemale.scaffeine.LoadingCache
import scala.concurrent.duration._

import lila.db.dsl._
import lila.memo.{ CacheApi, MongoCache }
import lila.user.User

final class Cached(
    gameRepo: GameRepo,
    cacheApi: CacheApi,
    mongoCache: MongoCache.Api
)(implicit ec: scala.concurrent.ExecutionContext) {

  def nbImportedBy(userId: User.ID): Fu[Int] = nbImportedCache.get(userId)
  def clearNbImportedByCache                 = nbImportedCache invalidate _

  def nbTotal: Fu[Long]                               = nbTotalCache.get {}
  def monthlyGames: Fu[List[(String, String, Long)]]  = monthlyGamesCache.get {}
  def gameWinRates: Fu[List[(String, Int, Int, Int)]] = gameWinRatesCache.get {}

  def nbPlaying = nbPlayingCache.get _
  //def nbCorrespondencePlaying = nbCorrespondencePlayingCache.get {}

  def lastPlayedPlayingId(userId: User.ID): Fu[Option[Game.ID]] = lastPlayedPlayingIdCache get userId

  private val lastPlayedPlayingIdCache: LoadingCache[User.ID, Fu[Option[Game.ID]]] =
    CacheApi.scaffeineNoScheduler
      .expireAfterWrite(5 seconds)
      .build(gameRepo.lastPlayedPlayingId)

  lila.common.Bus.subscribeFun("startGame") { case lila.game.actorApi.StartGame(game) =>
    game.userIds foreach lastPlayedPlayingIdCache.invalidate
  }

  private val nbPlayingCache = cacheApi[User.ID, Int](256, "game.nbPlaying") {
    _.expireAfterWrite(15 seconds)
      .buildAsyncFuture { userId =>
        gameRepo.coll.countSel(Query nowPlaying userId)
      }
  }

  // private val nbCorrespondencePlayingCache = mongoCache.unit[Int](
  //   "game.nbCorrespondingPlaying",
  //   1.minutes
  // ) { loader =>
  //   _.refreshAfterWrite(2.minutes)
  //     .buildAsyncFuture {
  //       loader { _ =>
  //         gameRepo.nbOngoingCorrespondenceGames
  //       }
  //     }
  // }

  private val nbImportedCache = mongoCache[User.ID, Int](
    4096,
    "game:imported",
    30 days,
    identity
  ) { loader =>
    _.expireAfterAccess(10 minutes)
      .buildAsyncFuture {
        loader { userId =>
          gameRepo.coll countSel Query.imported(userId)
        }
      }
  }

  private val nbTotalCache = mongoCache.unit[Long](
    "game:total",
    29 minutes
  ) { loader =>
    _.refreshAfterWrite(30 minutes)
      .buildAsyncFuture {
        loader { _ =>
          gameRepo.coll.countAll
        }
      }
  }

  private val monthlyGamesCache = cacheApi.unit[List[(String, String, Long)]] {
    _.refreshAfterWrite(1 days)
      .buildAsyncFuture { _ =>
        gameRepo.countByMonthly
      }
  }

  private val gameWinRatesCache = cacheApi.unit[List[(String, Int, Int, Int)]] {
    _.refreshAfterWrite(1 days)
      .buildAsyncFuture { _ =>
        gameRepo.calculateWinRatePercentages
      }
  }

}
