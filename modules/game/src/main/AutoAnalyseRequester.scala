package lila.game

final class AutoAnalyseRequester(
    fishnet: lila.hub.actors.Fishnet,
    enabled: Boolean
) {

  def apply(game: Game): Unit =
    if (enabled && analysable(game))
      fishnet ! lila.hub.actorApi.fishnet.AutoAnalyse(game.id)

  private def analysable(game: Game): Boolean = game.analysable && !game.hasPSBot
}
