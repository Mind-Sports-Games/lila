package lila.pool

import play.api.libs.json.Json
import strategygames.variant.Variant

object PoolList {

  import PoolConfig.*

  // Wave periods are intentionally distinct to reduce simultaneous GetCandidates bursts to the
  // sequential lobbyTrouper. Ordered by traffic (busiest = shortest period), with 3s steps between
  // periods. Combined with up to 3s of random jitter in PoolActor, simultaneous firing is unlikely
  // but not impossible.
  val all: List[PoolConfig] = List(
    // PoolConfig(1 ++ 0, Wave(22 seconds, 30 players), Variant.Chess(strategygames.chess.variant.Standard)),
    PoolConfig(3 ++ 2, Wave(22 seconds, 30 players), Variant.Chess(strategygames.chess.variant.Standard)),
    PoolConfig(3 ++ 2, Wave(25 seconds, 30 players), Variant.Samurai(strategygames.samurai.variant.Oware)),
    PoolConfig(5 ++ 0, Wave(28 seconds, 30 players), Variant.FairySF(strategygames.fairysf.variant.Flipello)),
    PoolConfig(
      strategygames.Clock.SimpleDelayConfig(90, 10),
      Wave(31 seconds, 30 players),
      Variant.Backgammon(strategygames.backgammon.variant.Backgammon)
    ),
    PoolConfig(5 ++ 3, Wave(34 seconds, 30 players), Variant.FairySF(strategygames.fairysf.variant.Xiangqi)),
    PoolConfig(
      strategygames.Clock.SimpleDelayConfig(6 * 60, 2),
      Wave(37 seconds, 30 players),
      Variant.Abalone(strategygames.abalone.variant.Abalone)
    ),
    PoolConfig(3 ++ 5, Wave(40 seconds, 30 players), Variant.FairySF(strategygames.fairysf.variant.Amazons)),
    PoolConfig(
      3 ++ 2,
      Wave(43 seconds, 30 players),
      Variant.FairySF(strategygames.fairysf.variant.BreakthroughTroyka)
    ),
    PoolConfig(
      3 ++ 2,
      Wave(46 seconds, 30 players),
      Variant.Chess(strategygames.chess.variant.LinesOfAction)
    ),
    PoolConfig(
      strategygames.ByoyomiClock.Config(5 * 60, 0, 10, 1),
      Wave(49 seconds, 30 players),
      Variant.FairySF(strategygames.fairysf.variant.Shogi)
    ),
    PoolConfig(
      5 ++ 3,
      Wave(52 seconds, 30 players),
      Variant.Draughts(strategygames.draughts.variant.Standard)
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
