package lila.fishnet

import strategygames.GameLogic

import lila.game.Game

// Queues gnubg analysis for every finished backgammon game played between two
// accounts, whether registered users or PS bots. Anonymous games are skipped, as
// are bot-vs-bot games.
final class BackgammonAutoAnalyser(
    analyser: Analyser,
    enabled: Boolean
) {

  def apply(game: Game): Unit =
    if (enabled && analysable(game))
      analyser(
        game,
        Work.Sender(userId = lila.user.User.playstrategyId, ip = none, mod = false, system = true)
      ).discard

  private def analysable(game: Game): Boolean =
    game.variant.gameLogic == GameLogic.Backgammon() &&
      game.analysable &&
      game.twoUserIds.isDefined &&
      !game.players.forall(_.isPSBot)
}
