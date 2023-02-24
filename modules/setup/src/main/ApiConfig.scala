package lila.setup

import strategygames.variant.Variant
import strategygames.chess.variant.{ Chess960, FromPosition }
import strategygames.format.{ FEN, Forsyth }
import strategygames.{ Clock, GameFamily, Mode, Speed }

import lila.game.PerfPicker
import lila.lobby.PlayerIndex
import lila.rating.PerfType
import lila.common.Template

final case class ApiConfig(
    variant: Variant,
    clock: Option[Clock.Config],
    days: Option[Int],
    rated: Boolean,
    playerIndex: PlayerIndex,
    position: Option[FEN] = None,
    acceptByToken: Option[String] = None,
    message: Option[Template],
    multiMatch: Boolean
) {

  def perfType: Option[PerfType] = PerfPicker.perfType(Speed(clock), variant, days)

  def validFen = ApiConfig.validFen(variant, position)

  def validSpeed(isBot: Boolean) =
    !isBot || clock.fold(true) { c =>
      Speed(c) >= Speed.Bullet
    }

  def validRated = mode.casual || clock.isDefined || variant.standard

  def mode = Mode(rated)

  def autoVariant =
    if (variant.standard && position.exists(!_.initial)) copy(variant = Variant.wrap(FromPosition))
    else this
}

object ApiConfig extends BaseHumanConfig {

  lazy val clockLimitSeconds: Set[Int] = Set(0, 15, 30, 45, 60, 90) ++ (2 to 180).view.map(60 *).toSet

  def from(
      v: Option[String],
      cl: Option[Clock.Config],
      d: Option[Int],
      r: Boolean,
      c: Option[String],
      pos: Option[String],
      tok: Option[String],
      msg: Option[String],
      mm: Option[Boolean]
  ) = {
    val variant = Variant.orDefault(~v)
    new ApiConfig(
      variant = variant,
      clock = cl,
      days = d,
      rated = r,
      playerIndex = PlayerIndex.orDefault(~c),
      position = pos.map(f => FEN.apply(variant.gameLogic, f)),
      acceptByToken = tok,
      message = msg map Template,
      multiMatch = ~mm
    ).autoVariant
  }

  def validFen(variant: Variant, fen: Option[FEN]) =
    // TODO: This .get is unsafe
    if (variant.chess960) fen.forall(f => Chess960.positionNumber(f.chessFen.get).isDefined)
    else if (variant.fromPosition)
      fen exists { f =>
        (Forsyth.<<<(variant.gameLogic, f)).exists(_.situation playable false)
      }
    else true
}
