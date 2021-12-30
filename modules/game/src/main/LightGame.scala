package lila.game

import strategygames.{ Player => SGPlayer, Status }

import lila.user.User

case class LightGame(
    id: Game.ID,
    p1Player: Player,
    p2Player: Player,
    status: Status
) {
  def playable                                        = status < Status.Aborted
  def player(sgPlayer: SGPlayer): Player                    = sgPlayer.fold(p1Player, p2Player)
  def player(playerId: Player.ID): Option[Player]     = players find (_.id == playerId)
  def players                                         = List(p1Player, p2Player)
  def playerByUserId(userId: User.ID): Option[Player] = players.find(_.userId contains userId)
  def winner                                          = players find (_.wins)
  def wonBy(c: SGPlayer): Option[Boolean]                = winner.map(_.sgPlayer == c)
  def finished                                        = status >= Status.Mate
}

object LightGame {

  import Game.{ BSONFields => F }

  def projection =
    lila.db.dsl.$doc(
      F.p1Player -> true,
      F.p2Player -> true,
      F.playerUids  -> true,
      F.winnerSGPlayer -> true,
      F.status      -> true
    )
}
