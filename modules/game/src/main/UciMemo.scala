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
    val current       = ~cache.getIfPresent(game.id)
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

  private def set(game: Game, uciStrs: ActionStrs) =
    cache.put(game.id, uciStrs)

  def set(game: Game, fen: Option[FEN]) =
    for {
      uciStrs <- uciStrsFromGame(game, maxTurns, fen)
    } yield cache.put(game.id, uciStrs)

  def set(game: Game) =
    for {
      uciStrs <- uciStrsFromGame(game, maxTurns)
    } yield cache.put(game.id, uciStrs)

  def get(game: Game, max: Int = maxTurns): Fu[ActionStrs] =
    cache.getIfPresent(game.id).filter { uciStrs =>
      uciStrs.size.min(max) == game.actionStrs.size.min(max)
    } match {
      case Some(uciStrs) => fuccess(uciStrs)
      case _             => uciStrsFromGame(game, max).addEffect { set(game, _) }
    }

  private def uciStrsFromGame(game: Game, max: Int, fen: Option[FEN]): Fu[ActionStrs] =
    UciDump(
      game.variant.gameLogic,
      game.actionStrs take maxTurns,
      fen,
      game.variant
    ).map(_.toVector.map(_.toVector)).toFuture

  private def uciStrsFromGame(game: Game, max: Int): Fu[ActionStrs] =
    for {
      fen        <- gameRepo initialFen game
      actionStrs <- uciStrsFromGame(game, max, fen)
    } yield actionStrs
}
