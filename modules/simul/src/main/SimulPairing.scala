package lila.simul

import lila.game.IdGenerator

final case class SimulPairing(
    player: SimulPlayer,
    gameId: String,
    status: strategygames.Status,
    wins: Option[Boolean],
    hostPlayerIndex: strategygames.Player
) {

  def finished = status >= strategygames.Status.Aborted
  def ongoing  = !finished

  def is(userId: String): Boolean     = player is userId
  def is(other: SimulPlayer): Boolean = player is other

  def finish(s: strategygames.Status, w: Option[String]) =
    copy(
      status = s,
      wins = w map player.is
    )

  def winnerPlayerIndex =
    wins.map { w =>
      if (w) !hostPlayerIndex else hostPlayerIndex
    }
}

private[simul] object SimulPairing {

  def apply(player: SimulPlayer): SimulPairing =
    new SimulPairing(
      player = player,
      gameId = IdGenerator.uncheckedGame,
      status = strategygames.Status.Created,
      wins = none,
      hostPlayerIndex = strategygames.P1
    )
}
