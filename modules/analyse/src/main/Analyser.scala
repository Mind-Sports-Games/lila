package lila.analyse

import lila.common.Bus
import lila.game.actorApi.InsertGame
import lila.game.{ Game, GameRepo }
import lila.hub.actorApi.map.TellIfExists

import strategygames.variant.Variant
import strategygames.format.FEN

final class Analyser(
    gameRepo: GameRepo,
    analysisRepo: AnalysisRepo
)(implicit ec: scala.concurrent.ExecutionContext) {

  def get(game: Game): Fu[Option[Analysis]] =
    analysisRepo byGame game

  def byId(id: Analysis.ID): Fu[Option[Analysis]] = analysisRepo byId id

  def save(analysis: Analysis): Funit =
    analysis.studyId match {
      case None =>
        gameRepo game analysis.id flatMap {
          _ ?? { game =>
            gameRepo.setAnalysed(game.id)
            analysisRepo.save(analysis) >>
              sendAnalysisProgress(analysis, complete = true) >>- {
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

  private def sendAnalysisProgress(analysis: Analysis, complete: Boolean): Funit =
    analysis.studyId match {
      case None =>
        gameRepo gameWithInitialFen analysis.id map {
          _ ?? { case (game, initialFen) =>
            Bus.publish(
              TellIfExists(
                analysis.id,
                actorApi.AnalysisProgress(
                  analysis = analysis,
                  game = game,
                  variant = game.variant match {
                    case Variant.Chess(variant) => variant
                    case _=> sys.error("Analysis Progress not implemented for other types of games")
                  },
                  initialFen = (initialFen, game.variant.initialFen) match {
                    case (Some(FEN.Chess(fen)), _) => fen
                    case (_, FEN.Chess(fen)) => fen
                    case _ => sys.error("Unknown FEN type")
                  }
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
