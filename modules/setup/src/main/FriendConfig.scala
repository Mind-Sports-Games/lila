package lila.setup

import strategygames.{ GameFamily, GameLogic, Mode, Speed }
import strategygames.variant.Variant
import strategygames.format.FEN
import lila.lobby.PlayerIndex
import lila.rating.PerfType
import lila.game.PerfPicker

case class FriendConfig(
    variant: Variant,
    fenVariant: Option[Variant],
    timeMode: TimeMode,
    time: Double,
    increment: Int,
    byoyomi: Int,
    periods: Int,
    goHandicap: Int,
    goKomi: Int,
    days: Int,
    mode: Mode,
    playerIndex: PlayerIndex,
    fen: Option[FEN] = None,
    multiMatch: Boolean = false
) extends HumanConfig
    with Positional {

  val strictFen = false

  def >> = (
    s"{$variant.gameFamily.id}_{$variant.id}",
    fenVariant.map(_.id),
    timeMode.id,
    time,
    increment,
    byoyomi,
    periods,
    goHandicap,
    goKomi,
    days,
    mode.id.some,
    playerIndex.name,
    fen.map(_.value),
    multiMatch
  ).some

  def isPersistent = timeMode == TimeMode.Unlimited || timeMode == TimeMode.Correspondence

  def perfType: Option[PerfType] = PerfPicker.perfType(Speed(makeClock), variant, makeDaysPerTurn)

  def actualFen: Option[FEN] = fen.fold {
    if (variant.gameFamily == GameFamily.Go())
      Some(
        FEN(
          variant.gameLogic,
          variant.toGo.fenFromSetupConfig(goHandicap, goKomi).value
        )
      )
    else None
  }(Some(_))

  def validKomi = variant.gameFamily != GameFamily.Go() || (variant.gameFamily == GameFamily.Go() &&
    goKomi.abs <= (variant.toGo.boardSize.width * variant.toGo.boardSize.width * 10))
}

object FriendConfig extends BaseHumanConfig {

  def from(
      v: String,
      v2: Option[Int],
      tm: Int,
      t: Double,
      i: Int,
      b: Int,
      p: Int,
      gh: Int,
      gk: Int,
      d: Int,
      m: Option[Int],
      c: String,
      fen: Option[String],
      mm: Boolean
  ) = {
    val gameLogic = GameFamily(v.split("_")(0).toInt).gameLogic
    val variantId = v.split("_")(1).toInt
    new FriendConfig(
      variant = Variant(gameLogic, variantId) err s"Invalid game variant $v",
      fenVariant = gameLogic match {
        case GameLogic.Draughts() =>
          v2.flatMap(strategygames.draughts.variant.Variant.apply).map(Variant.Draughts)
        case GameLogic.Go() => v2.flatMap(strategygames.go.variant.Variant.apply).map(Variant.Go)
        case _              => none
      },
      timeMode = TimeMode(tm) err s"Invalid time mode $tm",
      time = t,
      increment = i,
      byoyomi = b,
      periods = p,
      goHandicap = gh,
      goKomi = gk,
      days = d,
      mode = m.fold(Mode.default)(Mode.orDefault),
      playerIndex = PlayerIndex(c) err "Invalid playerIndex " + c,
      fen = fen.map(f => FEN.apply(gameLogic, f)),
      multiMatch = mm
    )
  }

  def default(l: Int) = FriendConfig(
    variant = defaultVariants(l),
    fenVariant = none,
    timeMode = TimeMode.Unlimited,
    time = 5d,
    increment = 8,
    byoyomi = 10,
    periods = 1,
    goHandicap = 0,
    goKomi = 75, // value is *10 to provide int
    days = 2,
    mode = Mode.default,
    playerIndex = PlayerIndex.default
  )

  import lila.db.BSON
  import lila.db.dsl._

  implicit private[setup] val friendConfigBSONHandler = new BSON[FriendConfig] {

    def reads(r: BSON.Reader): FriendConfig =
      FriendConfig(
        variant = Variant.orDefault(GameLogic(r intD "l"), r int "v"),
        fenVariant = r intD "l" match {
          case 0 => none
          case 1 => (r intO "v2").flatMap(strategygames.draughts.variant.Variant.apply).map(Variant.Draughts)
          case 5 => (r intO "v2").flatMap(strategygames.go.variant.Variant.apply).map(Variant.Go)
        },
        timeMode = TimeMode orDefault (r int "tm"),
        time = r double "t",
        increment = r int "i",
        byoyomi = r intD "b",
        periods = r intD "p",
        goHandicap = r intD "gh",
        goKomi = r intD "gk",
        days = r int "d",
        mode = Mode orDefault (r int "m"),
        playerIndex = PlayerIndex.P1,
        fen = r.getO[FEN]("f") filter (_.value.nonEmpty),
        multiMatch = ~r.boolO("mm")
      )

    def writes(w: BSON.Writer, o: FriendConfig) =
      $doc(
        "l"  -> o.variant.gameLogic.id,
        "v"  -> o.variant.id,
        "v2" -> o.fenVariant.map(_.id),
        "tm" -> o.timeMode.id,
        "t"  -> o.time,
        "i"  -> o.increment,
        "b"  -> o.byoyomi,
        "p"  -> o.periods,
        "gh" -> o.goHandicap,
        "gk" -> o.goKomi,
        "d"  -> o.days,
        "m"  -> o.mode.id,
        "f"  -> o.fen,
        "mm" -> o.multiMatch
      )
  }
}
