package lila.lobby

import strategygames.{ ClockConfig, GameLogic, Mode, Speed }
import strategygames.variant.Variant
import org.joda.time.DateTime
import play.api.i18n.Lang
import play.api.libs.json._

import lila.game.PerfPicker
import lila.rating.RatingRange
import lila.socket.Socket.Sri
import lila.user.User
import lila.i18n.VariantKeys

// realtime chess, volatile
case class Hook(
    id: String,
    sri: Sri,            // owner socket sri
    sid: Option[String], // owner cookie (used to prevent multiple hooks)
    lib: GameLogic,
    variant: Int,
    clock: ClockConfig,
    mode: Int,
    playerIndex: String,
    user: Option[LobbyUser],
    ratingRange: String,
    createdAt: DateTime,
    boardApi: Boolean
) {

  val realPlayerIndex = PlayerIndex orDefault playerIndex

  val realVariant = Variant.orDefault(lib, variant)

  val realMode = Mode orDefault mode

  val isAuth = user.nonEmpty

  def compatibleWith(h: Hook) =
    isAuth == h.isAuth &&
      mode == h.mode &&
      lib == h.lib &&
      variant == h.variant &&
      clock == h.clock &&
      (realPlayerIndex compatibleWith h.realPlayerIndex) &&
      ratingRangeCompatibleWith(h) && h.ratingRangeCompatibleWith(this) &&
      (userId.isEmpty || userId != h.userId)

  private def ratingRangeCompatibleWith(h: Hook) =
    realRatingRange.fold(true) { range =>
      h.rating ?? range.contains
    }

  lazy val realRatingRange: Option[RatingRange] = isAuth ?? {
    RatingRange noneIfDefault ratingRange
  }

  def userId   = user.map(_.id)
  def username = user.fold(User.anonymous)(_.username)
  def lame     = user ?? (_.lame)

  lazy val perfType = PerfPicker.perfType(speed, realVariant, none)

  lazy val perf: Option[LobbyPerf] = for { u <- user; pt <- perfType } yield u perfAt pt
  def rating: Option[Int]          = perf.map(_.rating)

  val message = s"Play live **${VariantKeys
    .variantName(realVariant)}** with ${user.fold(User.anonymous)(u => "@" + u.username)} (${clock.show})"

  def render(implicit lang: Lang): JsObject =
    Json
      .obj(
        "id"    -> id,
        "sri"   -> sri,
        "clock" -> clock.show,
        "t"     -> clock.estimateTotalSeconds,
        "s"     -> speed.id,
        "i"     -> (if (clock.graceSeconds > 0) 1 else 0)
      )
      .add("prov" -> perf.map(_.provisional).filter(identity))
      .add("u" -> user.map(_.username))
      .add("rating" -> rating)
      .add("variant" -> realVariant.exotic.option(realVariant.key))
      .add("ra" -> realMode.rated.option(1))
      .add("c" -> strategygames.Player.fromName(playerIndex).map(_.name))
      .add("variantC" -> strategygames.Player.fromName(playerIndex).map(realVariant.playerColors))
      .add("perf" -> perfType.map(_.trans))

  def randomPlayerIndex = playerIndex == "random"

  def compatibleWithPools(poolVariant: Variant) =
    realMode.rated && lila.pool.PoolList.variantSet
      .contains(realVariant) && realVariant == poolVariant && randomPlayerIndex &&
      lila.pool.PoolList.clockStringSet.contains(clock.show)

  def compatibleWithPool(poolClock: strategygames.ClockConfig, poolVariant: Variant) =
    compatibleWithPools(poolVariant) && clock == poolClock

  def toPool =
    lila.pool.HookThieve.PoolHook(
      hookId = id,
      member = lila.pool.PoolMember(
        userId = user.??(_.id),
        sri = sri,
        rating = rating | lila.rating.Glicko.default.intRating,
        ratingRange = realRatingRange,
        lame = user.??(_.lame),
        blocking = lila.pool.PoolMember.BlockedUsers(user.??(_.blocking)),
        rageSitCounter = 0
      )
    )

  private lazy val speed = Speed(clock)
}

object Hook {

  val idSize = 8

  def make(
      sri: Sri,
      variant: strategygames.variant.Variant,
      clock: ClockConfig,
      mode: Mode,
      playerIndex: String,
      user: Option[User],
      sid: Option[String],
      ratingRange: RatingRange,
      blocking: Set[String],
      boardApi: Boolean = false
  ): Hook =
    new Hook(
      id = lila.common.ThreadLocalRandom nextString idSize,
      sri = sri,
      lib = variant.gameLogic,
      variant = variant.id,
      clock = clock,
      mode = mode.id,
      playerIndex = playerIndex,
      user = user map { LobbyUser.make(_, blocking) },
      sid = sid,
      ratingRange = ratingRange.toString,
      createdAt = DateTime.now,
      boardApi = boardApi
    )
}
