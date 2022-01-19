package lila.round

import lila.hub.actorApi.timeline.{ Propagate, GameEnd => TLGameEnd }
import lila.notify.{ GameEnd, Notification, NotifyApi }

import lila.game.Game
import lila.user.User

import strategygames.{ Player => PlayerIndex }

final private class RoundNotifier(
    timeline: lila.hub.actors.Timeline,
    isUserPresent: (Game, User.ID) => Fu[Boolean],
    notifyApi: NotifyApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  def gameEnd(game: Game)(playerIndex: PlayerIndex) =
    if (!game.aborted) game.player(playerIndex).userId foreach { userId =>
      game.perfType foreach { perfType =>
        timeline ! (Propagate(
          TLGameEnd(
            playerId = game fullIdOf playerIndex,
            opponent = game.player(!playerIndex).userId,
            win = game.winnerPlayerIndex map (playerIndex ==),
            perf = perfType.key
          )
        ) toUser userId)
      }
      isUserPresent(game, userId) foreach {
        case false =>
          notifyApi.addNotification(
            Notification.make(
              Notification.Notifies(userId),
              GameEnd(
                GameEnd.GameId(game fullIdOf playerIndex),
                game.opponent(playerIndex).userId map GameEnd.OpponentId.apply,
                game.wonBy(playerIndex) map GameEnd.Win.apply
              )
            )
          )
        case _ =>
      }
    }
}
