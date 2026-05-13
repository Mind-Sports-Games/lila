package lila.setup

import play.api.data.Forms.*
import play.api.data.format.Formats.*

import strategygames.{ GameFamily, Mode }
import lila.rating.RatingRange
import lila.lobby.PlayerIndex

private object Mappings {

  val gameFamilys = number.verifying(Config.gameFamilys.contains)

  def variant(variants: Int => List[Int]) =
    text.verifying(
      Config.gameFamilys
        .map(gf => variants(gf).map((gf, _)))
        .flatten
        .map { case (f, v) => s"${f}_${v}" }
        .contains(_)
    )

  val boardApiVariantKeys =
    text.verifying(
      Config.gameFamilys
        .map(gf => Config.boardApiVariants(gf).map((gf, _)))
        .flatten
        .map { case (f, v) =>
          s"${f}_${v}"
        }
        .contains(_)
    )

  val draughtsFenVariants =
    number.verifying(Config.fenVariants(GameFamily.Draughts().id).contains)

  val time                        = of[Double].verifying(HookConfig.validateTime)
  val increment                   = number.verifying(HookConfig.validateIncrement)
  val byoyomi                     = number.verifying(HookConfig.validateByoyomi)
  val periods                     = number.verifying(HookConfig.validatePeriods)
  val goHandicap                  = number.verifying(GameConfig.validateGoHandicap)
  def goKomi(boardSize: Int)      = number.verifying(GameConfig.validateGoKomi(boardSize)(_))
  val backgammonPoints            = number.verifying(GameConfig.validateBackgammonPoints)
  val days                        = number(min = 1, max = 14)
  def timeMode                    = number.verifying(TimeMode.ids.contains)
  def mode(withRated: Boolean)    = optional(rawMode(withRated))
  def rawMode(withRated: Boolean) =
    number
      .verifying(HookConfig.modes.contains)
      .verifying(m => m == Mode.Casual.id || withRated)
  val ratingRange  = text.verifying(RatingRange.valid)
  val playerIndex  = text.verifying(PlayerIndex.names.contains)
  val speed        = number.verifying(Config.speeds.contains)
  val fenField     = optional(nonEmptyText)
  val opponentType = text.verifying(GameConfig.opponentTypes.contains)
}
