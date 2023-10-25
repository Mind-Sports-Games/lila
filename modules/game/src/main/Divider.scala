package lila.game

import com.github.blemale.scaffeine.Cache
import scala.concurrent.duration._

import strategygames.{ ActionStrs, Divider, Division, Replay }
import strategygames.variant.Variant
import strategygames.format.FEN

final class Divider {

  private val cache: Cache[Game.ID, Division] = lila.memo.CacheApi.scaffeineNoScheduler
    .expireAfterAccess(5 minutes)
    .build[Game.ID, Division]()

  def apply(game: Game, initialFen: Option[FEN]): Division =
    apply(game.id, game.actionStrs, game.variant, initialFen)

  def apply(id: Game.ID, actionStrs: => ActionStrs, variant: Variant, initialFen: Option[FEN]) =
    if (!Variant.divisionSensibleVariants(variant.gameLogic)(variant))
      Division.empty
    else
      cache.get(
        id,
        _ =>
          Replay
            .boards(
              lib = variant.gameLogic,
              actionStrs = actionStrs,
              initialFen = initialFen,
              variant = variant
            )
            .toOption
            .fold(Division.empty)(b => Divider.apply(variant.gameLogic, b))
      )
}
