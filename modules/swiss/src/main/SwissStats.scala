package lila.swiss

import akka.stream.scaladsl._
import reactivemongo.api.bson.Macros
import scala.concurrent.duration._

import lila.db.dsl._
import reactivemongo.api.bson.BSONDocumentHandler

case class SwissStats(
    games: Int = 0,
    p1Wins: Int = 0,
    p2Wins: Int = 0,
    draws: Int = 0,
    byes: Int = 0,
    absences: Int = 0,
    averageRating: Int = 0
)

final class SwissStatsApi(
    colls: SwissColls,
    sheetApi: SwissSheetApi,
    mongoCache: lila.memo.MongoCache.Api
)(implicit
    ec: scala.concurrent.ExecutionContext,
    mat: akka.stream.Materializer
) {

  import BsonHandlers._

  def apply(swiss: Swiss): Fu[Option[SwissStats]] =
    swiss.isFinished ?? cache.get(swiss.id).dmap(some).dmap(_.filter(_.games > 0))

  implicit private val statsBSONHandler: BSONDocumentHandler[SwissStats] = Macros.handler[SwissStats]

  private val cache = mongoCache[Swiss.Id, SwissStats](
    64,
    "swiss:stats",
    60 days,
    _.value
  ) { loader =>
    _.expireAfterAccess(5 seconds)
      .maximumSize(256)
      .buildAsyncFuture(loader(fetch))
  }

  private def fetch(id: Swiss.Id): Fu[SwissStats] =
    colls.swiss.byId[Swiss](id.value) flatMap {
      _.filter(_.nbPlayers > 0).fold(fuccess(SwissStats())) { swiss =>
        sheetApi
          .source(swiss, sort = $empty)
          .toMat(Sink.fold(SwissStats()) { case (stats, (player, pairings, sheet)) =>
            pairings.values.foldLeft((0, 0, 0, 0)) { case ((games, p1Wins, p2Wins, draws), pairing) =>
              (
                games + pairing.numGames,
                p1Wins + pairing.numFirstPlayerWins,
                p2Wins + pairing.numSecondPlayerWins,
                draws + pairing.numDraws
              )
            } match {
              case (games, p1Wins, p2Wins, draws) =>
                sheet.outcomes.foldLeft((0, 0)) { case ((byes, absences), outcome) =>
                  (
                    byes + (outcome.head == SwissSheet.Bye).??(1),
                    absences + (outcome.head == SwissSheet.Absent).??(1)
                  )
                } match {
                  case (byes, absences) =>
                    stats.copy(
                      games = stats.games + games,
                      p1Wins = stats.p1Wins + p1Wins,
                      p2Wins = stats.p2Wins + p2Wins,
                      draws = stats.draws + draws,
                      byes = stats.byes + byes,
                      absences = stats.absences + absences,
                      averageRating = stats.averageRating + player.actualRating
                    )
                }
            }
          })(Keep.right)
          .run()
          .dmap { s =>
            s.copy(
              games = s.games / 2,
              p1Wins = s.p1Wins / 2,
              p2Wins = s.p2Wins / 2,
              draws = s.draws / 2,
              averageRating = s.averageRating / swiss.nbPlayers
            )
          }
      }
    }
}
