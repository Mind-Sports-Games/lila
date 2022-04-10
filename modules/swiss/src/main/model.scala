package lila.swiss

import lila.user.User
import lila.game.Game

object SwissRound {

  case class Number(value: Int) extends AnyVal with IntValue
}

case class MyInfo(rank: Int, gameIds: Option[SwissPairingGameIds], user: User, player: SwissPlayer) {
  def page = (rank + 9) / 10
}

final class GetSwissName(f: Swiss.Id => Option[String]) extends (Swiss.Id => Option[String]) {
  def apply(id: Swiss.Id) = f(id)
}

case class GameView(
    swiss: Swiss,
    ranks: Option[GameRanks]
)
case class GameRanks(p1Rank: Int, p2Rank: Int)

case class FeaturedSwisses(
    created: List[Swiss],
    started: List[Swiss]
)

case class SwissFinish(id: Swiss.Id, ranking: Ranking)

object SwissBounds {
  val maxRounds           = 100
  val maxScore            = maxRounds * 2
  val maxBuccholz         = maxScore * 2
  val maxSonnenbornBerger = maxScore * 2
  val maxPerformance      = 5000

  // TODO: these are a candidates to be moved elsewhere
  case class WithBounds(value: Long, totalValues: Long)
  object WithBounds {
    // NOTE: the above max values need an extra value added to them
    //       in order to be used as the totalValues here, because 0
    //       is also one of the valid values.
    def score(value: Long) = WithBounds(value, maxScore + 1)
    def buccholz(value: Long) = WithBounds(value, maxBuccholz + 1)
    def sonnenbornBerger(value: Long) = WithBounds(value, maxSonnenbornBerger + 1)
    // Although not in this case, because this is already
    // an overestimated upper bound
    def performance(value: Long) = WithBounds(value, maxPerformance)
  }

  // TODO: could also do a quick check here to ensure
  //       it fits, rather than overflows
  // in this case it is assumed the first value will be put
  // into the LSB, and the later values into the MORE SB
  def encodeIntoLong(data: WithBounds*) =
    encodeIntoLongRecurse(1, data: _*)

  def encodeIntoLongRecurse(factor: Long, data: WithBounds*): Long =
    data match {
      case first :: Nil => factor * first.value
      case first :: rest =>
        (factor * first.value)
        +encodeIntoLongRecurse(factor * first.totalValues, rest: _*)
      case Nil => 0
    }
}
