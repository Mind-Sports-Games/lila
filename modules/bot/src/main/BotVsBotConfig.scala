package lila.bot

import cats.implicits.*
import scala.concurrent.duration.*
import strategygames.{Clock, GameLogic}
import strategygames.variant.Variant
import lila.common.LightUser
import lila.game.Source

case class BotVsBotGame(
    p1: LightUser,
    p2: LightUser,
    variant: Variant,
    clock: Clock.Config
)

case class BotVsBotStream(
    name: String,
    games: List[BotVsBotGame],
    source: Source = Source.BotVsBotStream
)

case class BotVsBotSpotlight(stream: BotVsBotStream, duration: Option[FiniteDuration])

object BotVsBotSpotlight {

  val name = "Spotlight"

  private val psBots: List[LightUser] =
    LightUser.tourBots ++ LightUser.poolBots ++ LightUser.stockfishBots ++ LightUser.gnubgBots :+
      LightUser.randomBot

  private val clockPattern = """(\d+)\+(\d+)""".r

  private def parseBots(value: String): Either[String, (LightUser, LightUser)] =
    value.split(',').toList.map(id => psBots.find(_.id == id.toLowerCase).toRight(id)) match {
      case List(Right(p1), Right(p2)) => Right(p1 -> p2)
      case List(_, _) =>
        Left(s"Not a PS bot: $value. Available: ${psBots.map(_.id).mkString(", ")}")
      case _ => Left(s"bots= takes two PS bot ids separated by a comma, got: $value")
    }

  private def parseClock(value: String): Either[String, Clock.Config] =
    value match {
      case clockPattern(minutes, increment) => Right(Clock.Config(minutes.toInt * 60, increment.toInt))
      case _                                => Left(s"clock= takes minutes+increment, e.g. 3+2, got: $value")
    }

  private def parseHours(value: String): Either[String, FiniteDuration] =
    value.toIntOption.filter(_ > 0).map(_.hours).toRight(s"hours= takes a positive whole number, got: $value")

  private def parseVariants(value: String): Either[String, List[Variant]] =
    value.split(',').toList.traverse { key =>
      Variant.byKey.get(key).filterNot(_.fromPositionVariant).toRight(s"Unknown variant: $key")
    }

  private val optionKeys = Set("bots", "clock", "hours")

  private def parseOption(arg: String): Either[String, (String, String)] =
    arg.split("=", 2) match {
      case Array(k, v) if optionKeys(k) => Right(k -> v)
      case _                            => Left(s"Unknown option: $arg")
    }

  def parse(args: List[String]): Either[String, BotVsBotSpotlight] =
    for {
      variantKeys <- args.headOption.toRight(
        "Usage: bot-vs-bot spotlight <variantKey,...> [bots=<id>,<id>] [clock=3+2] [hours=N]"
      )
      options  <- args.drop(1).traverse(parseOption).map(_.toMap)
      variants <- parseVariants(variantKeys)
      bots     <- options.get("bots").fold(Right(LightUser.poolBots(1) -> LightUser.poolBots(0)))(parseBots)
      clock    <- options.get("clock").fold(Right(BotVsBotConfig.blitzClock))(parseClock)
      duration <- options.get("hours").traverse(parseHours)
    } yield BotVsBotSpotlight(
      BotVsBotStream(
        name,
        BotVsBotConfig.gamesForMatchup(bots._1, bots._2, variants, clock),
        Source.BotVsBotSpotlight
      ),
      duration
    )
}

object BotVsBotConfig {

  private[bot] val blitzClock = Clock.Config(3 * 60, 2) // 3+2

  private val stockfishVariants: List[Variant] =
    Variant.all
      .filter(v => v.hasFishnet && !v.fromPositionVariant && v.gameLogic != GameLogic.Backgammon())
      .toList

  private val nonStockfishOrBackgammonVariants: List[Variant] =
    Variant.all
      .filter(v => !v.hasFishnet && !v.fromPositionVariant && v.gameLogic != GameLogic.Backgammon())
      .toList

  private val backgammonVariants: List[Variant] =
    Variant.all(GameLogic.Backgammon()).filter(!_.fromPositionVariant)

  private[bot] def gamesForMatchup(
      p1: LightUser,
      p2: LightUser,
      variants: List[Variant],
      clock: Clock.Config = blitzClock
  ): List[BotVsBotGame] =
    for {
      variant <- variants
      (a, b)  <- List((p1, p2), (p2, p1))
    } yield BotVsBotGame(a, b, variant, clock)

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
