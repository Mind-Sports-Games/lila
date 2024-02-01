package lila.setup

import strategygames.{ ByoyomiClock, Clock, ClockConfig, GameFamily, Mode, P1, P2, Speed }
import strategygames.variant.Variant
import strategygames.format.FEN
import strategygames.chess.variant.{ FromPosition, Standard }

import lila.game.{ Game, Player, Pov, Source }
import lila.lobby.PlayerIndex
import lila.user.User

final case class ApiAiConfig(
    variant: Variant,
    fenVariant: Option[Variant],
    clock: Option[ClockConfig],
    daysO: Option[Int],
    playerIndex: PlayerIndex,
    level: Int,
    fen: Option[FEN] = None
) extends Config
    with Positional {

  val strictFen = false

  val days = ~daysO
  // TODO: We are reusing the increment field for the Bronstein Delay and Simple Delay. This should probably be renamed
  val increment = clock.??(_.graceSeconds)
  val time      = clock.??(_.limit.roundSeconds / 60)
  val byoyomi = clock match {
    case Some(c: ByoyomiClock.Config) => c.byoyomi.roundSeconds
    case _                            => 0
  }

  val periods = clock match {
    case Some(c: ByoyomiClock.Config) => c.periodsTotal
    case _                            => 0
  }

  val timeMode =
    if (clock.isDefined) TimeMode.FischerClock
    else if (daysO.isDefined) TimeMode.Correspondence
    else TimeMode.Unlimited

  def game(user: Option[User]) =
    fenGame { stratGame =>
      val perfPicker = lila.game.PerfPicker.mainOrDefault(
        Speed(stratGame.clock.map(_.config)),
        stratGame.situation.board.variant,
        makeDaysPerTurn
      )
      Game
        .make(
          stratGame = stratGame,
          p1Player = creatorPlayerIndex.fold(
            Player.make(P1, user, perfPicker),
            Player.make(P1, level.some)
          ),
          p2Player = creatorPlayerIndex.fold(
            Player.make(P2, level.some),
            Player.make(P2, user, perfPicker)
          ),
          mode = Mode.Casual,
          source = if (stratGame.board.variant.fromPositionVariant) Source.Position else Source.Ai,
          daysPerTurn = makeDaysPerTurn,
          pgnImport = None
        )
        .sloppy
    } start

  def pov(user: Option[User]) = Pov(game(user), creatorPlayerIndex)

  def autoVariant =
    if (variant == Variant.Chess(Standard) && fen.exists(!_.initial))
      copy(variant = Variant.wrap(FromPosition))
    else this
}

object ApiAiConfig extends BaseConfig {

  // lazy val clockLimitSeconds: Set[Int] = Set(0, 15, 30, 45, 60, 90) ++ (2 to 180).view.map(60 *).toSet

  def from(
      l: Int,
      v: Option[String],
      fcl: Option[Clock.Config],
      sdc: Option[Clock.SimpleDelayConfig],
      bdc: Option[Clock.BronsteinConfig],
      bcl: Option[ByoyomiClock.Config],
      d: Option[Int],
      c: Option[String],
      pos: Option[String]
  ) = {
    val variant = Variant.orDefault(~v)
    new ApiAiConfig(
      variant = variant,
      fenVariant = none,
      clock = bcl.orElse(sdc).orElse(bdc).orElse(fcl),
      daysO = d,
      playerIndex = PlayerIndex.orDefault(~c),
      level = l,
      fen = pos.map(f => FEN.apply(variant.gameLogic, f))
    ).autoVariant
  }
}
