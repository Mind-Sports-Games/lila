package lila.pool

import scala.concurrent.duration._
import strategygames.variant.Variant

import lila.rating.PerfType

import strategygames.{ Clock, Speed }

case class PoolConfig(
    clock: Clock.Config,
    wave: PoolConfig.Wave,
    variant: Variant
) {

  val perfType =
    if (variant === Variant.Chess) PerfType.apply(Speed(clock).key) | PerfType.orDefault(Speed.Classical.key)
    else PerfType.apply(variant.key)

  val id = PoolConfig clockAndVariantToId clock variant
}

object PoolConfig {

  case class Id(value: String)     extends AnyVal
  case class NbPlayers(value: Int) extends AnyVal

  case class Wave(every: FiniteDuration, players: NbPlayers)

  //def clockToId(clock: Clock.Config) = Id(clock.show)

  def clockAndVariantToId(clock: Clock.Config, variant: Variant) = Id(clock.show + "-" + variant.key)

  import play.api.libs.json._
  implicit val poolConfigJsonWriter = OWrites[PoolConfig] { p =>
    Json.obj(
      "id"         -> p.id.value,
      "lim"        -> p.clock.limitInMinutes,
      "inc"        -> p.clock.incrementSeconds,
      "perf"       -> p.perfType.trans(lila.i18n.defaultLang),
      "variantKey" -> p.variant.key
    )
  }
}
