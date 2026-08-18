package lila.game

import strategygames.GameLogic

// Backgammon games are auto analysed on finish by lila.fishnet.BackgammonAutoAnalyser,
// so there is nothing to request for them here.
final class AutoAnalyseRequester(
    fishnet: lila.hub.actors.Fishnet,
    enabled: Boolean
) {

  def apply(game: Game): Unit =
    if (enabled && analysable(game))
      fishnet ! lila.hub.actorApi.fishnet.AutoAnalyse(game.id)

  private def analysable(game: Game): Boolean =
    game.variant.gameLogic != GameLogic.Backgammon() &&
      game.analysable &&
      !game.hasPSBot
}
