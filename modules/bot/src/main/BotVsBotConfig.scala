package lila.bot

import strategygames.{Clock, GameLogic}
import strategygames.variant.Variant
import lila.common.LightUser

case class BotVsBotGame(
    p1: LightUser,
    p2: LightUser,
    variant: Variant,
    clock: Clock.Config
)

case class BotVsBotStream(name: String, games: List[BotVsBotGame])

object BotVsBotConfig {

  private val rapidClock = Clock.Config(3 * 60, 2) // 3+2

  private val stockfishVariants: List[Variant] =
    Variant.all.filter(v => v.hasFishnet && !v.fromPositionVariant).toList

  private val nonStockfishOrBackgammonVariants: List[Variant] =
    Variant.all
      .filter(v => !v.hasFishnet && !v.fromPositionVariant && v.gameLogic != GameLogic.Backgammon())
      .toList

  private val backgammonVariants: List[Variant] =
    Variant.all(GameLogic.Backgammon()).filter(!_.fromPositionVariant)

  private def gamesForMatchup(p1: LightUser, p2: LightUser, variants: List[Variant]): List[BotVsBotGame] =
    for {
      variant <- variants
      (a, b)  <- List((p1, p2), (p2, p1))
    } yield BotVsBotGame(a, b, variant, rapidClock)

  val streams: List[BotVsBotStream] = {
    import LightUser.*
    List(
      BotVsBotStream(
        "Stockfish-8 vs Stockfish-7",
        gamesForMatchup(stockfishBots(7), stockfishBots(6), stockfishVariants)
      ),
      BotVsBotStream(
        "Greedy-Two-Move vs Stockfish-3/One-Move/GNUBG-3",
        gamesForMatchup(poolBots(1), stockfishBots(2), stockfishVariants) ++
          gamesForMatchup(poolBots(1), poolBots(0), nonStockfishOrBackgammonVariants) ++
          gamesForMatchup(poolBots(1), gnubgBots(2), backgammonVariants)
      ),
    )
  }

  val allBotIds: Set[String] =
    streams.flatMap(_.games.flatMap(g => List(g.p1.id, g.p2.id))).toSet
}
