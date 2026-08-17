package lila.fishnet

import org.joda.time.DateTime

import lila.analyse.AnalysisRepo
import lila.game.{ Game, UciMemo }

final class Analyser(
    repo: FishnetRepo,
    analysisRepo: AnalysisRepo,
    gameRepo: lila.game.GameRepo,
    uciMemo: UciMemo,
    evalCache: FishnetEvalCache,
    limiter: FishnetLimiter,
    sgfDump: lila.game.SgfDump
)(implicit
    ec: scala.concurrent.ExecutionContext,
    scheduler: akka.actor.Scheduler
) {

  val maxPlies = 200

  private val workQueue = new lila.hub.DuctSequencer(maxSize = 256, timeout = 5 seconds, "fishnetAnalyser")

  def apply(game: Game, sender: Work.Sender): Fu[Boolean] =
    request(game, sender) map (_.accepted)

  def request(game: Game, sender: Work.Sender): Fu[Analyser.Result] =
    (game.metadata.analysed so analysisRepo.exists(game.id)) flatMap {
      case true                  => fuccess(Analyser.Result.AlreadyAnalysed)
      case _ if !game.analysable => fuccess(Analyser.Result.NotAnalysable)
      case _                     =>
        limiter(
          sender,
          ignoreConcurrentCheck = false,
          ownGame = game.userIds contains sender.userId
        ) flatMap {
          case Some(decline) => fuccess(Analyser.Result.Declined(decline.reason))
          case None          =>
            makeWork(game, sender) flatMap { work =>
              workQueue {
                repo.getSimilarAnalysis(work) flatMap {
                  // already in progress, do nothing
                  case Some(similar) if similar.isAcquired => funit
                  // queued by system, reschedule for the human sender
                  case Some(similar) if similar.sender.system && !sender.system =>
                    repo.updateAnalysis(similar.copy(sender = sender))
                  // queued for someone else, do nothing
                  case Some(_) => funit
                  // first request, store
                  case _ =>
                    lila.mon.fishnet.analysis.requestCount("game").increment()
                    evalCache.skipPositions(work.game) flatMap { skipPositions =>
                      lila.mon.fishnet.analysis.evalCacheHits.record(skipPositions.size)
                      repo.addAnalysis(work.copy(skipPositions = skipPositions))
                    }
                }
              }
            } inject Analyser.Result.Accepted
        }
    }

  def apply(gameId: String, sender: Work.Sender): Fu[Boolean] =
    gameRepo.game(gameId) flatMap { _ so { apply(_, sender) } }

  def study(req: lila.hub.actorApi.fishnet.StudyChapterRequest): Fu[Boolean] =
    analysisRepo.exists(req.chapterId) flatMap {
      case true => fuFalse
      case _    =>
        import req.*
        val sender = Work.Sender(req.userId, none, mod = false, system = false)
        (if (req.unlimited) fuccess(none)
         else limiter(sender, ignoreConcurrentCheck = true, ownGame = false)) flatMap { decline =>
          decline.foreach { d =>
            logger.info(s"Study request declined: ${req.studyId}/${req.chapterId} by $sender: ${d.reason}")
          }
          decline.isEmpty so {
            val work = makeWork(
              game = Work.Game(
                id = chapterId,
                initialFen = initialFen,
                studyId = studyId.some,
                variant = variant,
                moves = moves take maxPlies map (_.uci) mkString " "
              ),
              // if p2 moves first, use 1 as startPly so the analysis doesn't get reversed
              startPly = initialFen.flatMap(_.player).so(_.fold(0, 1)),
              sender = sender
            )
            workQueue {
              repo.getSimilarAnalysis(work) flatMap {
                _.isEmpty so {
                  lila.mon.fishnet.analysis.requestCount("study").increment()
                  evalCache.skipPositions(work.game) flatMap { skipPositions =>
                    lila.mon.fishnet.analysis.evalCacheHits.record(skipPositions.size)
                    repo.addAnalysis(work.copy(skipPositions = skipPositions))
                  }
                }
              }
            }
          } inject decline.isEmpty
        }
    }

  private def makeWork(game: Game, sender: Work.Sender): Fu[Work.Analysis] =
    gameRepo.initialFen(game) zip uciMemo.get(game) flatMap { case (initialFen, moves) =>
      val cappedMoves = moves.take(maxPlies)
      backgammonWork(game, initialFen) map { backgammon =>
        makeWork(
          game = Work.Game(
            id = game.id,
            initialFen = initialFen,
            studyId = none,
            variant = game.variant,
            moves = cappedMoves.map(_.mkString(",")).mkString(" "),
            backgammon = backgammon
          ),
          startPly = game.stratGame.startedAtPly,
          sender = sender
        )
      }
    }

  private def backgammonWork(game: Game, initialFen: Option[strategygames.format.FEN]): Fu[Option[Work.BgWork]] =
    if (game.variant.gameLogic == strategygames.GameLogic.Backgammon())
      sgfDump(game, initialFen, isTags = true).map(sgf => Work.BgWork(sgf).some)
    else fuccess(none)

  private def makeWork(game: Work.Game, startPly: Int, sender: Work.Sender): Work.Analysis =
    Work.Analysis(
      _id = Work.makeId,
      sender = sender,
      game = game,
      startPly = startPly,
      tries = 0,
      lastTryByKey = none,
      acquired = none,
      skipPositions = Nil,
      createdAt = DateTime.now
    )
}

object Analyser {

  sealed abstract class Result(val accepted: Boolean, val reason: String)

  object Result {
    case object Accepted             extends Result(true, "")
    case object AlreadyAnalysed      extends Result(false, "This game already has a computer analysis")
    case object NotAnalysable        extends Result(false, "This game can not be analysed") // generic and should not happen
    case class Declined(why: String) extends Result(false, why)
  }
}
