package lila.pool

import scala.concurrent.duration._

import lila.rating.PerfType

import strategygames.{ Clock, Speed }

case class PoolConfig(
    clock: Clock.Config,
    wave: PoolConfig.Wave
) {

  val perfType = PerfType.apply(Speed(clock).key) | PerfType.orDefault(Speed.Classical.key)

  val id = PoolConfig clockToId clock
}

object PoolConfig {

  case class Id(value: String)     extends AnyVal
  case class NbPlayers(value: Int) extends AnyVal

  case class Wave(every: FiniteDuration, players: NbPlayers)

  def clockToId(clock: Clock.Config) = Id(clock.show)

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
