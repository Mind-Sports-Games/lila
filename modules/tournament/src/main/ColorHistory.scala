package lila.tournament

import strategygames.{ Player => PlayerIndex }
import scala.concurrent.duration._

import lila.memo.CacheApi

//positive strike -> user played straight strike games by p1 pieces
//negative strike -> p2 pieces
case class PlayerIndexHistory(strike: Int, balance: Int) extends Ordered[PlayerIndexHistory] {

  override def compare(that: PlayerIndexHistory): Int = {
    if (strike < that.strike) -1
    else if (strike > that.strike) 1
    else if (balance < that.balance) -1
    else if (balance > that.balance) 1
    else 0
  }

  def firstGetsP1(that: PlayerIndexHistory)(fallback: () => Boolean) = {
    val c = compare(that)
    c < 0 || (c == 0 && fallback())
  }

  def inc(playerIndex: PlayerIndex): PlayerIndexHistory =
    copy(
      strike = playerIndex.fold((strike + 1) atLeast 1, (strike - 1) atMost -1),
      balance = balance + playerIndex.fold(1, -1)
    )

  //couldn't play if both players played maxStrike p2s games before
  //or both player maxStrike games before
  def couldPlay(that: PlayerIndexHistory, maxStrike: Int): Boolean =
    (strike > -maxStrike || that.strike > -maxStrike) &&
      (strike < maxStrike || that.strike < maxStrike)

  //add some penalty for pairs when both players have played last game with same playerIndex
  //heuristics: after such pairing one streak will be always incremented
  def samePlayerIndexs(that: PlayerIndexHistory): Boolean = strike.sign * that.strike.sign > 0
}

case class PlayerWithPlayerIndexHistory(player: Player, playerIndexHistory: PlayerIndexHistory)

final class PlayerIndexHistoryApi(cacheApi: CacheApi) {

  private val cache = cacheApi.scaffeine
    .expireAfterAccess(1 hour)
    .build[Player.ID, PlayerIndexHistory]()

  def default = PlayerIndexHistory(0, 0)

  def get(playerId: Player.ID) = cache.getIfPresent(playerId) | default

  def inc(playerId: Player.ID, playerIndex: PlayerIndex) = cache.put(playerId, get(playerId) inc playerIndex)
}
