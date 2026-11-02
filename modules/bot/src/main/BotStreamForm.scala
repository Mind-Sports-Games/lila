package lila.bot

import cats.implicits.*
import org.joda.time.DateTime
import play.api.data.*
import play.api.data.Forms.*
import play.api.data.validation.{ Constraint, Invalid, Valid }
import strategygames.{ ByoyomiClock, Clock, ClockConfig }
import strategygames.variant.Variant

import lila.common.Clock.*
import lila.common.Form.*
import lila.common.Form.UTCDate.*
import lila.user.User

object BotStreamForm {

  val maxMatchups = 5

  val clockTimes: Seq[Double] = Seq(0d, 1 / 4d, 1 / 2d, 3 / 4d, 1d, 3 / 2d) ++ {
    (2 to 7 by 1) ++ (10 to 30 by 5) ++ (40 to 60 by 10)
  }.map(_.toDouble)
  val clockTimeChoices = clockTimeChoicesFromMinutes(clockTimes)

  val clockIncrementChoices = options((0 to 5) ++ (6 to 12 by 2) ++ (15 to 30 by 5) ++ (40 to 60 by 10), "%d second{s}")
  val clockDelayChoices     = clockIncrementChoices

  val clockByoyomi        = (1 to 9 by 1) ++ (10 to 30 by 5) ++ (40 to 60 by 10)
  val clockByoyomiChoices = options(clockByoyomi, "%d second{s}")
  val periodsChoices      = options(1 to 5, "%d period{s}")

  private def validClock(clock: ClockConfig) = clock match {
    case fc: Clock.Config             => (fc.limitSeconds + fc.incrementSeconds) > 0
    case bc: Clock.BronsteinConfig    => bc.limitSeconds > 0 && bc.delaySeconds > 0
    case udc: Clock.SimpleDelayConfig => udc.limitSeconds > 0 && udc.delaySeconds > 0
    case bc: ByoyomiClock.Config      =>
      (bc.limitSeconds + bc.incrementSeconds) > 0 || (bc.limitSeconds + bc.byoyomiSeconds) > 0
  }

  case class MatchupData(p1: String, p2: String, variants: String) {

    def isBlank = p1.isEmpty && p2.isEmpty && variants.trim.isEmpty

    private def parseVariants: Either[String, List[Variant]] =
      variants.split("[,\\s]+").toList.filter(_.nonEmpty).distinct.traverse { key =>
        BotVsBotConfig.streamableVariant(key).toRight(s"Unknown variant: $key")
      }

    def toMatchup: Either[String, BotStream.Matchup] =
      for {
        _        <- BotVsBotConfig.psBot(p1).toRight(s"Pick a PS bot for player 1")
        _        <- BotVsBotConfig.psBot(p2).toRight(s"Pick a PS bot for player 2")
        _        <- Either.cond(p1 != p2, (), "A bot cannot play itself")
        variants <- parseVariants
        _        <- Either.cond(variants.nonEmpty, (), "List at least one variant")
      } yield BotStream.Matchup(p1, p2, variants)
  }

  private val validMatchup = Constraint[MatchupData] { m =>
    if (m.isBlank) Valid else m.toMatchup.fold(e => Invalid(e), _ => Valid)
  }

  private val maxVariantsLength = BotVsBotConfig.streamableVariants.map(_.key.length + 3).sum

  def form = Form(
    mapping(
      "name"           -> text(minLength = 3, maxLength = 60),
      "spotlight"      -> boolean,
      "clock"          -> clockConfigMappingsMinutes(clockTimes, clockByoyomi).verifying("Invalid clock", validClock),
      "until"          -> optional(utcDate),
      "matchups"       -> list(
        mapping(
          "p1"       -> text(maxLength = 30),
          "p2"       -> text(maxLength = 30),
          "variants" -> text(maxLength = maxVariantsLength)
        )(MatchupData.apply)(unapply).verifying(validMatchup)
      ).verifying("Add at least one matchup", _.exists(!_.isBlank))
        .verifying(s"At most $maxMatchups matchups", _.sizeIs <= maxMatchups)
    )(Data.apply)(unapply)
  ).fill(
    Data(
      name = "",
      spotlight = true,
      clock = Clock.Config(180, 2),
      until = DateTime.now.plusWeeks(2).some,
      matchups = Nil
    )
  )

  case class Data(
      name: String,
      spotlight: Boolean,
      clock: ClockConfig,
      until: Option[DateTime],
      matchups: List[MatchupData]
  ) {

    private def validMatchups = matchups.filterNot(_.isBlank).flatMap(_.toMatchup.toOption)

    def make(by: User.ID) =
      BotStream(
        _id = BotStream.makeId,
        name = name.trim,
        matchups = validMatchups,
        clock = clock,
        spotlight = spotlight,
        active = false,
        until = until,
        createdBy = by,
        createdAt = DateTime.now,
        updatedAt = DateTime.now
      )

    def update(old: BotStream) =
      old.copy(
        name = name.trim,
        matchups = validMatchups,
        clock = clock,
        spotlight = spotlight,
        until = until,
        updatedAt = DateTime.now
      )
  }

  object Data {

    def make(stream: BotStream) =
      Data(
        name = stream.name,
        spotlight = stream.spotlight,
        clock = stream.clock,
        until = stream.until,
        matchups = stream.matchups.map { m =>
          MatchupData(m.p1, m.p2, m.variants.map(_.key).mkString(", "))
        }
      )
  }
}
