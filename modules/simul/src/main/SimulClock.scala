package lila.simul

import strategygames.{ Centis, ClockConfig, Player => PlayerIndex }

// All durations are expressed in seconds
case class SimulClock(
    config: ClockConfig,
    hostExtraTime: Int
) {

  def chessClockOf(hostPlayerIndex: PlayerIndex) =
    config.toClock.giveTime(hostPlayerIndex, Centis.ofSeconds(hostExtraTime))

  def hostExtraMinutes = hostExtraTime / 60
}
