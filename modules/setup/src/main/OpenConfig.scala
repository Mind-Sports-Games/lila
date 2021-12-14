package lila.setup

import strategygames.{ Clock, GameFamily, Speed }
import strategygames.variant.Variant
import strategygames.format.FEN
import strategygames.chess.variant.FromPosition

import lila.game.PerfPicker
import lila.rating.PerfType

final case class OpenConfig(
    name: Option[String],
    variant: Variant,
    clock: Option[Clock.Config],
    rated: Boolean,
    position: Option[FEN] = None
) {

  def perfType: Option[PerfType] = PerfPicker.perfType(Speed(clock), variant, none)

  def validFen = ApiConfig.validFen(variant, position)

  def autoVariant =
    if (variant.standard && position.exists(!_.initial)) copy(variant = Variant.wrap(FromPosition))
    else this
}

object OpenConfig {

  def from(
      n: Option[String],
      v: Option[String],
      cl: Option[Clock.Config],
      rated: Boolean,
      pos: Option[String]
  ) = {
    val variant = Variant.orDefault(~v)
    new OpenConfig(
      name = n.map(_.trim).filter(_.nonEmpty),
      variant = variant,
      clock = cl,
      rated = rated,
      position = pos.map(f => FEN.apply(variant.gameLogic, f))
    ).autoVariant
  }
}
