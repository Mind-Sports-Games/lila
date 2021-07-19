package lila.simul

import strategygames.{ Centis, Clock, Color }

// All durations are expressed in seconds
case class SimulClock(
    config: Clock.Config,
    hostExtraTime: Int
) {

  def chessClockOf(hostColor: Color) =
    config.toClock(strategygames.GameLib.Chess()).giveTime(hostColor, Centis.ofSeconds(hostExtraTime))

  def hostExtraMinutes = hostExtraTime / 60
}
