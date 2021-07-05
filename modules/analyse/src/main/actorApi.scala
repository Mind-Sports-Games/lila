package lila.analyse
package actorApi

import lila.game.Game

case class AnalysisReady(game: Game, analysis: Analysis)

case class AnalysisProgress(
    game: Game,
    variant: strategygames.chess.variant.Variant,
    initialFen: strategygames.chess.format.FEN,
    analysis: Analysis
)

case class StudyAnalysisProgress(analysis: Analysis, complete: Boolean)
