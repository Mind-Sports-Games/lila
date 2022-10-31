package lila.simul

import cats.implicits._
import strategygames.{ GameFamily, GameLogic }
import strategygames.format.FEN
import strategygames.variant.Variant
import strategygames.chess.StartingPosition
import org.joda.time.DateTime
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraint

import lila.common.Form._
import lila.hub.LeaderTeam
import lila.user.User

object SimulForm {

  val clockTimes       = (5 to 15 by 5) ++ (20 to 90 by 10) ++ (120 to 180 by 20)
  val clockTimeDefault = 20
  val clockTimeChoices = options(clockTimes, "%d minute{s}")

  val clockIncrements       = (0 to 2 by 1) ++ (3 to 7) ++ (10 to 30 by 5) ++ (40 to 60 by 10) ++ (90 to 180 by 30)
  val clockIncrementDefault = 60
  val clockIncrementChoices = options(clockIncrements, "%d second{s}")

  val clockExtras       = (0 to 15 by 5) ++ (20 to 60 by 10) ++ (90 to 120 by 30)
  val clockExtraChoices = options(clockExtras, "%d minute{s}")
  val clockExtraDefault = 0

  val playerIndexs = List("p1", "random", "p2")
  val playerIndexChoices = List(
    "p1"     -> "Player 1",
    "random" -> "Random Side",
    "p2"     -> "Player 2"
  )
  val playerIndexDefault = "p1"

  private def nameType(host: User) =
    eventName(2, 40).verifying(
      Constraint[String] { (t: String) =>
        if (t.toLowerCase contains "playstrategy")
          validation.Invalid(validation.ValidationError("Must not contain \"playstrategy\""))
        else validation.Valid
      },
      Constraint[String] { (t: String) =>
        if (
          t.toUpperCase.split(' ').exists { word =>
            lila.user.Title.all.exists { case (title, name) =>
              !host.title.has(title) && {
                title.value == word || name.toUpperCase == word
              }
            }
          }
        )
          validation.Invalid(validation.ValidationError("Must not contain a title"))
        else validation.Valid
      }
    )

  def create(host: User, teams: List[LeaderTeam]) =
    baseForm(host, teams) fill Setup(
      name = host.titleUsername,
      clockTime = clockTimeDefault,
      clockIncrement = clockIncrementDefault,
      clockExtra = clockExtraDefault,
      variants = List(s"${GameFamily.Chess().id}_${Variant.default(GameLogic.Chess()).id}"),
      position = none,
      playerIndex = playerIndexDefault,
      text = "",
      estimatedStartAt = none,
      team = none,
      featured = host.isSimulFeatured.some // it was: host.hasTitle.some
    )

  def edit(host: User, teams: List[LeaderTeam], simul: Simul) =
    baseForm(host, teams) fill Setup(
      name = simul.name,
      clockTime = simul.clock.config.limitInMinutes.toInt,
      clockIncrement = simul.clock.config.increment.roundSeconds,
      clockExtra = simul.clock.hostExtraMinutes,
      variants = simul.variants.map(v => s"${v.gameFamily.id}_${v.id}"),
      position = simul.position,
      playerIndex = simul.playerIndex | "random",
      text = simul.text,
      estimatedStartAt = simul.estimatedStartAt,
      team = simul.team,
      featured = simul.featurable
    )

  private def baseForm(host: User, teams: List[LeaderTeam]) =
    Form(
      mapping(
        "name"           -> nameType(host),
        "clockTime"      -> numberIn(clockTimeChoices),
        "clockIncrement" -> numberIn(clockIncrementChoices),
        "clockExtra"     -> numberIn(clockExtraChoices),
        //only variants that arent FromPosition
        "variants" -> list {
          nonEmptyText.verifying(g =>
            Variant.all
              .filter(v => !v.fromPositionVariant)
              .map(v => s"${v.gameFamily.id}_${v.id}")
              .toSet contains g
          )
        }.verifying("At least one variant", _.nonEmpty),
        "position"         -> optional(lila.common.Form.fen.playableStrict),
        "playerIndex"      -> stringIn(playerIndexChoices),
        "text"             -> cleanText,
        "estimatedStartAt" -> optional(inTheFuture(ISODateTimeOrTimestamp.isoDateTimeOrTimestamp)),
        "team"             -> optional(nonEmptyText.verifying(id => teams.exists(_.id == id))),
        "featured"         -> optional(boolean)
      )(Setup.apply)(Setup.unapply)
        .verifying("Only allowed a different starting fen if only playing standard chess", _.validUsePosition)
    )

  val positions = StartingPosition.allWithInitial.map(_.fen)
  val positionChoices = StartingPosition.allWithInitial.map { p =>
    p.fen -> p.fullName
  }
  val positionDefault = StartingPosition.initial.fen

  def setText = Form(single("text" -> text))

  case class Setup(
      name: String,
      clockTime: Int,
      clockIncrement: Int,
      clockExtra: Int,
      variants: List[String],
      position: Option[FEN],
      playerIndex: String,
      text: String,
      estimatedStartAt: Option[DateTime] = None,
      team: Option[String],
      featured: Option[Boolean]
  ) {
    def clock =
      SimulClock(
        config = strategygames.Clock.Config(clockTime * 60, clockIncrement),
        hostExtraTime = clockExtra * 60
      )

    def actualVariants: List[Variant] = variants map { v =>
      Variant
        .orDefault(
          GameFamily(v.split("_")(0).toInt).gameLogic,
          v.split("_")(1).toInt
        )
    }

    def realPosition = position.filterNot(_.initial)

    def validUsePosition: Boolean =
      position.fold(true)(_ => variants.size == 1 && variants(0) == "0_1")
  }
}
