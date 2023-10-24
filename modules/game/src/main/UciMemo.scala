package lila.game

import com.github.blemale.scaffeine.Cache
import scala.concurrent.duration._

import strategygames.format.UciDump
import strategygames.{ Actions, MoveOrDrop, Player }

final class UciMemo(gameRepo: GameRepo)(implicit ec: scala.concurrent.ExecutionContext) {

  private val cache: Cache[Game.ID, Actions] = lila.memo.CacheApi.scaffeineNoScheduler
    .expireAfterAccess(5 minutes)
    .build[Game.ID, Actions]()

  private val maxTurns = 300

  def add(game: Game, uciAction: String, playerIndex: Player): Unit = {
    val current = ~cache.getIfPresent(game.id)
    val newActions =
      if (Player.fromTurnCount(current.size + game.stratGame.startedAtTurn) == playerIndex)
        current :+ List(uciAction)
      else current.dropRight(1) :+ (current.takeRight(1).flatten :+ uciAction)
    cache.put(game.id, newActions)
  }

  def add(game: Game, action: MoveOrDrop): Unit =
    add(
      game,
      UciDump.action(game.variant.gameLogic, game.variant)(action),
      action.fold(_.situationBefore.player, _.situationBefore.player)
    )

  def set(game: Game, actions: Actions) =
    cache.put(game.id, actions)

  def set(game: Game) =
    cache.put(game.id, game.actions)

  def get(game: Game, max: Int = maxTurns): Fu[Actions] =
    cache getIfPresent game.id filter { actions =>
      actions.size.min(max) == game.actions.size.min(max)
    } match {
      case Some(actions) => fuccess(actions)
      case _             => compute(game, max) addEffect { set(game, _) }
    }

  //def drop(game: Game, nb: Int) = {
  //  val current = ~cache.getIfPresent(game.id)
  //  cache.put(game.id, current.take(current.size - nb))
  //}

  private def compute(game: Game, max: Int): Fu[Actions] =
    for {
      fen <- gameRepo initialFen game
      actions <- UciDump(
        game.variant.gameLogic,
        game.actions take maxTurns,
        fen,
        game.variant
      ).toFuture
    } yield actions.toVector.map(_.toVector)
}
