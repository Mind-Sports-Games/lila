package lila.setup

import strategygames.{ GameFamily, GameLogic, Mode, Speed }
import strategygames.variant.Variant
import strategygames.format.FEN
import lila.lobby.PlayerIndex
import lila.rating.PerfType
import lila.rating.RatingRange
import lila.game.PerfPicker

case class GameConfig(
    variant: Variant,
    fenVariant: Option[Variant],
    timeMode: TimeMode,
    time: Double,
    increment: Int,
    byoyomi: Int,
    periods: Int,
    goHandicap: Int,
    goKomi: Int,
    backgammonPoints: Option[Int],
    days: Int,
    mode: Mode,
    playerIndex: PlayerIndex,
    fen: Option[FEN] = None,
    multiMatch: Boolean = false,
    opponent: String = "friend"
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
    backgammonPoints,
    days,
    mode.id.some,
    playerIndex.name,
    fen.map(_.value),
    multiMatch,
    opponent
  ).some

  def toHookConfig = HookConfig(
    variant = variant,
    timeMode = timeMode,
    time = time,
    increment = increment,
    byoyomi = byoyomi,
    periods = periods,
    days = days,
    mode = mode,
    playerIndex = playerIndex,
    ratingRange = RatingRange.default
  )

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
    else if (variant.gameFamily == GameFamily.Backgammon())
      Some(
        FEN(
          variant.gameLogic,
          variant.toBackgammon.fenFromSetupConfig(backgammonPoints.getOrElse(1) != 1).value
        )
      )
    else None
  }(Some(_))

  def validKomi =
    variant.gameFamily != GameFamily.Go() || (variant.gameFamily == GameFamily.Go() &&
      goKomi.abs <= (variant.toGo.boardSize.width * variant.toGo.boardSize.width * 10))

  def validPoints =
    variant.gameFamily != GameFamily.Backgammon() || (
      variant.gameFamily == GameFamily.Backgammon() &&
        backgammonPoints.getOrElse(1) % 2 == 1
    )

}

object GameConfig extends BaseHumanConfig {

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
      bp: Option[Int],
      d: Int,
      m: Option[Int],
      c: String,
      fen: Option[String],
      mm: Boolean,
      o: String
  ) = {
    val gameLogic = GameFamily(v.split("_")(0).toInt).gameLogic
    val variantId = v.split("_")(1).toInt
    new GameConfig(
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
      backgammonPoints = bp,
      days = d,
      mode = m.fold(Mode.default)(Mode.orDefault),
      playerIndex = PlayerIndex(c) err "Invalid playerIndex " + c,
      fen = fen.map(f => FEN.apply(gameLogic, f)),
      multiMatch = mm,
      opponent = o
    )
  }

  def default(l: Int) = GameConfig(
    variant = defaultVariants(l),
    fenVariant = none,
    timeMode = TimeMode.Unlimited,
    time = 5d,
    increment = 8,
    byoyomi = 10,
    periods = 1,
    goHandicap = 0,
    goKomi = 75, // value is *10 to provide int
    backgammonPoints = none,
    days = 2,
    mode = Mode.default,
    playerIndex = PlayerIndex.default,
    opponent = "friend"
  )

  def opponentTypes: List[String] = List("friend", "bot", "lobby")

  import lila.db.BSON
  import lila.db.dsl._

  implicit private[setup] val gameConfigBSONHandler: BSON[GameConfig] = new BSON[GameConfig] {

    def reads(r: BSON.Reader): GameConfig =
      GameConfig(
        variant = Variant.orDefault(GameLogic(r intD "l"), r int "v"),
        fenVariant = r intD "l" match {
          case 0 => none
          case 1 => (r intO "v2").flatMap(strategygames.draughts.variant.Variant.apply).map(Variant.Draughts)
          case 5 => (r intO "v2").flatMap(strategygames.go.variant.Variant.apply).map(Variant.Go)
          case 6 =>
            (r intO "v2").flatMap(strategygames.backgammon.variant.Variant.apply).map(Variant.Backgammon)
        },
        timeMode = TimeMode orDefault (r int "tm"),
        time = r double "t",
        increment = r int "i",
        byoyomi = r intD "b",
        periods = r intD "p",
        goHandicap = r intD "gh",
        goKomi = r intD "gk",
        backgammonPoints = r intO "bp",
        days = r int "d",
        mode = Mode orDefault (r int "m"),
        playerIndex = PlayerIndex.P1,
        fen = r.getO[FEN]("f") filter (_.value.nonEmpty),
        multiMatch = ~r.boolO("mm"),
        opponent = "friend"
      )

    def writes(w: BSON.Writer, o: GameConfig) =
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
        "bp" -> o.backgammonPoints,
        "d"  -> o.days,
        "m"  -> o.mode.id,
        "f"  -> o.fen,
        "mm" -> o.multiMatch
      )
  }
}
