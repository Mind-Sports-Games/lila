package lila.setup

import strategygames.variant.Variant
import strategygames.chess.variant.{ Chess960, FromPosition, Standard }
import strategygames.format.{ FEN, Forsyth }
import strategygames.{ ByoyomiClock, Clock, ClockConfig, GameFamily, Mode, Speed }

import lila.game.PerfPicker
import lila.lobby.PlayerIndex
import lila.rating.PerfType
import lila.common.Template

import scala.util.Random

final case class ApiConfig(
    variant: Variant,
    clock: Option[ClockConfig],
    days: Option[Int],
    rated: Boolean,
    playerIndex: PlayerIndex,
    position: Option[FEN] = None,
    acceptByToken: Option[String] = None,
    message: Option[Template],
    multiMatch: Boolean,
    backgammonPoints: Option[Int] = None
) {

  def perfType: Option[PerfType] = PerfPicker.perfType(Speed(clock), variant, days)

  def validFen = ApiConfig.validFen(variant, position)

  def initialFen: Option[FEN] = position.flatMap(p =>
    if (variant.initialFens.contains(p) && variant.initialFens.size > 1)
      Random.shuffle(variant.initialFens).headOption
    else Some(p)
  )

  def validSpeed(isBot: Boolean) =
    !isBot || clock.fold(true) { c =>
      Speed(c) >= Speed.Bullet
    }

  def validRated = mode.casual || clock.isDefined || variant == Variant.Chess(Standard)

  def mode = Mode(rated)

  def autoVariant =
    if (variant == Variant.Chess(Standard) && position.exists(!_.initial))
      copy(variant = Variant.wrap(FromPosition))
    else this

}

object ApiConfig extends BaseHumanConfig {

  lazy val clockLimitSeconds: Set[Int] = Set(0, 15, 30, 45, 60, 90) ++ (2 to 180).view.map(60 *).toSet

  def from(
      v: Option[String],
      fcl: Option[Clock.Config],
      sdc: Option[Clock.SimpleDelayConfig],
      bdc: Option[Clock.BronsteinConfig],
      bcl: Option[ByoyomiClock.Config],
      d: Option[Int],
      r: Boolean,
      c: Option[String],
      pos: Option[String],
      tok: Option[String],
      msg: Option[String],
      mm: Option[Boolean],
      bp: Option[Int]
  ) = {
    val variant = Variant.orDefault(~v)
    new ApiConfig(
      variant = variant,
      clock = bcl.orElse(sdc).orElse(bdc).orElse(fcl),
      days = d,
      rated = r,
      playerIndex = PlayerIndex.orDefault(~c),
      position = pos.map(f => FEN.apply(variant.gameLogic, f)),
      acceptByToken = tok,
      message = msg map Template,
      multiMatch = ~mm,
      backgammonPoints = bp
    ).autoVariant
  }

  def validFen(variant: Variant, fen: Option[FEN]) =
    // TODO: This .get is unsafe
    if (variant == Variant.Chess(Chess960)) fen.forall(f => Chess960.positionNumber(f.chessFen.get).isDefined)
    else if (variant.fromPositionVariant)
      fen exists { f =>
        (Forsyth.<<<(variant.gameLogic, f)).exists(_.situation playable false)
      }
    else true
}
