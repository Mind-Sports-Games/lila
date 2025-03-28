package lila.tournament

import strategygames.{ Player => PlayerIndex }
import lila.game.Game
import lila.user.User

case class Pairing(
    id: Game.ID,
    tourId: Tournament.ID,
    status: strategygames.Status,
    user1: User.ID,
    user2: User.ID,
    winner: Option[User.ID],
    turns: Option[Int],
    invertStartPlayer: Boolean,
    berserk1: Boolean,
    berserk2: Boolean
) {

  def gameId = id

  def users                                       = List(user1, user2)
  def usersPair                                   = user1 -> user2
  def contains(user: User.ID): Boolean            = user1 == user || user2 == user
  def contains(u1: User.ID, u2: User.ID): Boolean = contains(u1) && contains(u2)
  def notContains(user: User.ID)                  = !contains(user)

  def opponentOf(userId: User.ID) =
    if (userId == user1) user2.some
    else if (userId == user2) user1.some
    else none

  def finished = status >= strategygames.Status.Mate
  def playing  = !finished

  //these don't work so well for multiaction as they trigger on turns started, not finished.
  //If a player has started a turn but doesn't complete it and resigns mid turn these will trigger a turn early for p2
  def quickFinish      = finished && turns.exists(20 >)
  def quickDraw        = draw && turns.exists(20 >)
  def notSoQuickFinish = finished && turns.exists(14 <=)
  def longGame         = turns.exists(60 <=)

  def wonBy(user: User.ID): Boolean     = winner.has(user)
  def lostBy(user: User.ID): Boolean    = winner.exists(user !=)
  def notLostBy(user: User.ID): Boolean = winner.fold(true)(user ==)
  def draw: Boolean                     = finished && winner.isEmpty

  def playerIndexOf(userId: User.ID): Option[PlayerIndex] =
    if (userId == user1) PlayerIndex.P1.some
    else if (userId == user2) PlayerIndex.P2.some
    else none

  def berserkOf(userId: User.ID): Boolean =
    if (userId == user1) berserk1
    else if (userId == user2) berserk2
    else false

  def berserkOf(playerIndex: PlayerIndex) = playerIndex.fold(berserk1, berserk2)

  def similar(other: Pairing) = other.contains(user1, user2)
}

private[tournament] object Pairing {

  case class LastOpponents(hash: Map[User.ID, User.ID]) extends AnyVal

  object LastOpponents {
    val empty = LastOpponents(Map[User.ID, User.ID]().empty)
  }

  private def make(
      gameId: Game.ID,
      tourId: Tournament.ID,
      u1: User.ID,
      u2: User.ID
  ) =
    new Pairing(
      id = gameId,
      tourId = tourId,
      status = strategygames.Status.Created,
      user1 = u1,
      user2 = u2,
      winner = none,
      turns = none,
      invertStartPlayer = false,
      berserk1 = false,
      berserk2 = false
    )

  case class Prep(tourId: Tournament.ID, user1: User.ID, user2: User.ID) {
    def toPairing(gameId: Game.ID): Pairing =
      make(gameId, tourId, user1, user2)
  }

  def prepWithPlayerIndex(
      tour: Tournament,
      p1: RankedPlayerWithPlayerIndexHistory,
      p2: RankedPlayerWithPlayerIndexHistory
  ) =
    if (tour.handicapped) {
      //in go handicapped tournament weaker player must go first
      if (p1.player.actualRating <= p2.player.actualRating) Prep(tour.id, p1.player.userId, p2.player.userId)
      else Prep(tour.id, p2.player.userId, p1.player.userId)
    } else {
      if (
        p1.playerIndexHistory.firstGetsP1(p2.playerIndexHistory)(() =>
          lila.common.ThreadLocalRandom.nextBoolean()
        )
      )
        Prep(tour.id, p1.player.userId, p2.player.userId)
      else Prep(tour.id, p2.player.userId, p1.player.userId)
    }
}
