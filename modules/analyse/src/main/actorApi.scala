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

// NOTE(bg-analysis): backgammon analysis is stored in its own collection
// (BackgammonAnalysis), not as a chess Analysis + move tree, so it can't reuse
// AnalysisProgress. This minimal message just signals "the backgammon analysis
// for this game is ready". RoundDuct turns it into a "bgAnalysisProgress" socket
// message; the client then fetches the win% series from /<id>/backgammon-rating.json.
// TODO(bg-analysis): carry the win% series in here if you'd rather push than fetch.
case class BackgammonAnalysisProgress(gameId: String, complete: Boolean)
