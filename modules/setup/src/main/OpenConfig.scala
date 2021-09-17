package lila.setup

import strategygames.{ Clock, DisplayLib }
import strategygames.variant.Variant
import strategygames.format.FEN
import strategygames.chess.variant.FromPosition

import lila.game.PerfPicker
import lila.rating.PerfType

final case class OpenConfig(
    name: Option[String],
    variant: strategygames.variant.Variant,
    clock: Option[Clock.Config],
    rated: Boolean,
    position: Option[FEN] = None
) {

  def perfType: Option[PerfType] = PerfPicker.perfType(strategygames.Speed(clock), variant, none)

  def validFen = ApiConfig.validFen(variant, position)

  def autoVariant =
    if (variant.standard && position.exists(!_.initial)) copy(variant = Variant.wrap(FromPosition))
    else this
}

object OpenConfig {

  def from(
      n: Option[String],
      l: Int,
      cv: Option[String],
      dv: Option[String],
      lv: Option[String],
      cl: Option[Clock.Config],
      rated: Boolean,
      pos: Option[String]
  ) =
    new OpenConfig(
      name = n.map(_.trim).filter(_.nonEmpty),
      variant = Variant.orDefault(DisplayLib(l).codeLib, l match {
        case 0 => ~cv
        case 1 => ~dv
        case 2 => ~lv
      }),
      clock = cl,
      rated = rated,
      position = pos.map(f => FEN.apply(DisplayLib(l).codeLib, f))
    ).autoVariant
}
