package lila.pool

import scala.concurrent.duration._
import strategygames.variant.Variant
import strategygames.chess.variant.Standard
import lila.i18n.VariantKeys
import lila.rating.PerfType

import strategygames.{ ByoyomiClock, Clock, ClockConfig, Speed }

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

  val id = PoolConfig.clockAndVariantToId(clock, variant)
}

object PoolConfig {

  case class Id(value: String)     extends AnyVal
  case class NbPlayers(value: Int) extends AnyVal

  case class Wave(every: FiniteDuration, players: NbPlayers)

  //def clockToId(clock: Clock.Config) = Id(clock.show)

  def clockAndVariantToId(clock: ClockConfig, variant: Variant) = Id(
    clock.show.replace("|", "-") + "-" + variant.key
  )

  import play.api.libs.json._
  implicit val poolConfigJsonWriter = OWrites[PoolConfig] { p =>
    p.clock match {
      case fc: Clock.Config =>
        Json.obj(
          "id"         -> p.id.value,
          "lim"        -> fc.limitInMinutes,
          "inc"        -> fc.incrementSeconds,
          "perf"       -> p.perfType.trans(lila.i18n.defaultLang),
          "variantKey" -> VariantKeys.variantName(p.variant)
        )
      case fc: Clock.BronsteinConfig =>
        Json.obj(
          "id"         -> p.id.value,
          "lim"        -> fc.limitInMinutes,
          "delay"      -> fc.delaySeconds,
          "perf"       -> p.perfType.trans(lila.i18n.defaultLang),
          "variantKey" -> VariantKeys.variantName(p.variant)
        )
      case bc: ByoyomiClock.Config =>
        Json.obj(
          "id"         -> p.id.value,
          "lim"        -> bc.limitInMinutes,
          "inc"        -> bc.incrementSeconds,
          "byoyomi"    -> bc.byoyomiSeconds,
          "periods"    -> bc.periodsTotal,
          "perf"       -> p.perfType.trans(lila.i18n.defaultLang),
          "variantKey" -> VariantKeys.variantName(p.variant)
        )
    }
  }
}
