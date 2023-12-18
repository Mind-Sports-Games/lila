package lila.game

import com.github.blemale.scaffeine.Cache
import scala.concurrent.duration._

import strategygames.format.{ FEN, UciDump }
import strategygames.{ Action, ActionStrs, Player }

final class UciMemo(gameRepo: GameRepo)(implicit ec: scala.concurrent.ExecutionContext) {

  private val cache: Cache[Game.ID, ActionStrs] = lila.memo.CacheApi.scaffeineNoScheduler
    .expireAfterAccess(5 minutes)
    .build[Game.ID, ActionStrs]()

  private val maxTurns = 300

  def add(game: Game, uci: String, playerIndex: Player): Unit = {
    val current = ~cache.getIfPresent(game.id)
    val newActionStrs =
      if (Player.fromTurnCount(current.size + game.stratGame.startedAtTurn) == playerIndex)
        current :+ List(uci)
      else current.dropRight(1) :+ (current.takeRight(1).flatten :+ uci)
    cache.put(game.id, newActionStrs)
  }

  def add(game: Game, action: Action): Unit =
    add(
      game,
      UciDump.action(game.variant.gameLogic, game.variant)(action),
      action.player
    )

  def get(game: Game, max: Int = maxTurns): Fu[ActionStrs] =
    cache
      .getIfPresent(game.id)
      .filter(_.size.min(max) == game.actionStrs.size.min(max))
      .fold(uciStrsFromGame(game, max).addEffect(cache.put(game.id, _)))(uciStrs => fuccess(uciStrs))

  // These API methods assume you already have the initial fen and doesn't query for it
  def set(game: Game, fen: Option[FEN]): Fu[Unit] =
    uciStrsFromGame(game, maxTurns, fen)
      .map(cache.put(game.id, _))

  private def uciStrsFromGame(game: Game, max: Int, fen: Option[FEN]): Fu[ActionStrs] =
    UciDump(game.variant.gameLogic, game.actionStrs take maxTurns, fen, game.variant)
      .map(_.toVector.map(_.toVector))
      .toFuture

  // These API methods will query for the initial fen and then use it.
  def set(game: Game): Fu[Unit] =
    gameRepo
      .initialFen(game)
      .flatMap(set(game, _))

  private def uciStrsFromGame(game: Game, max: Int): Fu[ActionStrs] =
    gameRepo
      .initialFen(game)
      .flatMap(uciStrsFromGame(game, max, _))
}
