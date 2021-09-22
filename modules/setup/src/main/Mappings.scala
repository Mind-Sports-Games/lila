package lila.setup

import play.api.data.Forms._
import play.api.data.format.Formats._

import strategygames.Mode
import lila.rating.RatingRange
import lila.lobby.Color

private object Mappings {

  val gameFamilys               = number.verifying(Config.gameFamilys contains _)

  val chessVariant                   = number.verifying(Config.chessVariants contains _)
  val chessVariantWithFen            = number.verifying(Config.chessVariantsWithFen contains _)
  val chessAIVariants                = number.verifying(Config.chessAIVariants contains _)
  val chessVariantWithVariants       = number.verifying(Config.chessVariantsWithVariants contains _)
  val chessVariantWithFenAndVariants = number.verifying(Config.chessVariantsWithFenAndVariants contains _)

  val draughtsVariant                   = number.verifying(Config.draughtsVariants contains _)
  val draughtsVariantWithFen            = number.verifying(Config.draughtsVariantsWithFen contains _)
  val draughtsAIVariants                = number.verifying(Config.draughtsAIVariants contains _)
  val draughtsFromPositionVariants      = number.verifying(Config.draughtsFromPositionVariants contains _)
  val draughtsVariantWithVariants       = number.verifying(Config.draughtsVariantsWithVariants contains _)
  val draughtsVariantWithFenAndVariants = number.verifying(Config.draughtsVariantsWithFenAndVariants contains _)

  val loaVariant                   = number.verifying(Config.loaVariants contains _)
  val loaVariantWithFen            = number.verifying(Config.loaVariantsWithFen contains _)
  val loaAIVariants                = number.verifying(Config.loaAIVariants contains _)
  val loaVariantWithVariants       = number.verifying(Config.loaVariantsWithVariants contains _)
  val loaVariantWithFenAndVariants = number.verifying(Config.loaVariantsWithFenAndVariants contains _)

  val chessBoardApiVariants = Set(
    strategygames.chess.variant.Standard.key,
    strategygames.chess.variant.Chess960.key,
    strategygames.chess.variant.Crazyhouse.key,
    strategygames.chess.variant.KingOfTheHill.key,
    strategygames.chess.variant.ThreeCheck.key,
    strategygames.chess.variant.Antichess.key,
    strategygames.chess.variant.Atomic.key,
    strategygames.chess.variant.Horde.key,
    strategygames.chess.variant.RacingKings.key
  )
  val chessBoardApiVariantKeys = text.verifying(chessBoardApiVariants contains _)

  val draughtsBoardApiVariants = Set(
    strategygames.draughts.variant.Standard.key,
    strategygames.draughts.variant.Frisian.key,
    strategygames.draughts.variant.Frysk.key,
    strategygames.draughts.variant.Antidraughts.key,
    strategygames.draughts.variant.Breakthrough.key,
    strategygames.draughts.variant.Russian.key,
    strategygames.draughts.variant.Brazilian.key,
    strategygames.draughts.variant.Pool.key,
  )
  val draughtsBoardApiVariantKeys = text.verifying(draughtsBoardApiVariants contains _)

  val loaBoardApiVariants = Set(
    strategygames.chess.variant.LinesOfAction.key
  )
  val loaBoardApiVariantKeys = text.verifying(loaBoardApiVariants contains _)

  val time                     = of[Double].verifying(HookConfig validateTime _)
  val increment                = number.verifying(HookConfig validateIncrement _)
  val days                     = number(min = 1, max = 14)
  def timeMode                 = number.verifying(TimeMode.ids contains _)
  def mode(withRated: Boolean) = optional(rawMode(withRated))
  def rawMode(withRated: Boolean) =
    number
      .verifying(HookConfig.modes contains _)
      .verifying(m => m == Mode.Casual.id || withRated)
  val ratingRange = text.verifying(RatingRange valid _)
  val color       = text.verifying(Color.names contains _)
  val level       = number.verifying(AiConfig.levels contains _)
  val speed       = number.verifying(Config.speeds contains _)
  val fenField    = optional(nonEmptyText)
}
