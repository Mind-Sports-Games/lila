package lila.pool

import scala.concurrent.duration._
import strategygames.variant.Variant
import strategygames.chess.variant.Standard
import lila.i18n.VariantKeys
import lila.rating.PerfType

import strategygames.{ ClockConfig, Speed }

case class PoolConfig(
    clock: ClockConfig,
    wave: PoolConfig.Wave,
    variant: Variant
) {

  val perfType =
    variant.key match {
      case "standard" =>
        PerfType.apply(Speed(clock).key) | PerfType
          .orDefault(Speed.Classical.key)

      case variantKey => PerfType.orDefault(variantKey)
    }

  val id = PoolConfig clockAndVariantToId (clock, variant)
}

object PoolConfig {

  case class Id(value: String)     extends AnyVal
  case class NbPlayers(value: Int) extends AnyVal

  case class Wave(every: FiniteDuration, players: NbPlayers)

  //def clockToId(clock: Clock.Config) = Id(clock.show)

  def clockAndVariantToId(clock: ClockConfig, variant: Variant) = Id(clock.show + "-" + variant.key)

  import play.api.libs.json._
  implicit val poolConfigJsonWriter = OWrites[PoolConfig] { p =>
    Json.obj(
      "id"         -> p.id.value,
      "lim"        -> p.clock.limitInMinutes,
      "inc"        -> p.clock.incrementSeconds,
      "perf"       -> p.perfType.trans(lila.i18n.defaultLang),
      "variantKey" -> VariantKeys.variantName(p.variant)
    )
  }
}
