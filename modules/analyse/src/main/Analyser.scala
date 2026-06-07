package lila.analyse

import lila.common.Bus
import lila.game.actorApi.InsertGame
import lila.game.{ Game, GameRepo }
import lila.hub.actorApi.map.TellIfExists

import strategygames.variant.Variant
import strategygames.format.FEN

final class Analyser(
    gameRepo: GameRepo,
    analysisRepo: AnalysisRepo,
    backgammonRepo: BackgammonAnalysisRepo
)(implicit ec: scala.concurrent.ExecutionContext) {

  def get(game: Game): Fu[Option[Analysis]] =
    analysisRepo.byGame(game)

  def byId(id: Analysis.ID): Fu[Option[Analysis]] = analysisRepo.byId(id)

  def save(analysis: Analysis): Funit =
    analysis.studyId match {
      case None =>
        gameRepo.game(analysis.id) flatMap {
          _ so { game =>
            gameRepo.setAnalysed(game.id)
            analysisRepo.save(analysis) >>
              sendAnalysisProgress(analysis, complete = true).andDo {
                Bus.publish(actorApi.AnalysisReady(game, analysis), "analysisReady")
                Bus.publish(InsertGame(game), "gameSearchInsert")
              }
          }
        }
      case Some(_) =>
        analysisRepo.save(analysis) >>
          sendAnalysisProgress(analysis, complete = true)
    }

  def progress(analysis: Analysis): Funit = sendAnalysisProgress(analysis, complete = false)

  def getBackgammon(id: BackgammonAnalysis.ID): Fu[Option[BackgammonAnalysis]] =
    backgammonRepo.byId(id)

  // Store the whole-game backgammon analysis the worker posted. On a game (not a
  // study chapter) mark it analysed.
  def saveBackgammon(analysis: BackgammonAnalysis): Funit =
    backgammonRepo.save(analysis) map { _ =>
      if (analysis.studyId.isEmpty) gameRepo.setAnalysed(analysis.id)
    }

  private def sendAnalysisProgress(analysis: Analysis, complete: Boolean): Funit =
    analysis.studyId match {
      case None =>
        gameRepo.gameWithInitialFen(analysis.id) map {
          _ so { case (game, initialFen) =>
            Bus.publish(
              TellIfExists(
                analysis.id,
                actorApi.AnalysisProgress(
                  analysis = analysis,
                  game = game,
                  variant = game.variant,
                  initialFen = initialFen | game.variant.initialFen
                )
              ),
              "roundSocket"
            )
          }
        }
      case Some(_) =>
        fuccess {
          Bus.publish(actorApi.StudyAnalysisProgress(analysis, complete), "studyAnalysisProgress")
        }
    }
}
