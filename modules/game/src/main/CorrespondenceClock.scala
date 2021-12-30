package lila.game

import strategygames.{ Player => SGPlayer }

// times are expressed in seconds
case class CorrespondenceClock(
    increment: Int,
    p1Time: Float,
    p2Time: Float
) {

  import CorrespondenceClock._

  def daysPerTurn = increment / 60 / 60 / 24

  def remainingTime(c: SGPlayer) = c.fold(p1Time, p2Time)

  def outoftime(c: SGPlayer) = remainingTime(c) == 0

  def moretimeable(c: SGPlayer) = remainingTime(c) < (increment - hourSeconds)

  def giveTime(c: SGPlayer) =
    c.fold(
      copy(p1Time = p1Time + daySeconds),
      copy(p2Time = p2Time + daySeconds)
    )

  // in seconds
  def estimateTotalTime = increment * 40 / 2

  def incrementHours = increment / 60 / 60
}

private object CorrespondenceClock {

  private val hourSeconds = 60 * 60
  private val daySeconds  = 24 * hourSeconds
}
