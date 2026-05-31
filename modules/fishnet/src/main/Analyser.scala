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
    limiter: FishnetLimiter
)(implicit
    ec: scala.concurrent.ExecutionContext,
    scheduler: akka.actor.Scheduler
) {

  val maxPlies = 200

  private val workQueue = new lila.hub.DuctSequencer(maxSize = 256, timeout = 5 seconds, "fishnetAnalyser")

  def apply(game: Game, sender: Work.Sender): Fu[Boolean] =
    (game.metadata.analysed so analysisRepo.exists(game.id)) flatMap {
      case true                  => fuFalse
      case _ if !game.analysable => fuFalse
      case _                     =>
        limiter(
          sender,
          ignoreConcurrentCheck = false,
          ownGame = game.userIds contains sender.userId
        ) flatMap { accepted =>
          accepted so {
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
            }
          } inject accepted
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
        (fuccess(req.unlimited) >>| limiter(sender, ignoreConcurrentCheck = true, ownGame = false)) flatMap {
          accepted =>
            if (!accepted)
              logger.info(s"Study request declined: ${req.studyId}/${req.chapterId} by $sender")
            accepted so {
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
            } inject accepted
        }
    }

  private def makeWork(game: Game, sender: Work.Sender): Fu[Work.Analysis] =
    gameRepo.initialFen(game) zip uciMemo.get(game) map { case (initialFen, moves) =>
      val cappedMoves = moves.take(maxPlies)
      makeWork(
        game = Work.Game(
          id = game.id,
          initialFen = initialFen,
          studyId = none,
          variant = game.variant,
          moves = cappedMoves.map(_.mkString(",")).mkString(" "),
          backgammon = backgammonWork(game, initialFen, cappedMoves)
        ),
        startPly = game.stratGame.startedAtPly,
        sender = sender
      )
    }

  // For backgammon games, replay the action list and emit one decision per dice
  // roll (the chequer-play position: board + dice + cube + score). The gnubg
  // worker turns each FEN into an XGID and evaluates it. Cube decisions are not
  // emitted yet. None for any non-backgammon variant.
  private def backgammonWork(
      game: Game,
      initialFen: Option[strategygames.format.FEN],
      actionStrs: strategygames.ActionStrs
  ): Option[Work.BgWork] =
    Option.when(game.variant.gameLogic == strategygames.GameLogic.Backgammon()) {
      val bgVariant = game.variant.toBackgammon
      val fen       = initialFen.fold(bgVariant.initialFen)(_.toBackgammon)
      val games     = strategygames.backgammon.Replay.gameWithUciWhileValid(actionStrs, fen, bgVariant)._2
      val decisions = games.collect {
        case (gameAfter, withSan)
            if withSan.uci.isInstanceOf[strategygames.backgammon.format.Uci.DiceRoll] =>
          strategygames.backgammon.format.Forsyth.>>(gameAfter).value
      }.zipWithIndex.map { case (decisionFen, idx) => Work.BgDecision(idx, "chequer", decisionFen) }
      Work.BgWork(matchLength = game.multiPointState.fold(0)(_.target), decisions = decisions)
    }

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
