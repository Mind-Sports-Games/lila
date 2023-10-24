package views.html.board

import play.api.i18n.Lang

import lila.app.templating.Environment._
import lila.i18n.{ MessageKey, I18nKeys => trans }

object userAnalysisI18n {

  def apply(
      withCeval: Boolean = true,
      withExplorer: Boolean = true,
      withForecast: Boolean = false,
      withAdvantageChart: Boolean = false
  )(implicit lang: Lang) =
    i18nJsObject(
      baseTranslations ++ {
        withCeval ?? cevalTranslations
      } ++ {
        withExplorer ?? explorerTranslations
      } ++ {
        withForecast ?? forecastTranslations
      } ++ {
        withAdvantageChart ?? advantageChartTranslations
      }
    )

  private val baseTranslations: Vector[MessageKey] = Vector(
    trans.analysis,
    trans.flipBoard,
    trans.backToGame,
    trans.gameAborted,
    trans.checkmate,
    trans.perpetualCheck,
    trans.playerIndexResigned,
    trans.stalemate,
    trans.playerIndexLeftTheGame,
    trans.draw,
    trans.playerIndexTimeOut,
    trans.playingRightNow,
    trans.playerIndexIsVictorious,
    trans.promotion,
    trans.cheatDetected,
    trans.kingInTheCenter,
    trans.threeChecks,
    trans.fiveChecks,
    trans.checkersConnected,
    trans.gameFinished,
    trans.variantEnding,
    trans.gameFinishedRepetition,
    trans.playerIndexPlays,
    trans.gameOver,
    trans.importPgn,
    trans.requestAComputerAnalysis,
    trans.computerAnalysis,
    trans.learnFromYourMistakes,
    trans.averageCentipawnLoss,
    trans.viewTheSolution,
    // action menu
    trans.menu,
    trans.boardEditor,
    trans.continueFromHere,
    trans.toStudy,
    trans.playWithTheMachine,
    trans.playWithAFriend,
    trans.openStudy,
    trans.preferences.preferences,
    trans.inlineNotation,
    trans.replayMode,
    trans.slow,
    trans.fast,
    trans.realtimeReplay,
    trans.byCPL,
    // context menu
    trans.promoteVariation,
    trans.makeMainLine,
    trans.deleteFromHere,
    trans.forceVariation,
    // practice (also uses checkmate, draw)
    trans.practiceWithComputer,
    trans.puzzle.goodMove,
    trans.inaccuracy,
    trans.mistake,
    trans.blunder,
    trans.perpetualWarning,
    trans.threefoldRepetition,
    trans.anotherWasX,
    trans.bestWasX,
    trans.youBrowsedAway,
    trans.resumePractice,
    trans.playerIndexWinsGame,
    trans.theGameIsADraw,
    trans.yourTurn,
    trans.computerThinking,
    trans.seeBestMove,
    trans.hideBestMove,
    trans.getAHint,
    trans.evaluatingYourMove,
    // gamebook
    trans.puzzle.findTheBestMoveForPlayerIndex
  ).map(_.key)

  private val cevalTranslations: Vector[MessageKey] = Vector(
    // also uses gameOver
    trans.depthX,
    trans.usingServerAnalysis,
    trans.loadingEngine,
    trans.cloudAnalysis,
    trans.goDeeper,
    trans.showThreat,
    trans.inLocalBrowser,
    trans.toggleLocalEvaluation,
    // ceval menu
    trans.computerAnalysis,
    trans.enable,
    trans.bestMoveArrow,
    trans.evaluationGauge,
    trans.infiniteAnalysis,
    trans.removesTheDepthLimit,
    trans.multipleLines,
    trans.cpus,
    trans.memory
  ).map(_.key)

  private val explorerTranslations: Vector[MessageKey] = Vector(
    // also uses gameOver, checkmate, stalemate, draw, variantEnding
    trans.openingExplorerAndTablebase,
    trans.openingExplorer,
    trans.xOpeningExplorer,
    trans.move,
    trans.games,
    trans.variantLoss,
    trans.variantWin,
    trans.insufficientMaterial,
    trans.capture,
    trans.pawnMove,
    trans.close,
    trans.winning,
    trans.unknown,
    trans.losing,
    trans.drawn,
    trans.timeControl,
    trans.averageElo,
    trans.database,
    trans.recentGames,
    trans.topGames,
    trans.p1DrawP2,
    trans.averageRatingX,
    trans.masterDbExplanation,
    trans.mateInXHalfMoves,
    trans.nextCaptureOrPawnMoveInXHalfMoves,
    trans.noGameFound,
    trans.maybeIncludeMoreGamesFromThePreferencesMenu,
    trans.winPreventedBy50MoveRule,
    trans.lossSavedBy50MoveRule,
    trans.allSet
  ).map(_.key)

  private val forecastTranslations: Vector[MessageKey] = Vector(
    trans.conditionalPremoves,
    trans.addCurrentVariation,
    trans.playVariationToCreateConditionalPremoves,
    trans.noConditionalPremoves,
    trans.playX,
    trans.andSaveNbPremoveLines
  ).map(_.key)

  private val advantageChartTranslations: Vector[MessageKey] = Vector(
    trans.nbInaccuracies,
    trans.nbMistakes,
    trans.nbBlunders,
    trans.advantage,
    trans.opening,
    trans.middlegame,
    trans.endgame
  ).map(_.key)
}
