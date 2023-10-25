package lila.game

import com.github.blemale.scaffeine.Cache
import scala.concurrent.duration._

import strategygames.format.UciDump
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

  def set(game: Game, actionStrs: ActionStrs) =
    cache.put(game.id, actionStrs)

  def set(game: Game) =
    cache.put(game.id, game.actionStrs)

  def get(game: Game, max: Int = maxTurns): Fu[ActionStrs] =
    cache getIfPresent game.id filter { actionStrs =>
      actionStrs.size.min(max) == game.actionStrs.size.min(max)
    } match {
      case Some(actionStrs) => fuccess(actionStrs)
      case _                => compute(game, max) addEffect { set(game, _) }
    }

  //def drop(game: Game, nb: Int) = {
  //  val current = ~cache.getIfPresent(game.id)
  //  cache.put(game.id, current.take(current.size - nb))
  //}

  private def compute(game: Game, max: Int): Fu[ActionStrs] =
    for {
      fen <- gameRepo initialFen game
      actionStrs <- UciDump(
        game.variant.gameLogic,
        game.actionStrs take maxTurns,
        fen,
        game.variant
      ).toFuture
    } yield actionStrs.toVector.map(_.toVector)
}
