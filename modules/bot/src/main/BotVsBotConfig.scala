package lila.bot

import strategygames.{ ClockConfig, GameLogic }
import strategygames.variant.Variant
import lila.common.LightUser

case class BotVsBotGame(
    p1: LightUser,
    p2: LightUser,
    variant: Variant,
    clock: ClockConfig
)

object BotVsBotConfig {

  val psBots: List[LightUser] =
    LightUser.tourBots ++ LightUser.poolBots ++ LightUser.stockfishBots ++ LightUser.gnubgBots :+
      LightUser.randomBot

  private val psBotsById: Map[String, LightUser] = psBots.map(u => u.id -> u).toMap

  def psBot(id: String): Option[LightUser] = psBotsById.get(id)

  val streamableVariants: List[Variant] = Variant.all.filterNot(_.fromPositionVariant).toList

  private val streamableVariantsByKey: Map[String, Variant] = streamableVariants.map(v => v.key -> v).toMap

  def streamableVariant(key: String): Option[Variant] = streamableVariantsByKey.get(key)

  private val stockfishVariants: List[Variant] =
    streamableVariants.filter(v => v.hasFishnet && v.gameLogic != GameLogic.Backgammon())

  private val nonStockfishOrBackgammonVariants: List[Variant] =
    streamableVariants.filter(v => !v.hasFishnet && v.gameLogic != GameLogic.Backgammon())

  private val backgammonVariants: List[Variant] =
    streamableVariants.filter(_.gameLogic == GameLogic.Backgammon())

  val variantGroups: List[(String, List[Variant])] = List(
    "Stockfish"                     -> stockfishVariants,
    "Non-Stockfish (no backgammon)" -> nonStockfishOrBackgammonVariants,
    "Backgammon"                    -> backgammonVariants
  )

  private[bot] def gamesForMatchup(
      p1: LightUser,
      p2: LightUser,
      variants: List[Variant],
      clock: ClockConfig
  ): List[BotVsBotGame] =
    for {
      variant <- variants
      (a, b)  <- List((p1, p2), (p2, p1))
    } yield BotVsBotGame(a, b, variant, clock)
}
