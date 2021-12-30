package lila.round
package actorApi

import scala.concurrent.Promise
import scala.concurrent.duration._

import strategygames.format.Uci
import strategygames.{ Player => SGPlayer, Move, MoveMetrics }

import lila.common.IpAddress
import lila.game.Game.PlayerId
import lila.socket.Socket.SocketVersion

case class ByePlayer(playerId: PlayerId)
case class GetSocketStatus(promise: Promise[SocketStatus])
case class SocketStatus(
    version: SocketVersion,
    p1OnGame: Boolean,
    p1IsGone: Boolean,
    p2OnGame: Boolean,
    p2IsGone: Boolean
) {
  def onGame(sgPlayer: SGPlayer)     = sgPlayer.fold(p1OnGame, p2OnGame)
  def isGone(sgPlayer: SGPlayer)     = sgPlayer.fold(p1IsGone, p2IsGone)
  def sgPlayersOnGame: Set[SGPlayer] = SGPlayer.all.filter(onGame).toSet
}
case class RoomCrowd(p1: Boolean, p2: Boolean)
case class BotConnected(sgPlayer: SGPlayer, v: Boolean)

package round {

  case class HumanPlay(
      playerId: PlayerId,
      uci: Uci,
      blur: Boolean,
      moveMetrics: MoveMetrics = MoveMetrics(),
      promise: Option[Promise[Unit]] = None,
      finalSquare: Boolean = false
  )

  case class PlayResult(events: Events, fen: String, lastMove: Option[String])

  case object AbortForce
  case object Threefold
  case object ResignAi
  case class ResignForce(playerId: PlayerId)
  case class DrawForce(playerId: PlayerId)
  case class DrawClaim(playerId: PlayerId)
  case class DrawYes(playerId: PlayerId)
  case class DrawNo(playerId: PlayerId)
  case class TakebackYes(playerId: PlayerId)
  case class TakebackNo(playerId: PlayerId)
  object Moretime { val defaultDuration = 15.seconds }
  case class Moretime(playerId: PlayerId, seconds: FiniteDuration = Moretime.defaultDuration)
  case object QuietFlag
  case class ClientFlag(sgPlayer: SGPlayer, fromPlayerId: Option[PlayerId])
  case object Abandon
  case class ForecastPlay(lastMove: Move)
  case class Cheat(sgPlayer: SGPlayer)
  case class HoldAlert(playerId: PlayerId, mean: Int, sd: Int, ip: IpAddress)
  case class GoBerserk(sgPlayer: SGPlayer, promise: Promise[Boolean])
  case object NoStart
  case object StartClock
  case object TooManyPlies
}
