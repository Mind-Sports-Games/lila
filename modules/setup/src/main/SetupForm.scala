package lila.setup

import strategygames.format.FEN
import strategygames.GameLib
import strategygames.variant.Variant
import strategygames.Centis
import play.api.data._
import play.api.data.Forms._

import lila.rating.RatingRange
import lila.user.{ User, UserContext }

object SetupForm {

  import Mappings._

  val filter = Form(single("local" -> text))

  def aiFilled(fen: Option[FEN]): Form[AiConfig] =
    ai fill fen.foldLeft(AiConfig.default) { case (config, f) =>
      config.copy(fen = f.some, variant = Variant.wrap(strategygames.chess.variant.FromPosition))
    }

  lazy val ai = Form(
    mapping(
      "variant"   -> aiVariants,
      "timeMode"  -> timeMode,
      "time"      -> time,
      "increment" -> increment,
      "days"      -> days,
      "level"     -> level,
      "color"     -> color,
      "fen"       -> fenField
    )(AiConfig.from)(_.>>)
      .verifying("invalidFen", _.validFen)
      .verifying("Can't play that time control from a position", _.timeControlFromPosition)
  )

  def friendFilled(fen: Option[FEN])(implicit ctx: UserContext): Form[FriendConfig] =
    friend(ctx) fill fen.foldLeft(FriendConfig.default) { case (config, f) =>
      config.copy(fen = f.some, variant = Variant.wrap(strategygames.chess.variant.FromPosition))
    }

  def friend(ctx: UserContext) =
    Form(
      mapping(
        "variant"   -> variantWithFenAndVariants,
        "timeMode"  -> timeMode,
        "time"      -> time,
        "increment" -> increment,
        "days"      -> days,
        "mode"      -> mode(withRated = ctx.isAuth),
        "color"     -> color,
        "fen"       -> fenField
      )(FriendConfig.from)(_.>>)
        .verifying("Invalid clock", _.validClock)
        .verifying("Invalid speed", _.validSpeed(ctx.me.exists(_.isBot)))
        .verifying("invalidFen", _.validFen)
    )

  def hookFilled(timeModeString: Option[String])(implicit ctx: UserContext): Form[HookConfig] =
    hook fill HookConfig.default(ctx.isAuth).withTimeModeString(timeModeString)

  def hook(implicit ctx: UserContext) =
    Form(
      mapping(
        "lib"         -> gameLibs,
        "variant"     -> variantWithVariants,
        "timeMode"    -> timeMode,
        "time"        -> time,
        "increment"   -> increment,
        "days"        -> days,
        "mode"        -> mode(ctx.isAuth),
        "ratingRange" -> optional(ratingRange),
        "color"       -> color
      )(HookConfig.from)(_.>>)
        .verifying("Invalid clock", _.validClock)
        .verifying("Can't create rated unlimited in lobby", _.noRatedUnlimited)
    )

  lazy val boardApiHook = Form(
    mapping(
      "time"        -> time,
      "increment"   -> increment,
      "variant"     -> optional(boardApiVariantKeys),
      "rated"       -> optional(boolean),
      "color"       -> optional(color),
      "ratingRange" -> optional(ratingRange)
    )((t, i, v, r, c, g) =>
      HookConfig(
        variant = v.flatMap(v => Variant.apply(GameLib.Chess(), v)) | Variant.default(GameLib.Chess()),
        timeMode = TimeMode.RealTime,
        time = t,
        increment = i,
        days = 1,
        mode = strategygames.Mode(~r),
        color = lila.lobby.Color.orDefault(c),
        ratingRange = g.fold(RatingRange.default)(RatingRange.orDefault)
      )
    )(_ => none)
      .verifying("Invalid clock", _.validClock)
      .verifying(
        "Invalid time control",
        hook => hook.makeClock ?? lila.game.Game.isBoardCompatible
      )
  )

  object api {

    lazy val clockMapping =
      mapping(
        "limit"     -> number.verifying(ApiConfig.clockLimitSeconds.contains _),
        "increment" -> increment
      )(strategygames.Clock.Config.apply)(strategygames.Clock.Config.unapply)
        .verifying("Invalid clock", c => c.estimateTotalTime > Centis(0))

    lazy val clock = "clock" -> optional(clockMapping)

    lazy val variant =
      "variant" -> optional(text.verifying(Variant.byKey(GameLib.Chess()).contains _))

    lazy val message = optional(
      nonEmptyText.verifying(
        "The message must contain {game}, which will be replaced with the game URL.",
        _ contains "{game}"
      )
    )

    def user(from: User) =
      Form(challengeMapping.verifying("Invalid speed", _ validSpeed from.isBot))

    def admin = Form(challengeMapping)

    private val challengeMapping =
      mapping(
        variant,
        clock,
        "days"          -> optional(days),
        "rated"         -> boolean,
        "color"         -> optional(color),
        "fen"           -> fenField,
        "acceptByToken" -> optional(nonEmptyText),
        "message"       -> message
      )(ApiConfig.from)(_ => none)
        .verifying("invalidFen", _.validFen)
        .verifying("can't be rated", _.validRated)

    lazy val ai = Form(
      mapping(
        "level" -> level,
        variant,
        clock,
        "days"  -> optional(days),
        "color" -> optional(color),
        "fen"   -> fenField
      )(ApiAiConfig.from)(_ => none).verifying("invalidFen", _.validFen)
    )

    lazy val open = Form(
      mapping(
        "name" -> optional(lila.common.Form.cleanNonEmptyText(maxLength = 200)),
        variant,
        clock,
        "rated" -> boolean,
        "fen"   -> fenField
      )(OpenConfig.from)(_ => none)
        .verifying("invalidFen", _.validFen)
        .verifying("rated without a clock", c => c.clock.isDefined || !c.rated)
    )
  }
}
