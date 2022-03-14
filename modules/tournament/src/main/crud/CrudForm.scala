package lila.tournament
package crud

import org.joda.time.DateTime
import play.api.data._
import play.api.data.Forms._

import strategygames.variant.Variant
import lila.common.Form._
import strategygames.format.FEN
import strategygames.{ GameFamily, GameLogic }

object CrudForm {

  import TournamentForm._
  import lila.common.Form.ISODateTime._

  val maxHomepageHours = 168

  lazy val apply = Form(
    mapping(
      "name"           -> text(minLength = 3, maxLength = 40),
      "homepageHours"  -> number(min = 0, max = maxHomepageHours),
      "clockTime"      -> numberInDouble(clockTimeChoices),
      "clockIncrement" -> numberIn(clockIncrementChoices),
      "minutes"        -> number(min = 20, max = 1440),
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
    clockTime = clockTimeDefault,
    clockIncrement = clockIncrementDefault,
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
      clockTime: Double,
      clockIncrement: Int,
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

    def validClock = (clockTime + clockIncrement) > 0

    def validTiming = (minutes * 60) >= (3 * estimatedGameDuration)

    private def estimatedGameDuration = 60 * clockTime + 30 * clockIncrement
  }

  val imageChoices = List(
    ""             -> "PlayStrategy",
    "mso.logo.png" -> "MSO"
  )
  val imageDefault = ""
}
