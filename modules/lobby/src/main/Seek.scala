package lila.lobby

import strategygames.{ GameLogic, Mode, Speed }
import org.joda.time.DateTime
import play.api.libs.json._
import play.api.i18n.Lang

import lila.game.PerfPicker
import lila.rating.RatingRange
import lila.user.User
import lila.i18n.VariantKeys

// correspondence chess, persistent
case class Seek(
    _id: String,
    gameLogic: GameLogic,
    variant: Int,
    daysPerTurn: Option[Int],
    mode: Int,
    playerIndex: String,
    user: LobbyUser,
    ratingRange: String,
    createdAt: DateTime
) {

  def id = _id

  val realPlayerIndex = PlayerIndex orDefault playerIndex

  val realVariant = strategygames.variant.Variant.orDefault(gameLogic, variant)

  val realMode = Mode orDefault mode

  def compatibleWith(h: Seek) =
    user.id != h.user.id &&
      compatibilityProperties == h.compatibilityProperties &&
      (realPlayerIndex compatibleWith h.realPlayerIndex) &&
      ratingRangeCompatibleWith(h) && h.ratingRangeCompatibleWith(this)

  private def ratingRangeCompatibleWith(s: Seek) =
    realRatingRange.fold(true) { range =>
      s.rating ?? range.contains
    }

  private def compatibilityProperties =
    (gameLogic, variant, mode, daysPerTurn)

  lazy val realRatingRange: Option[RatingRange] = RatingRange noneIfDefault ratingRange

  def perf = perfType map user.perfAt

  def rating = perf.map(_.rating)

  val message =
    s"Play ${daysPerTurn.fold("unlimited")(_ => "correspondence")} **${VariantKeys
      .variantName(realVariant)}** with @${user.username}${daysPerTurn.fold("")(d => s" (${d}d)")}"

  def render(implicit lang: Lang): JsObject =
    Json
      .obj(
        "id"       -> _id,
        "username" -> user.username,
        "rating"   -> rating,
        "variant" -> Json.obj(
          "key"   -> realVariant.key,
          "short" -> VariantKeys.variantShortName(realVariant),
          "name"  -> VariantKeys.variantName(realVariant)
        ),
        "mode"        -> realMode.id,
        "days"        -> daysPerTurn,
        "playerIndex" -> strategygames.Player.fromName(playerIndex).??(_.name),
        "perf" -> Json.obj(
          "icon" -> perfType.map(_.iconChar.toString),
          "name" -> perfType.map(_.trans)
        )
      )
      .add("provisional" -> perf.map(_.provisional).filter(identity))

  lazy val perfType = PerfPicker.perfType(Speed.Correspondence, realVariant, daysPerTurn)
}

object Seek {

  val idSize = 8

  def make(
      variant: strategygames.variant.Variant,
      daysPerTurn: Option[Int],
      mode: Mode,
      playerIndex: String,
      user: User,
      ratingRange: RatingRange,
      blocking: Set[String]
  ): Seek =
    new Seek(
      _id = lila.common.ThreadLocalRandom nextString idSize,
      gameLogic = variant.gameLogic,
      variant = variant.id,
      daysPerTurn = daysPerTurn,
      mode = mode.id,
      playerIndex = playerIndex,
      user = LobbyUser.make(user, blocking),
      ratingRange = ratingRange.toString,
      createdAt = DateTime.now
    )

  def renew(seek: Seek) =
    new Seek(
      _id = lila.common.ThreadLocalRandom nextString idSize,
      gameLogic = seek.gameLogic,
      variant = seek.variant,
      daysPerTurn = seek.daysPerTurn,
      mode = seek.mode,
      playerIndex = seek.playerIndex,
      user = seek.user,
      ratingRange = seek.ratingRange,
      createdAt = DateTime.now
    )

  import reactivemongo.api.bson._
  import lila.db.BSON.BSONJodaDateTimeHandler
  implicit val lobbyPerfBSONHandler: BSONHandler[LobbyPerf] =
    BSONIntegerHandler.as[LobbyPerf](
      b => LobbyPerf(b.abs, b < 0),
      x => x.rating * (if (x.provisional) -1 else 1)
    )

  // TODO: this should probably go somewhere else.
  implicit val gameLogicBSONHandler: BSONHandler[GameLogic] =
    BSONIntegerHandler.as[GameLogic](
      b => GameLogic(b.abs),
      lib => lib.id
    )
  implicit private[lobby] val lobbyUserBSONHandler: BSONDocumentHandler[LobbyUser] = Macros.handler[LobbyUser]
  implicit private[lobby] val seekBSONHandler: BSONDocumentHandler[Seek]           = Macros.handler[Seek]
}
