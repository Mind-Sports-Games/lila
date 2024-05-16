package lila.setup

import play.api.data.Forms._
import play.api.data.format.Formats._

import strategygames.{ GameFamily, Mode }
import strategygames.variant.Variant
import lila.rating.RatingRange
import lila.lobby.PlayerIndex

private object Mappings {

  val gameFamilys = number.verifying(Config.gameFamilys contains _)

  def variant(variants: Int => List[Int]) =
    text.verifying(
      Config.gameFamilys.map(gf => variants(gf).map((gf, _))).flatten.map { case (f, v) => s"${f}_${v}" }
        contains _
    )

  val boardApiVariantKeys =
    text.verifying(Config.gameFamilys.map(gf => Config.boardApiVariants(gf).map((gf, _))).flatten.map {
      case (f, v) => s"${f}_${v}"
    }
      contains _)

  val draughtsFenVariants =
    number.verifying(Config.fenVariants(GameFamily.Draughts().id) contains _)

  val time                     = of[Double].verifying(HookConfig validateTime _)
  val increment                = number.verifying(HookConfig validateIncrement _)
  val byoyomi                  = number.verifying(HookConfig validateByoyomi _)
  val periods                  = number.verifying(HookConfig validatePeriods _)
  val goHandicap               = number.verifying(FriendConfig validateGoHandicap _)
  def goKomi(boardSize: Int)   = number.verifying(FriendConfig.validateGoKomi(boardSize)(_))
  val days                     = number(min = 1, max = 14)
  def timeMode                 = number.verifying(TimeMode.ids contains _)
  def mode(withRated: Boolean) = optional(rawMode(withRated))
  def rawMode(withRated: Boolean) =
    number
      .verifying(HookConfig.modes contains _)
      .verifying(m => m == Mode.Casual.id || withRated)
  val ratingRange = text.verifying(RatingRange valid _)
  val playerIndex = text.verifying(PlayerIndex.names contains _)
  val level       = number.verifying(AiConfig.levels contains _)
  val speed       = number.verifying(Config.speeds contains _)
  val fenField    = optional(nonEmptyText)
}
