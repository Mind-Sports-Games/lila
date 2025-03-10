package lila.tournament

import reactivemongo.api.bson.Macros
import scala.concurrent.duration._

import strategygames.{ Player => PlayerIndex }
import lila.db.dsl._
import reactivemongo.api.bson.BSONDocumentHandler

final class TournamentStatsApi(
    playerRepo: PlayerRepo,
    pairingRepo: PairingRepo,
    mongoCache: lila.memo.MongoCache.Api
)(implicit ec: scala.concurrent.ExecutionContext) {

  def apply(tournament: Tournament): Fu[Option[TournamentStats]] =
    tournament.isFinished ?? cache.get(tournament.id).dmap(some)

  implicit private val statsBSONHandler: BSONDocumentHandler[TournamentStats] =
    Macros.handler[TournamentStats]

  private val cache = mongoCache[Tournament.ID, TournamentStats](
    64,
    "tournament:stats",
    60 days,
    identity
  ) { loader =>
    _.expireAfterAccess(10 minutes)
      .maximumSize(256)
      .buildAsyncFuture(loader(fetch))
  }

  private def fetch(tournamentId: Tournament.ID): Fu[TournamentStats] =
    for {
      rating   <- playerRepo.averageRating(tournamentId)
      rawStats <- pairingRepo.rawStats(tournamentId)
    } yield TournamentStats.readAggregation(rating)(rawStats)
}

case class TournamentStats(
    games: Int,
    moves: Int,
    p1Wins: Int,
    p2Wins: Int,
    draws: Int,
    berserks: Int,
    averageRating: Int
)

private object TournamentStats {

  private case class PlayerIndexStats(games: Int, moves: Int, b1: Int, b2: Int) {
    def berserks = b1 + b2
  }

  def readAggregation(rating: Int)(docs: List[Bdoc]): TournamentStats = {
    val playerIndexStats: Map[Option[PlayerIndex], PlayerIndexStats] = docs.view.map { doc =>
      doc.getAsOpt[Boolean]("_id").map(PlayerIndex.fromP1) ->
        PlayerIndexStats(
          ~doc.int("games"),
          ~doc.int("moves"),
          ~doc.int("b1"),
          ~doc.int("b2")
        )
    }.toMap
    TournamentStats(
      games = playerIndexStats.foldLeft(0)(_ + _._2.games),
      moves = playerIndexStats.foldLeft(0)(_ + _._2.moves),
      p1Wins = playerIndexStats.get(PlayerIndex.P1.some).??(_.games),
      p2Wins = playerIndexStats.get(PlayerIndex.P2.some).??(_.games),
      draws = playerIndexStats.get(none).??(_.games),
      berserks = playerIndexStats.foldLeft(0)(_ + _._2.berserks),
      averageRating = rating
    )
  }
}
