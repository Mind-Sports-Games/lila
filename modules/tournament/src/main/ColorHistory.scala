package lila.tournament

import strategygames.{ Player => SGPlayer }
import scala.concurrent.duration._

import lila.memo.CacheApi

//positive strike -> user played straight strike games by p1 pieces
//negative strike -> p2 pieces
case class SGPlayerHistory(strike: Int, balance: Int) extends Ordered[SGPlayerHistory] {

  override def compare(that: SGPlayerHistory): Int = {
    if (strike < that.strike) -1
    else if (strike > that.strike) 1
    else if (balance < that.balance) -1
    else if (balance > that.balance) 1
    else 0
  }

  def firstGetsP1(that: SGPlayerHistory)(fallback: () => Boolean) = {
    val c = compare(that)
    c < 0 || (c == 0 && fallback())
  }

  def inc(sgPlayer: SGPlayer): SGPlayerHistory =
    copy(
      strike = sgPlayer.fold((strike + 1) atLeast 1, (strike - 1) atMost -1),
      balance = balance + sgPlayer.fold(1, -1)
    )

  //couldn't play if both players played maxStrike p2s games before
  //or both player maxStrike games before
  def couldPlay(that: SGPlayerHistory, maxStrike: Int): Boolean =
    (strike > -maxStrike || that.strike > -maxStrike) &&
      (strike < maxStrike || that.strike < maxStrike)

  //add some penalty for pairs when both players have played last game with same sgPlayer
  //heuristics: after such pairing one streak will be always incremented
  def sameSGPlayers(that: SGPlayerHistory): Boolean = strike.sign * that.strike.sign > 0
}

case class PlayerWithSGPlayerHistory(player: Player, sgPlayerHistory: SGPlayerHistory)

final class SGPlayerHistoryApi(cacheApi: CacheApi) {

  private val cache = cacheApi.scaffeine
    .expireAfterAccess(1 hour)
    .build[Player.ID, SGPlayerHistory]()

  def default = SGPlayerHistory(0, 0)

  def get(playerId: Player.ID) = cache.getIfPresent(playerId) | default

  def inc(playerId: Player.ID, sgPlayer: SGPlayer) = cache.put(playerId, get(playerId) inc sgPlayer)
}
