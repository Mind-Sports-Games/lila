package lila.pool

import play.api.libs.json.Json
import scala.concurrent.duration._
import strategygames.variant.Variant

object PoolList {

  import PoolConfig._

  val all: List[PoolConfig] = List(
    //PoolConfig(1 ++ 0, Wave(22 seconds, 30 players), Variant.Chess(strategygames.chess.variant.Standard)),
    PoolConfig(3 ++ 2, Wave(22 seconds, 30 players), Variant.Chess(strategygames.chess.variant.Standard)),
    PoolConfig(
      3 ++ 2,
      Wave(22 seconds, 30 players),
      Variant.Draughts(strategygames.draughts.variant.Standard)
    ),
    PoolConfig(
      3 ++ 2,
      Wave(22 seconds, 30 players),
      Variant.Chess(strategygames.chess.variant.LinesOfAction)
    ),
    PoolConfig(
      strategygames.ByoyomiClock.Config(5 * 60, 0, 10, 1),
      Wave(22 seconds, 30 players),
      Variant.FairySF(strategygames.fairysf.variant.Shogi)
    ),
    PoolConfig(3 ++ 2, Wave(22 seconds, 30 players), Variant.FairySF(strategygames.fairysf.variant.Xiangqi)),
    PoolConfig(3 ++ 2, Wave(22 seconds, 30 players), Variant.FairySF(strategygames.fairysf.variant.Flipello)),
    PoolConfig(3 ++ 2, Wave(22 seconds, 30 players), Variant.FairySF(strategygames.fairysf.variant.Amazons)),
    PoolConfig(3 ++ 2, Wave(22 seconds, 30 players), Variant.Samurai(strategygames.samurai.variant.Oware)),
    PoolConfig(
      3 ++ 2,
      Wave(22 seconds, 30 players),
      Variant.Togyzkumalak(strategygames.togyzkumalak.variant.Togyzkumalak)
    ),
    PoolConfig(
      3 ++ 2,
      Wave(22 seconds, 30 players),
      Variant.FairySF(strategygames.fairysf.variant.BreakthroughTroyka)
    ),
    PoolConfig(
      strategygames.Clock.SimpleDelayConfig(2 * 60, 12),
      Wave(22 seconds, 30 players),
      Variant.Backgammon(strategygames.backgammon.variant.Backgammon)
    )
  )

  val clockStringSet: Set[String] = all.view.map(_.clock.show) to Set

  val variantSet: Set[Variant] = all.view.map(_.variant) to Set

  val json = Json toJson all

  implicit private class PimpedInt(self: Int) {
    def ++(increment: Int) = strategygames.Clock.Config(self * 60, increment)
    def players            = NbPlayers(self)
  }
}
