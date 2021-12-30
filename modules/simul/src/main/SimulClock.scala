package lila.simul

import strategygames.{ Centis, Clock, Player => SGPlayer }

// All durations are expressed in seconds
case class SimulClock(
    config: Clock.Config,
    hostExtraTime: Int
) {

  def chessClockOf(hostSGPlayer: SGPlayer) =
    config.toClock.giveTime(hostSGPlayer, Centis.ofSeconds(hostExtraTime))

  def hostExtraMinutes = hostExtraTime / 60
}
