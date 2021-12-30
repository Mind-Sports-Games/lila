package lila.round

import lila.hub.actorApi.timeline.{ Propagate, GameEnd => TLGameEnd }
import lila.notify.{ GameEnd, Notification, NotifyApi }

import lila.game.Game
import lila.user.User

import strategygames.{ Player => SGPlayer }

final private class RoundNotifier(
    timeline: lila.hub.actors.Timeline,
    isUserPresent: (Game, User.ID) => Fu[Boolean],
    notifyApi: NotifyApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  def gameEnd(game: Game)(sgPlayer: SGPlayer) =
    if (!game.aborted) game.player(sgPlayer).userId foreach { userId =>
      game.perfType foreach { perfType =>
        timeline ! (Propagate(
          TLGameEnd(
            playerId = game fullIdOf sgPlayer,
            opponent = game.player(!sgPlayer).userId,
            win = game.winnerSGPlayer map (sgPlayer ==),
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
                GameEnd.GameId(game fullIdOf sgPlayer),
                game.opponent(sgPlayer).userId map GameEnd.OpponentId.apply,
                game.wonBy(sgPlayer) map GameEnd.Win.apply
              )
            )
          )
        case _ =>
      }
    }
}
