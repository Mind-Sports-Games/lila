package lila.setup

import strategygames.{ GameFamily, GameLogic, Mode, Speed }
import strategygames.variant.Variant
import lila.lobby.PlayerIndex
import lila.lobby.{ Hook, Seek }
import lila.rating.RatingRange
import lila.user.User

case class HookConfig(
    variant: Variant,
    timeMode: TimeMode,
    time: Double,
    increment: Int,
    days: Int,
    mode: Mode,
    playerIndex: PlayerIndex,
    ratingRange: RatingRange
) extends HumanConfig {

  def withinLimits(user: Option[User]): HookConfig =
    (for {
      pt <- perfType
      me <- user
    } yield copy(
      ratingRange = ratingRange.withinLimits(
        rating = me.perfs(pt).intRating,
        delta = 400,
        multipleOf = 50
      )
    )) | this

  private def perfType = lila.game.PerfPicker.perfType(makeSpeed, variant, makeDaysPerTurn)

  def makeSpeed = Speed(makeClock)

  def fixPlayerIndex =
    copy(
      playerIndex =
        if (
          mode == Mode.Rated &&
          lila.game.Game.variantsWhereP1IsBetter(variant) &&
          playerIndex != PlayerIndex.Random
        ) PlayerIndex.Random
        else playerIndex
    )

  def >> =
    (s"{$variant.gameLogic.id}_{$variant.id}", timeMode.id, time, increment, days, mode.id.some, ratingRange.toString.some, playerIndex.name).some

  def withTimeModeString(tc: Option[String]) =
    tc match {
      case Some("realTime")       => copy(timeMode = TimeMode.RealTime)
      case Some("correspondence") => copy(timeMode = TimeMode.Correspondence)
      case Some("unlimited")      => copy(timeMode = TimeMode.Unlimited)
      case _                      => this
    }

  def hook(
      sri: lila.socket.Socket.Sri,
      user: Option[User],
      sid: Option[String],
      blocking: Set[String]
  ): Either[Hook, Option[Seek]] =
    timeMode match {
      case TimeMode.RealTime =>
        val clock = justMakeClock
        Left(
          Hook.make(
            sri = sri,
            variant = variant,
            clock = clock,
            mode = if (lila.game.Game.allowRated(variant, clock.some)) mode else Mode.Casual,
            playerIndex = playerIndex.name,
            user = user,
            blocking = blocking,
            sid = sid,
            ratingRange = ratingRange
          )
        )
      case _ =>
        Right(user map { u =>
          Seek.make(
            variant = variant,
            daysPerTurn = makeDaysPerTurn,
            mode = mode,
            playerIndex = playerIndex.name,
            user = u,
            blocking = blocking,
            ratingRange = ratingRange
          )
        })
    }

  def noRatedUnlimited = mode.casual || hasClock || makeDaysPerTurn.isDefined

  def updateFrom(game: lila.game.Game) =
    copy(
      variant = game.variant,
      timeMode = TimeMode ofGame game,
      time = game.clock.map(_.limitInMinutes) | time,
      increment = game.clock.map(_.incrementSeconds) | increment,
      days = game.daysPerTurn | days,
      mode = game.mode
    )

  def withRatingRange(str: Option[String]) = copy(ratingRange = RatingRange orDefault str)
}

object HookConfig extends BaseHumanConfig {

  def from(v: String, tm: Int, t: Double, i: Int, d: Int, m: Option[Int], e: Option[String], c: String) = {
    val realMode = m.fold(Mode.default)(Mode.orDefault)
    val gameFamily = GameFamily(v.split("_")(0).toInt)
    val variantId  = v.split("_")(1).toInt
    new HookConfig(
      variant = Variant(gameFamily.gameLogic, variantId) err s"Invalid game variant $v",
      timeMode = TimeMode(tm) err s"Invalid time mode $tm",
      time = t,
      increment = i,
      days = d,
      mode = realMode,
      ratingRange = e.fold(RatingRange.default)(RatingRange.orDefault),
      playerIndex = PlayerIndex(c) err s"Invalid playerIndex $c"
    )
  }

  def default(auth: Boolean): HookConfig = default.copy(mode = Mode(auth))

  private val default = HookConfig(
    variant = variantDefaultStrat,
    timeMode = TimeMode.RealTime,
    time = 5d,
    increment = 3,
    days = 2,
    mode = Mode.default,
    ratingRange = RatingRange.default,
    playerIndex = PlayerIndex.default
  )

  import lila.db.BSON
  import lila.db.dsl._

  implicit private[setup] val hookConfigBSONHandler = new BSON[HookConfig] {

    def reads(r: BSON.Reader): HookConfig =
      HookConfig(
        variant = Variant.orDefault(GameLogic(r intD "l"), r int "v"),
        timeMode = TimeMode orDefault (r int "tm"),
        time = r double "t",
        increment = r int "i",
        days = r int "d",
        mode = Mode orDefault (r int "m"),
        playerIndex = PlayerIndex.Random,
        ratingRange = r strO "e" flatMap RatingRange.apply getOrElse RatingRange.default
      )

    def writes(w: BSON.Writer, o: HookConfig) =
      $doc(
        "l"  -> o.variant.gameLogic.id,
        "v"  -> o.variant.id,
        "tm" -> o.timeMode.id,
        "t"  -> o.time,
        "i"  -> o.increment,
        "d"  -> o.days,
        "m"  -> o.mode.id,
        "e"  -> o.ratingRange.toString
      )
  }
}
