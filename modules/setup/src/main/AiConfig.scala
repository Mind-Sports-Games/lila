package lila.setup

import strategygames.{ GameFamily, GameLogic, Mode, P1, P2, Speed }
import strategygames.format.FEN
import strategygames.variant.Variant
import lila.game.{ Game, Player, Pov, Source }
import lila.lobby.PlayerIndex
import lila.user.User

case class AiConfig(
    variant: Variant,
    fenVariant: Option[Variant],
    timeMode: TimeMode,
    time: Double,
    increment: Int,
    byoyomi: Int,
    periods: Int,
    days: Int,
    level: Int,
    playerIndex: PlayerIndex,
    fen: Option[FEN] = None
) extends Config
    with Positional {

  val strictFen = true

  def >> = (
    s"{$variant.gameFamily.id}_{$variant.id}",
    timeMode.id,
    time,
    increment,
    byoyomi,
    periods,
    days,
    level,
    playerIndex.name,
    fen.map(_.value)
  ).some

  def game(user: Option[User]) =
    fenGame { chessGame =>
      val perfPicker = lila.game.PerfPicker.mainOrDefault(
        Speed(chessGame.clock.map(_.config)),
        chessGame.situation.board.variant,
        makeDaysPerTurn
      )
      Game
        .make(
          chess = chessGame,
          p1Player = creatorPlayerIndex.fold(
            Player.make(P1, user, perfPicker),
            Player.make(P1, level.some)
          ),
          p2Player = creatorPlayerIndex.fold(
            Player.make(P2, level.some),
            Player.make(P2, user, perfPicker)
          ),
          mode = Mode.Casual,
          source = if (chessGame.board.variant.fromPosition) Source.Position else Source.Ai,
          daysPerTurn = makeDaysPerTurn,
          pgnImport = None
        )
        .sloppy
    } start

  def pov(user: Option[User]) = Pov(game(user), creatorPlayerIndex)

  def timeControlFromPosition = variant != strategygames.chess.variant.FromPosition || time >= 1
}

object AiConfig extends BaseConfig {

  def from(
      v: String,
      tm: Int,
      t: Double,
      i: Int,
      b: Int,
      p: Int,
      d: Int,
      level: Int,
      c: String,
      fen: Option[String]
  ) = {
    val gameLogic = GameFamily(v.split("_")(0).toInt).gameLogic
    val variantId = v.split("_")(1).toInt
    new AiConfig(
      variant = Variant(gameLogic, variantId) err s"Invalid game variant $v",
      fenVariant = none,
      timeMode = TimeMode(tm) err s"Invalid time mode $tm",
      time = t,
      increment = i,
      byoyomi = b,
      periods = p,
      days = d,
      level = level,
      playerIndex = PlayerIndex(c) err "Invalid playerIndex " + c,
      fen = fen.map(f => FEN.apply(gameLogic, f))
    )
  }

  def default = AiConfig(
    variant = defaultVariants(GameLogic.Chess().id),
    fenVariant = none,
    timeMode = TimeMode.Unlimited,
    time = 5d,
    increment = 8,
    byoyomi = 10,
    periods = 1,
    days = 2,
    level = 1,
    playerIndex = PlayerIndex.default
  )

  val levels = (1 to 8).toList

  val levelChoices = levels map { l =>
    (l.toString, l.toString, none)
  }

  import lila.db.BSON
  import lila.db.dsl._

  implicit private[setup] val aiConfigBSONHandler = new BSON[AiConfig] {

    def reads(r: BSON.Reader): AiConfig =
      AiConfig(
        variant = Variant.orDefault(GameLogic(r intD "l"), r int "v"),
        fenVariant = none,
        timeMode = TimeMode orDefault (r int "tm"),
        time = r double "t",
        increment = r int "i",
        byoyomi = r intD "b",
        periods = r intD "p",
        days = r int "d",
        level = r int "l",
        playerIndex = PlayerIndex.P1,
        fen = r.getO[FEN]("f").filter(_.value.nonEmpty)
      )

    def writes(w: BSON.Writer, o: AiConfig) =
      $doc(
        "v"  -> o.variant.id,
        "tm" -> o.timeMode.id,
        "t"  -> o.time,
        "i"  -> o.increment,
        "b"  -> o.byoyomi,
        "p"  -> o.periods,
        "d"  -> o.days,
        "l"  -> o.level,
        "f"  -> o.fen
      )
  }
}
