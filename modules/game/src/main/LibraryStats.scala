package lila.game

import scala.concurrent.duration._

import lila.user.{ User, UserRepo }
import lila.db.dsl._
import lila.memo.{ CacheApi }
import reactivemongo.api.{ Cursor, ReadPreference }

final class LibraryStats(
    gameColl: Coll,
    userRepo: UserRepo,
    cacheApi: lila.memo.CacheApi
)(implicit ec: scala.concurrent.ExecutionContext) {
  import BSONHandlers._
  import Game.{ ID, BSONFields => F }

  def gameClockRates: Fu[(Int, Int)]  = gameClockRatesCache.get {}
  def botOrHumanGames: Fu[(Int, Int)] = botOrHumanGameCache.get {}

  def finishedGameClockStats: Fu[(Int, Int, Int)] =
    gameColl
      .aggregateList(
        maxDocs = 1,
        ReadPreference.secondaryPreferred
      ) { framework =>
        import framework._
        Match(Query.finished) -> List(
          Project(
            $doc(
              "clock"   -> $doc("$cond" -> $arr($doc("$ifNull" -> $arr(s"$$${F.clock}", false)), 1, 0)),
              "noClock" -> $doc("$cond" -> $arr($doc("$ifNull" -> $arr(s"$$${F.clock}", false)), 0, 1))
            )
          ),
          Group($doc())(
            "total"   -> SumAll,
            "clock"   -> SumField("clock"),
            "noClock" -> SumField("noClock")
          )
        )
      }
      .map { docs =>
        docs.headOption.flatMap { doc =>
          for {
            total   <- doc.getAsOpt[Int]("total")
            clock   <- doc.getAsOpt[Int]("clock")
            noClock <- doc.getAsOpt[Int]("noClock")
          } yield (total, clock, noClock)
        } getOrElse (0, 0, 0)
      }

  def finishedGameClockPercentages: Fu[(Int, Int)] =
    finishedGameClockStats.map { case (total, clock, _) =>
      val clockPct   = if (total > 0) (clock * 100) / total else 0
      val noClockPct = if (total > 0) 100 - clockPct else 0
      (clockPct, noClockPct)
    }

  private val gameClockRatesCache = cacheApi.unit[(Int, Int)] {
    _.refreshAfterWrite(1 day)
      .buildAsyncFuture { _ =>
        finishedGameClockPercentages
      }
  }

  def finishedBotOrHumanGameStats: Fu[(Int, Int)] =
    for {
      // Get all finished games with playerUids
      games <- gameColl
        .find(Query.finished, $doc(Game.BSONFields.playerUids -> true, "p0" -> true, "p1" -> true).some)
        .cursor[Bdoc](ReadPreference.secondaryPreferred)
        .collect[List](Int.MaxValue, Cursor.FailOnError[List[Bdoc]]())

      // Get all unique user IDs
      allUserIds = games.flatMap(_.getAsOpt[List[String]](Game.BSONFields.playerUids)).flatten.distinct

      // Get bot user IDs
      botUsers <- userRepo.botsByIds(allUserIds)
      botIds = botUsers.map(_.id).toSet

      // Count games where at least one player is a bot or the ai flag is set (stockfish)
      (botGames, humanGames) = games.partition { doc =>
        val p0     = doc.getAsOpt[Bdoc]("p0")
        val p1     = doc.getAsOpt[Bdoc]("p1")
        val p0IsAi = p0.exists(_.contains("ai"))
        val p1IsAi = p1.exists(_.contains("ai"))
        val hasBotUser =
          doc.getAsOpt[List[String]](Game.BSONFields.playerUids).exists(_.exists(botIds.contains))
        hasBotUser || p0IsAi || p1IsAi
      }

      total = botGames.size + humanGames.size
    } yield if (total > 0) (botGames.size * 100 / total, 100 - botGames.size * 100 / total) else (0, 0)

  private val botOrHumanGameCache = cacheApi.unit[(Int, Int)] {
    _.refreshAfterWrite(1 day)
      .buildAsyncFuture { _ =>
        finishedBotOrHumanGameStats
      }
  }

}
