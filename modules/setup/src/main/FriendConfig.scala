package lila.setup

import strategygames.{ GameLogic, Mode }
import strategygames.variant.Variant
import strategygames.format.FEN
import lila.lobby.Color
import lila.rating.PerfType
import lila.game.PerfPicker

case class FriendConfig(
    variant: Variant,
    fenVariant: Option[Variant],
    timeMode: TimeMode,
    time: Double,
    increment: Int,
    days: Int,
    mode: Mode,
    color: Color,
    fen: Option[FEN] = None,
    microMatch: Boolean = false
) extends HumanConfig
    with Positional {

  val strictFen = false

  def >> = (variant.gameLogic.id, variant.id, variant.id, variant.id, fenVariant.map(_.id), timeMode.id, time, increment, days, mode.id.some, color.name, fen.map(_.value), microMatch).some

  def isPersistent = timeMode == TimeMode.Unlimited || timeMode == TimeMode.Correspondence

  def perfType: Option[PerfType] = PerfPicker.perfType(strategygames.Speed(makeClock), variant, makeDaysPerTurn)
}

object FriendConfig extends BaseHumanConfig {

  def from(l: Int, cv: Int, dv: Int, lv: Int, v2: Option[Int], tm: Int, t: Double, i: Int, d: Int, m: Option[Int], c: String, fen: Option[String], mm: Boolean) =
    new FriendConfig(
      variant = l match {
        case 0 => Variant.wrap(
          strategygames.chess.variant.Variant(cv) err "Invalid game variant " + cv
        )
        case 1 => Variant.wrap(
          strategygames.draughts.variant.Variant(dv) err "Invalid game variant " + dv
        )
        case 2 => Variant.wrap(
          strategygames.chess.variant.Variant(lv) err "Invalid game variant " + lv
        )
      },
      fenVariant = l match {
        case 0 | 2 => none
        case 1     => v2.flatMap(strategygames.draughts.variant.Variant.apply).map(Variant.Draughts)
      },
      timeMode = TimeMode(tm) err s"Invalid time mode $tm",
      time = t,
      increment = i,
      days = d,
      mode = m.fold(Mode.default)(Mode.orDefault),
      color = Color(c) err "Invalid color " + c,
      fen = fen.map(f => FEN.apply(GameLogic(l), f)),
      microMatch = mm
    )

  def default(l: Int) = FriendConfig(
    variant = l match {
      case 0 => Variant.Chess(chessVariantDefault)
      case 1 => Variant.Draughts(draughtsVariantDefault)
      case 2 => Variant.Chess(loaVariantDefault)
    },
    fenVariant = none,
    timeMode = TimeMode.Unlimited,
    time = 5d,
    increment = 8,
    days = 2,
    mode = Mode.default,
    color = Color.default
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
        },
        timeMode = TimeMode orDefault (r int "tm"),
        time = r double "t",
        increment = r int "i",
        days = r int "d",
        mode = Mode orDefault (r int "m"),
        color = Color.White,
        fen = r.getO[FEN]("f") filter (_.value.nonEmpty),
        microMatch = ~r.boolO("mm")
      )

    def writes(w: BSON.Writer, o: FriendConfig) =
      $doc(
        "l"  -> o.variant.gameLogic.id,
        "v"  -> o.variant.id,
        "v2" -> o.fenVariant.map(_.id),
        "tm" -> o.timeMode.id,
        "t"  -> o.time,
        "i"  -> o.increment,
        "d"  -> o.days,
        "m"  -> o.mode.id,
        "f"  -> o.fen,
        "mm" -> o.microMatch
      )
  }
}
