package lila.round
package actorApi

import scala.concurrent.Promise
import scala.concurrent.duration._

import strategygames.format.Uci
import strategygames.{ Player => PlayerIndex, Move, MoveMetrics, Pos }

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
  def onGame(playerIndex: PlayerIndex)     = playerIndex.fold(p1OnGame, p2OnGame)
  def isGone(playerIndex: PlayerIndex)     = playerIndex.fold(p1IsGone, p2IsGone)
  def playerIndexsOnGame: Set[PlayerIndex] = PlayerIndex.all.filter(onGame).toSet
}
case class RoomCrowd(p1: Boolean, p2: Boolean)
case class BotConnected(playerIndex: PlayerIndex, v: Boolean)

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

  case class PlayerSelectSquares(playerId: PlayerId, squares: List[Pos])

  case object AbortForce
  case object Threefold
  case object ResignAi
  case class ResignForce(playerId: PlayerId)
  case class DrawForce(playerId: PlayerId)
  case class DrawClaim(playerId: PlayerId)
  case class DrawYes(playerId: PlayerId)
  case class DrawNo(playerId: PlayerId)
  case class SelectSquaresAccept(playerId: PlayerId)
  case class SelectSquaresDecline(playerId: PlayerId)
  case class TakebackYes(playerId: PlayerId)
  case class TakebackNo(playerId: PlayerId)
  object Moretime { val defaultDuration = 15.seconds }
  case class Moretime(playerId: PlayerId, seconds: FiniteDuration = Moretime.defaultDuration)
  case object QuietFlag
  case class ClientFlag(playerIndex: PlayerIndex, fromPlayerId: Option[PlayerId])
  case object Abandon
  case class ForecastPlay(lastMove: Move)
  case class Cheat(playerIndex: PlayerIndex)
  case class HoldAlert(playerId: PlayerId, mean: Int, sd: Int, ip: IpAddress)
  case class GoBerserk(playerIndex: PlayerIndex, promise: Promise[Boolean])
  case object NoStart
  case object StartClock
  case object TooManyPlies
}
