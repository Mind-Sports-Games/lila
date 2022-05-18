package lila.swiss

import cats.implicits._

import scala.concurrent.duration._

import lila.common.LightUser
import lila.game.Game

private case class SwissBoard(
    gameId: Game.ID,
    p1: SwissBoard.Player,
    p2: SwissBoard.Player,
    // TODO: This interface seems ugly, but I guess it's fine.
    isMicroMatch: Boolean,
    microMatchGameId: Option[Game.ID],
)

private object SwissBoard {
  case class Player(user: LightUser, rank: Int, rating: Int)
  case class WithGame(board: SwissBoard, game: Game, microMatchGame: Option[Game])
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
          (gameProxyRepo.game(board.gameId) zip board.microMatchGameId.traverse(gameProxyRepo.game)).map {
            case (game: Option[Game], microMatchGame: Option[Option[Game]]) => game.map(g => SwissBoard.WithGame(board, g, microMatchGame.flatten))
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
                    p1 = SwissBoard.Player(u1, r1, p1.rating),
                    p2 = SwissBoard.Player(u2, r2, p2.rating),
                    isMicroMatch = pairing.isMicroMatch,
                    microMatchGameId = pairing.microMatchGameId
                  )
                }
            )
        }
    }
}
