package lila.tournament
package crud

import org.joda.time.DateTime
import play.api.data._
import play.api.data.Forms._

import strategygames.variant.Variant
import lila.common.Form._
import strategygames.format.FEN
import strategygames.{ ByoyomiClock, Clock, ClockConfig, GameFamily, GameLogic }

object CrudForm {

  import TournamentForm._
  import lila.common.Form.ISODateTime._

  val maxHomepageHours = 168

  // Yes, I know this is kinda gross. :'(
  private def valuesFromClockConfig(
      c: ClockConfig
  ): Option[(Boolean, Double, Int, Option[Int], Option[Int])] =
    c match {
      case fc: Clock.Config => {
        Clock.Config.unapply(fc).map(t => (false, t._1 / 4d, t._2, None, None))
      }
      case bc: Clock.BronsteinConfig => {
        Clock.BronsteinConfig.unapply(bc).map(t => (false, t._1 / 4d, t._2, None, None))
      }
      case bc: ByoyomiClock.Config => {
        ByoyomiClock.Config.unapply(bc).map(t => (true, t._1 / 4d, t._2, Some(t._3), Some(t._4)))
      }
    }

  // Yes, I know this is kinda gross. :'(
  private def clockConfigFromValues(
      useByoyomi: Boolean,
      limit: Double,
      increment: Int,
      byoyomi: Option[Int],
      periods: Option[Int]
  ): ClockConfig = // TODO: deal with Bronstein as well
    (useByoyomi, byoyomi, periods) match {
      case (true, Some(byoyomi), Some(periods)) =>
        ByoyomiClock.Config((limit * 60).toInt, increment, byoyomi, periods)
      case _ =>
        Clock.Config((limit * 60).toInt, increment)
    }

  lazy val apply = Form(
    mapping(
      "name"          -> text(minLength = 3, maxLength = 40),
      "homepageHours" -> number(min = 0, max = maxHomepageHours),
      "clock" -> mapping[ClockConfig, Boolean, Double, Int, Option[Int], Option[Int]](
        "useByoyomi" -> boolean,
        "limit"      -> numberInDouble(clockTimeChoices),
        "increment"  -> numberIn(clockIncrementChoices),
        "byoyomi"    -> optional(numberIn(clockByoyomiChoices)),
        "periods"    -> optional(numberIn(periodsChoices))
      )(clockConfigFromValues)(valuesFromClockConfig)
        .verifying("Invalid clock", _.estimateTotalSeconds > 0),
      "minutes" -> number(min = 20, max = 1440),
      "variant" -> optional(
        nonEmptyText.verifying(v =>
          Variant(GameFamily(v.split("_")(0).toInt).gameLogic, v.split("_")(1).toInt).isDefined
        )
      ),
      "position"    -> optional(lila.common.Form.fen.playableStrict),
      "date"        -> isoDateTime,
      "image"       -> stringIn(imageChoices),
      "headline"    -> text(minLength = 5, maxLength = 30),
      "description" -> text(minLength = 10, maxLength = 400),
      "conditions"  -> Condition.DataForm.all(Nil),
      "berserkable" -> boolean,
      "streakable"  -> boolean,
      "teamBattle"  -> boolean,
      "hasChat"     -> boolean
    )(CrudForm.Data.apply)(CrudForm.Data.unapply)
      .verifying("Invalid clock", _.validClock)
      .verifying("Increase tournament duration, or decrease game clock", _.validTiming)
  ) fill CrudForm.Data(
    name = "",
    homepageHours = 0,
    clock = Clock.Config(180, 0),
    minutes = minuteDefault,
    variant = s"${GameFamily.Chess().id}_${Variant.default(GameLogic.Chess()).id}".some,
    position = none,
    date = DateTime.now plusDays 7,
    image = "",
    headline = "",
    description = "",
    conditions = Condition.DataForm.AllSetup.default,
    berserkable = true,
    streakable = true,
    teamBattle = false,
    hasChat = true
  )

  case class Data(
      name: String,
      homepageHours: Int,
      clock: ClockConfig,
      minutes: Int,
      variant: Option[String],
      position: Option[FEN],
      date: DateTime,
      image: String,
      headline: String,
      description: String,
      conditions: Condition.DataForm.AllSetup,
      berserkable: Boolean,
      streakable: Boolean,
      teamBattle: Boolean,
      hasChat: Boolean
  ) {

    def gameLogic = variant match {
      case Some(v) => GameFamily(v.split("_")(0).toInt).gameLogic
      case None    => GameLogic.Chess()
    }

    def realVariant = variant flatMap { v =>
      Variant.apply(gameLogic, v.split("_")(1).toInt)
    } getOrElse Variant.default(gameLogic)

    def realPosition = position ifTrue realVariant.standard

    def validClock = (clock.limitSeconds + clock.incrementSeconds) > 0

    def validTiming = (minutes * 60) >= (3 * estimatedGameDuration)

    private def estimatedGameDuration = 60 * clock.limitSeconds + 30 * clock.incrementSeconds
  }

  val imageChoices = List(
    ""             -> "PlayStrategy",
    "mso.logo.png" -> "MSO"
  )
  val imageDefault = ""
}
