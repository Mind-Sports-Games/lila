package lila.tournament

import org.joda.time.DateTime
import scala.concurrent.Promise

import strategygames.Clock.{ Config => TournamentClock }
import lila.user.User
import lila.common.LightUser

private[tournament] case class WaitingUsers(
    hash: Map[LightUser, DateTime],
    clock: TournamentClock,
    date: DateTime //,
    //lightUserApi: lila.user.LightUserApi
) {

  // ultrabullet -> 8
  // hyperbullet -> 10
  // 1+0  -> 12  -> 15
  // 3+0  -> 24  -> 24
  // 5+0  -> 36  -> 36
  // 10+0 -> 66  -> 50
  private val waitSeconds: Int =
    if (clock.estimateTotalSeconds < 30) 8
    else if (clock.estimateTotalSeconds < 60) 10
    else {
      clock.estimateTotalSeconds / 10 + 6
    } atMost 50 atLeast 15

  lazy val all       = hash.keySet
  lazy val allNoBots = hash.keySet.filterNot(_.isBot)
  lazy val allBots   = hash.keySet.filter(_.isBot)
  lazy val botHash   = hash.view.filterKeys(allBots).toMap

  lazy val allIds       = all.map(_.id)
  lazy val allIdsNoBots = allNoBots.map(_.id)

  lazy val size       = hash.size
  lazy val sizeNoBots = allNoBots.size

  def isOdd       = size       % 2 == 1
  def isOddNoBots = sizeNoBots % 2 == 1

  //// skips the most recent user if odd
  //def evenNumber: Set[User.ID] = {
  //  if (isOdd) (all - hash.maxBy(_._2.getMillis)._1).map(_.id)
  //  else allIds
  //}

  def evenNumber: Set[User.ID] = {
    if (isOddNoBots)
      if (isOdd)
        // skips the most recent user if truly odd
        if (size == sizeNoBots) (all - hash.maxBy(_._2.getMillis)._1).map(_.id)
        // we have bots so exclude the most recent bot
        else (all - botHash.maxBy(_._2.getMillis)._1).map(_.id)
      // adding bots makes us even
      else allIds
    // without bots we have an even number
    else allIdsNoBots
  }

  lazy val haveWaitedEnough: Boolean =
    size > 100 || {
      val since = date minusSeconds waitSeconds
      hash.count { case (_, d) => d.isBefore(since) } > 1
    }

  def update(us: Set[LightUser]) = {
    val newDate = DateTime.now
    copy(
      date = newDate,
      hash = {
        hash.view.filterKeys(us.contains) ++
          us.filterNot(hash.contains).map { _ -> newDate }
      }.toMap
    )
  }

  def hasUser(userId: User.ID) = allIds contains userId
}

private[tournament] object WaitingUsers {

  def empty(clock: TournamentClock) = WaitingUsers(Map.empty, clock, DateTime.now)

  case class WithNext(waiting: WaitingUsers, next: Option[Promise[WaitingUsers]])

  def emptyWithNext(clock: TournamentClock) = WithNext(empty(clock), none)
}
