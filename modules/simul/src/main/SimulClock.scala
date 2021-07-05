package lila.simul

import strategygames.chess.{ Color }
import strategygames.{ Centis, Clock }

// All durations are expressed in seconds
case class SimulClock(
    config: Clock.Config,
    hostExtraTime: Int
) {

  def chessClockOf(hostColor: Color) =
    config.toClock(strategygames.GameLib.Chess()).giveTime(
      hostColor match {
        case(strategygames.chess.White) => strategygames.White(strategygames.GameLib.Chess())
        case(strategygames.chess.Black) => strategygames.Black(strategygames.GameLib.Chess())
      }, Centis.ofSeconds(hostExtraTime))

  def hostExtraMinutes = hostExtraTime / 60
}
