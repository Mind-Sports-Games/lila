package lila.analyse
package actorApi

import lila.game.Game

import strategygames.variant.Variant
import strategygames.format.FEN

case class AnalysisReady(game: Game, analysis: Analysis)

case class AnalysisProgress(
    game: Game,
    variant: Variant,
    initialFen: FEN,
    analysis: Analysis
)

case class StudyAnalysisProgress(analysis: Analysis, complete: Boolean)
