package lila.game

import strategygames.Speed
import strategygames.variant.Variant
import strategygames.chess.variant.{ FromPosition, Standard }
import lila.rating.{ Perf, PerfType }
import lila.user.Perfs

object PerfPicker {

  val default = (perfs: Perfs) => perfs.standard

  def perfType(speed: Speed, variant: Variant, daysPerTurn: Option[Int]): Option[PerfType] =
    PerfType(key(speed, variant, daysPerTurn))

  def key(speed: Speed, variant: Variant, daysPerTurn: Option[Int]): String =
    if (variant == Variant.Chess(Standard)) {
      if (daysPerTurn.isDefined || speed == Speed.Correspondence) Speed.Correspondence.key
      else speed.key
    } else variant.key

  def key(game: Game): String = key(game.speed, game.ratingVariant, game.daysPerTurn)

  def main(speed: Speed, variant: Variant, daysPerTurn: Option[Int]): Option[Perfs => Perf] =
    if (variant == Variant.Chess(Standard)) Some {
      if (daysPerTurn.isDefined) (perfs: Perfs) => perfs.correspondence
      else Perfs speedLens speed
    }
    else Perfs variantLens variant

  def main(game: Game): Option[Perfs => Perf] = main(game.speed, game.ratingVariant, game.daysPerTurn)

  def mainOrDefault(speed: Speed, variant: Variant, daysPerTurn: Option[Int]): Perfs => Perf =
    main(speed, variant, daysPerTurn) orElse {
      (variant == Variant.Chess(FromPosition)) ?? main(speed, Variant.Chess(Standard), daysPerTurn)
    } getOrElse default

  def mainOrDefault(game: Game): Perfs => Perf =
    mainOrDefault(game.speed, game.ratingVariant, game.daysPerTurn)
}
