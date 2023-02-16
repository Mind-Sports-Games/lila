package lila.setup

sealed abstract class TimeMode(val id: Int)

object TimeMode {

  case object Unlimited      extends TimeMode(0)
  case object FischerClock   extends TimeMode(1)
  case object Correspondence extends TimeMode(2)
  case object ByoyomiClock   extends TimeMode(3)

  val default = FischerClock

  val all = List(Unlimited, FischerClock, ByoyomiClock, Correspondence)

  val ids = all map (_.id)

  val byId = all map { v =>
    (v.id, v)
  } toMap

  def apply(id: Int): Option[TimeMode] = byId get id

  def orDefault(id: Int) = apply(id) | default

  def ofGame(game: lila.game.Game) =
    if (game.hasFisherClock) FischerClock
    else if (game.hasByoyomiClock) ByoyomiClock
    else if (game.hasCorrespondenceClock) Correspondence
    else Unlimited
}
