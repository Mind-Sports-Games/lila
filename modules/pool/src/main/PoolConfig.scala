package lila.pool

import scala.concurrent.duration._

import lila.rating.PerfType

case class PoolConfig(
    clock: strategygames.Clock.Config,
    wave: PoolConfig.Wave
) {

  val perfType = PerfType(strategygames.Speed(clock).key) | PerfType.Classical

  val id = PoolConfig clockToId clock
}

object PoolConfig {

  case class Id(value: String)     extends AnyVal
  case class NbPlayers(value: Int) extends AnyVal

  case class Wave(every: FiniteDuration, players: NbPlayers)

  def clockToId(clock: strategygames.Clock.Config) = Id(clock.show)

  import play.api.libs.json._
  implicit val poolConfigJsonWriter = OWrites[PoolConfig] { p =>
    Json.obj(
      "id"   -> p.id.value,
      "lim"  -> p.clock.limitInMinutes,
      "inc"  -> p.clock.incrementSeconds,
      "perf" -> p.perfType.trans(lila.i18n.defaultLang)
    )
  }
}
