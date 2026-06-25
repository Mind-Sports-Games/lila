package lila.bot

import strategygames.{ Clock, GameFamily }
import strategygames.variant.Variant
import lila.common.LightUser

case class BotVsBotGame(
    p1: LightUser,
    p2: LightUser,
    variant: Variant,
    clock: Clock.Config
)

object BotVsBotConfig {

  private val rapidClock = Clock.Config(3 * 60, 2) // 3+2

  private val stockfishVariants: List[Variant] =
    GameFamily.all
      .filter(_.hasFishnet)
      .flatMap(_.variants.filter(!_.fromPositionVariant))
      .toList

  private val allVariants: List[Variant] =
    GameFamily.all
      .flatMap(_.variants.filter(!_.fromPositionVariant))
      .toList

  private val matchups: List[(LightUser, LightUser, List[Variant])] = {
    import LightUser.*
    List(
      (stockfishBots(0), poolBots(1), stockfishVariants), // Stockfish-Level1 vs PS-Greedy-Two-Move
      (stockfishBots(1), poolBots(1), stockfishVariants), // Stockfish-Level2 vs PS-Greedy-Two-Move
      (poolBots(1), poolBots(0), allVariants),             // PS-Greedy-Two-Move vs PS-Greedy-One-Move
      (stockfishBots(7), stockfishBots(6), stockfishVariants), // Stockfish-Level8 vs Stockfish-Level7
    )
  }

  // Each (matchup, variant) produces two games: p1 vs p2 then p2 vs p1
  val allGames: List[BotVsBotGame] = for {
    (p1, p2, variants) <- matchups
    variant            <- variants
    (a, b)             <- List((p1, p2), (p2, p1))
  } yield BotVsBotGame(a, b, variant, rapidClock)

  val allBotIds: Set[String] = matchups.flatMap { case (p1, p2, _) => List(p1.id, p2.id) }.toSet
}
