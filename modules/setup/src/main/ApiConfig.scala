package lila.setup

import strategygames.GameLib
import strategygames.variant.{ Variant => StratVariant }
import strategygames.format.{ FEN, Forsyth }
import strategygames.chess.variant.Chess960
import strategygames.chess.variant.FromPosition
import strategygames.{ Clock, Speed }

import lila.game.PerfPicker
import lila.lobby.Color
import lila.rating.PerfType
import strategygames.variant.Variant
import lila.common.Template

final case class ApiConfig(
    variant: strategygames.variant.Variant,
    clock: Option[Clock.Config],
    days: Option[Int],
    rated: Boolean,
    color: Color,
    position: Option[FEN] = None,
    acceptByToken: Option[String] = None,
    message: Option[Template],
    microMatch: Boolean
) {

  def perfType: Option[PerfType] = PerfPicker.perfType(strategygames.Speed(clock), variant, days)

  def validFen = ApiConfig.validFen(variant, position)

  def validSpeed(isBot: Boolean) =
    !isBot || clock.fold(true) { c =>
      Speed(c) >= Speed.Bullet
    }

  def validRated = mode.casual || clock.isDefined || variant.standard

  def mode = strategygames.Mode(rated)

  def autoVariant =
    if (variant.standard && position.exists(!_.initial)) copy(variant = Variant.wrap(FromPosition))
    else this
}

object ApiConfig extends BaseHumanConfig {

  lazy val clockLimitSeconds: Set[Int] = Set(0, 15, 30, 45, 60, 90) ++ (2 to 180).view.map(60 *).toSet

  def from(
      l: Int,
      cv: Option[String],
      dv: Option[String],
      cl: Option[Clock.Config],
      d: Option[Int],
      r: Boolean,
      c: Option[String],
      pos: Option[String],
      tok: Option[String],
      msg: Option[String],
      mm: Option[Boolean]
  ) =
    new ApiConfig(
      variant = strategygames.variant.Variant.orDefault(GameLib(l), l match {
        case 0 => ~cv
        case 1 => ~dv
      }),
      clock = cl,
      days = d,
      rated = r,
      color = Color.orDefault(~c),
      position = pos.map(f => FEN.apply(GameLib(l), f)),
      acceptByToken = tok,
      message = msg map Template,
      microMatch = ~mm
    ).autoVariant

  def validFen(variant: Variant, fen: Option[FEN]) =
    // TODO: This .get is unsafe
    if (variant.chess960) fen.forall(f => Chess960.positionNumber(f.chessFen.get).isDefined)
    else if (variant.fromPosition)
      fen exists { f =>
        (Forsyth.<<<(variant.gameLib, f)).exists(_.situation playable false)
      }
    else true
}
