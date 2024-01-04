package lila.setup

import strategygames.format.FEN
import strategygames.{ GameFamily, GameLogic }
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
      "variant"     -> variant(Config.fishnetVariants),
      "timeMode"    -> timeMode,
      "time"        -> time,
      "increment"   -> increment,
      "byoyomi"     -> byoyomi,
      "periods"     -> periods,
      "days"        -> days,
      "level"       -> level,
      "playerIndex" -> playerIndex,
      "fen"         -> fenField
    )(AiConfig.from)(_.>>)
      .verifying("invalidFen", _.validFen)
      .verifying("Can't play that time control from a position", _.timeControlFromPosition)
  )

  def friendFilled(lib: GameLogic, fen: Option[FEN])(implicit ctx: UserContext): Form[FriendConfig] =
    friend(ctx) fill fen.foldLeft(FriendConfig.default(lib.id)) { case (config, f) =>
      config.copy(
        fen = f.some,
        variant = lib match {
          case GameLogic.Chess()    => Variant.wrap(strategygames.chess.variant.FromPosition)
          case GameLogic.Draughts() => Variant.wrap(strategygames.draughts.variant.FromPosition)
          case _                    => sys.error("No from position variant")
        }
      )
    }

  def friend(ctx: UserContext) =
    Form(
      mapping(
        "variant"     -> variant(Config.variantsWithFenAndVariants),
        "fenVariant"  -> optional(draughtsFenVariants),
        "timeMode"    -> timeMode,
        "time"        -> time,
        "increment"   -> increment,
        "byoyomi"     -> byoyomi,
        "periods"     -> periods,
        "goHandicap"  -> goHandicap,
        "goKomi"      -> goKomi,
        "days"        -> days,
        "mode"        -> mode(withRated = ctx.isAuth),
        "playerIndex" -> playerIndex,
        "fen"         -> fenField,
        "multiMatch"  -> boolean
      )(FriendConfig.from)(_.>>)
        .verifying("Invalid clock", _.validClock)
        .verifying("Invalid speed", _.validSpeed(ctx.me.exists(_.isBot)))
        .verifying("invalidFen", _.validFen)
    )

  def hookFilled(timeModeString: Option[String])(implicit ctx: UserContext): Form[HookConfig] =
    hook.fill(HookConfig.default(ctx.isAuth).withTimeModeString(timeModeString))

  def hook(implicit ctx: UserContext) =
    Form(
      mapping(
        "variant"     -> variant(Config.variantsWithVariants),
        "timeMode"    -> timeMode,
        "time"        -> time,
        "increment"   -> increment,
        "byoyomi"     -> byoyomi,
        "periods"     -> periods,
        "days"        -> days,
        "mode"        -> mode(ctx.isAuth),
        "ratingRange" -> optional(ratingRange),
        "playerIndex" -> playerIndex
      )(HookConfig.from)(_.>>)
        .verifying("Invalid clock", _.validClock)
        .verifying("Can't create rated unlimited in lobby", _.noRatedUnlimited)
    )

  lazy val boardApiHook = Form(
    mapping(
      "variant"     -> optional(boardApiVariantKeys),
      "time"        -> time,
      "increment"   -> increment,
      "byoyomi"     -> byoyomi,
      "periods"     -> periods,
      "rated"       -> optional(boolean),
      "playerIndex" -> optional(playerIndex),
      "ratingRange" -> optional(ratingRange)
    )((v, t, i, b, p, r, c, g) =>
      HookConfig(
        variant = v match {
          case Some(v) =>
            Variant.apply(GameFamily(v.split('_')(0).toInt).gameLogic, v.split('_')(1)) | Variant.default(
              GameFamily(v.split('_')(0).toInt).gameLogic
            )
          case None => Variant.default(GameLogic.Chess())
        },
        timeMode = TimeMode.FischerClock,
        time = t,
        increment = i,
        byoyomi = b,
        periods = p,
        days = 1,
        mode = strategygames.Mode(~r),
        playerIndex = lila.lobby.PlayerIndex.orDefault(c),
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

    // TODO: There has to be a way to reduce this code.
    lazy val fischerClockMapping =
      mapping(
        "limit"     -> number.verifying(ApiConfig.clockLimitSeconds.contains _),
        "increment" -> increment
      )(strategygames.Clock.Config.apply)(strategygames.Clock.Config.unapply)
        .verifying("Invalid clock", c => c.estimateTotalTime > Centis(0))

    lazy val fischerClock = "clock" -> optional(fischerClockMapping)

    lazy val bronsteinDelayClockMapping =
      mapping(
        "limit" -> number.verifying(ApiConfig.clockLimitSeconds.contains _),
        "delay" -> increment
      )(strategygames.Clock.BronsteinConfig.apply)(strategygames.Clock.BronsteinConfig.unapply)
        .verifying("Invalid clock", c => c.estimateTotalTime > Centis(0))
    lazy val bronsteinDelayClock = "clock" -> optional(bronsteinDelayClockMapping)

    lazy val simpleDelayMapping =
      mapping(
        "limit" -> number.verifying(ApiConfig.clockLimitSeconds.contains _),
        "delay" -> increment
      )(strategygames.Clock.SimpleDelayConfig.apply)(strategygames.Clock.SimpleDelayConfig.unapply)
        .verifying("Invalid clock", c => c.estimateTotalTime > Centis(0))
    lazy val simpleDelayClock = "clock" -> optional(simpleDelayMapping)

    lazy val byoyomiClockMapping =
      mapping(
        "limit"     -> number.verifying(ApiConfig.clockLimitSeconds.contains _),
        "increment" -> increment,
        "byoyomi"   -> byoyomi,
        "periods"   -> periods
      )(strategygames.ByoyomiClock.Config.apply)(strategygames.ByoyomiClock.Config.unapply)
        .verifying("Invalid clock", c => c.estimateTotalTime > Centis(0))
    lazy val byoyomiClock = "clock" -> optional(byoyomiClockMapping)

    lazy val variant =
      "variant" -> optional(text.verifying(Variant.byKey.contains _))

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
        fischerClock,
        simpleDelayClock,
        bronsteinDelayClock,
        byoyomiClock,
        "days"          -> optional(days),
        "rated"         -> boolean,
        "playerIndex"   -> optional(playerIndex),
        "fen"           -> fenField,
        "acceptByToken" -> optional(nonEmptyText),
        "message"       -> message,
        "multiMatch"    -> optional(boolean)
      )(ApiConfig.from)(_ => none)
        .verifying("invalidFen", _.validFen)
        .verifying("can't be rated", _.validRated)

    lazy val ai = Form(
      mapping(
        "level" -> level,
        variant,
        fischerClock,
        simpleDelayClock,
        bronsteinDelayClock,
        byoyomiClock,
        "days"        -> optional(days),
        "playerIndex" -> optional(playerIndex),
        "fen"         -> fenField
      )(ApiAiConfig.from)(_ => none).verifying("invalidFen", _.validFen)
    )

    lazy val open = Form(
      mapping(
        "name" -> optional(lila.common.Form.cleanNonEmptyText(maxLength = 200)),
        variant,
        fischerClock,
        simpleDelayClock,
        bronsteinDelayClock,
        byoyomiClock,
        "rated" -> boolean,
        "fen"   -> fenField
      )(OpenConfig.from)(_ => none)
        .verifying("invalidFen", _.validFen)
        .verifying("rated without a clock", c => c.clock.isDefined || !c.rated)
    )
  }
}
