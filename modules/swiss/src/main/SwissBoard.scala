package lila.swiss

import cats.implicits._

import scala.concurrent.duration._

import lila.common.LightUser
import lila.game.Game

private case class SwissBoard(
    gameId: Game.ID,
    p1: SwissBoard.Player,
    p2: SwissBoard.Player,
    isBestOfX: Boolean,
    isPlayX: Boolean,
    multiMatchGameIds: Option[List[Game.ID]]
)

private object SwissBoard {
  case class Player(user: LightUser, rank: Int, rating: Int, inputRating: Option[Int])
  case class WithGame(
      board: SwissBoard,
      game: Game,
      multiMatchGames: Option[List[Game]]
  )
}

final private class SwissBoardApi(
    rankingApi: SwissRankingApi,
    cacheApi: lila.memo.CacheApi,
    lightUserApi: lila.user.LightUserApi,
    gameProxyRepo: lila.round.GameProxyRepo
)(implicit ec: scala.concurrent.ExecutionContext) {

  private val displayBoards = 6

  private val boardsCache = cacheApi.scaffeine
    .expireAfterWrite(60 minutes)
    .build[Swiss.Id, List[SwissBoard]]()

  def apply(id: Swiss.Id): Fu[List[SwissBoard.WithGame]] =
    boardsCache.getIfPresent(id) ?? {
      _.map { board =>
        (gameProxyRepo.game(board.gameId) zip (
          board.multiMatchGameIds
            .traverse(l => l.traverse(gid => gameProxyRepo.game(gid)).map(_.flatten))
        ))
          .map { case (game: Option[Game], multiMatchGames: Option[List[Game]]) =>
            game.map(g => SwissBoard.WithGame(board, g, multiMatchGames))
          }
      }.sequenceFu
        .dmap(_.flatten)
    }

  def update(data: SwissScoring.Result): Funit =
    data match {
      case SwissScoring.Result(swiss, leaderboard, playerMap, pairings) =>
        rankingApi(swiss) map { ranks =>
          boardsCache
            .put(
              swiss.id,
              leaderboard
                .collect {
                  case (player, _) if player.present => player
                }
                .flatMap { player =>
                  pairings get player.userId flatMap {
                    _ get swiss.round
                  }
                }
                .filter(_.isOngoing)
                .distinct
                .take(displayBoards)
                .flatMap { pairing =>
                  for {
                    p1 <- playerMap get pairing.p1
                    p2 <- playerMap get pairing.p2
                    u1 <- lightUserApi sync p1.userId
                    u2 <- lightUserApi sync p2.userId
                    r1 <- ranks get p1.userId
                    r2 <- ranks get p2.userId
                  } yield SwissBoard(
                    pairing.gameId,
                    p1 = SwissBoard.Player(u1, r1, p1.rating, p1.inputRating),
                    p2 = SwissBoard.Player(u2, r2, p2.rating, p2.inputRating),
                    isBestOfX = pairing.isBestOfX,
                    isPlayX = pairing.isPlayX,
                    multiMatchGameIds = pairing.multiMatchGameIds
                  )
                }
            )
        }
    }
}
