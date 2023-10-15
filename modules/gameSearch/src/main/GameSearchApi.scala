package lila.gameSearch

import play.api.libs.json._
import scala.concurrent.duration._

import strategygames.{ ByoyomiClock, Clock }

import lila.game.{ Game, GameRepo }
import lila.search._

final class GameSearchApi(
    client: ESClient,
    gameRepo: GameRepo
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem
) extends SearchReadApi[Game, Query] {

  def search(query: Query, from: From, size: Size) =
    client.search(query, from, size) flatMap { res =>
      gameRepo gamesFromSecondary res.ids
    }

  def count(query: Query) =
    client.count(query) dmap (_.count)

  def ids(query: Query, max: Int): Fu[List[String]] =
    client.search(query, From(0), Size(max)).map(_.ids)

  def store(game: Game) =
    storable(game) ?? {
      gameRepo isAnalysed game.id flatMap { analysed =>
        lila.common.Future.retry(
          () => client.store(Id(game.id), toDoc(game, analysed)),
          delay = 20.seconds,
          retries = 2,
          logger.some
        )
      }
    }

  private def storable(game: Game) = game.finished || game.imported

  private def toDoc(game: Game, analysed: Boolean) =
    Json
      .obj(
        Fields.status -> (game.status match {
          case s if s.is(_.Timeout) => strategygames.Status.Resign
          case s if s.is(_.NoStart) => strategygames.Status.Resign
          case _                    => game.status
        }).id,
        Fields.turns             -> (game.turns + 1) / 2,
        Fields.rated             -> game.rated,
        Fields.perf              -> game.perfType.map(_.id),
        Fields.uids              -> game.userIds.toArray.some.filterNot(_.isEmpty),
        Fields.winner            -> game.winner.flatMap(_.userId),
        Fields.loser             -> game.loser.flatMap(_.userId),
        Fields.winnerPlayerIndex -> game.winner.fold(3)(_.playerIndex.fold(1, 2)),
        Fields.averageRating     -> game.averageUsersRating,
        Fields.ai                -> game.aiLevel,
        Fields.date              -> (lila.search.Date.formatter print game.movedAt),
        Fields.duration          -> game.durationSeconds, // for realtime games only
        Fields.clockInit         -> game.clock.map(_.limitSeconds),
        Fields.clockInc -> game.clock.map(_.config match {
          case fc: Clock.Config         => fc.incrementSeconds
          case _: Clock.BronsteinConfig => 0
          case _: Clock.UsDelayConfig   => 0
          case bc: ByoyomiClock.Config  => bc.incrementSeconds
        }),
        // TODO: add in bronstein delay and us delay and byoyomi here.
        Fields.analysed -> analysed,
        Fields.p1User   -> game.p1Player.userId,
        Fields.p2User   -> game.p2Player.userId,
        Fields.source   -> game.source.map(_.id)
      )
      .noNull
}
