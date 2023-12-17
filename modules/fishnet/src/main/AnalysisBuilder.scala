package lila.fishnet

import org.joda.time.DateTime

import strategygames.format.{ FEN, Uci }
import strategygames.Replay
import JsonApi.Request.Evaluation
import lila.analyse.{ Analysis, Info }
import lila.tree.Eval

final private class AnalysisBuilder(evalCache: FishnetEvalCache)(implicit
    ec: scala.concurrent.ExecutionContext
) {

  def apply(client: Client, work: Work.Analysis, evals: List[Evaluation.OrSkipped[Uci]]): Fu[Analysis] =
    partial(client, work, evals map some, isPartial = false)

  def partial(
      client: Client,
      work: Work.Analysis,
      evals: List[Option[Evaluation.OrSkipped[Uci]]],
      isPartial: Boolean = true
  ): Fu[Analysis] = {
    evalCache.evals(work) flatMap { cachedFull =>
      /* remove first eval in partial analysis
       * to prevent the mobile app from thinking it's complete
       * https://github.com/veloce/lichobile/issues/722
       */
      val cached = if (isPartial) cachedFull - 0 else cachedFull
      def debug  = s"${work.game.variant.key} analysis for ${work.game.id} by ${client.fullId}"
      Replay(
        work.game.variant.gameLogic,
        work.game.uciList,
        work.game.initialFen,
        work.game.variant
      )
        .fold(
          fufail(_),
          replay =>
            UciToPgn(
              replay,
              Analysis(
                id = work.game.id,
                studyId = work.game.studyId,
                infos = makeInfos(mergeEvalsAndCached(work, evals, cached), work.game.uciList, work.startPly),
                startPly = work.startPly,
                fk = !client.playstrategy option client.key.value,
                date = DateTime.now
              ),
              work.game.variant
            ) match {
              case (analysis, errors) =>
                errors foreach { e =>
                  logger.debug(s"[UciToPgn] $debug $e")
                }
                if (analysis.valid) {
                  if (!isPartial && analysis.emptyRatio >= 1d / 10)
                    fufail(
                      s"${work.game.variant.key} analysis $debug has ${analysis.nbEmptyInfos} empty infos out of ${analysis.infos.size}"
                    )
                  else fuccess(analysis)
                } else fufail(s"${work.game.variant.key} analysis $debug is empty")
            }
        )
    }
  }

  private def duplicateValsForMultiMoveGames(
      work: Work.Analysis,
      evals: List[Option[Evaluation.OrSkipped[Uci]]]
  ): List[Option[Evaluation.OrSkipped[Uci]]] =
    if (work.game.variant.key == "amazons") evals.flatMap(e => List(e, e)) else evals

  private def mergeEvalsAndCached(
      work: Work.Analysis,
      evals: List[Option[Evaluation.OrSkipped[Uci]]],
      cached: Map[Int, Evaluation[Uci]]
  ): List[Option[Evaluation[Uci]]] =
    duplicateValsForMultiMoveGames(work, evals).zipWithIndex.map {
      case (None, i)              => cached get i
      case (Some(Right(eval)), i) => cached.getOrElse(i, eval).some
      case (_, i) =>
        cached get i orElse {
          logger.error(s"Missing cached eval for skipped position at index $i in $work")
          none[Evaluation[Uci]]
        }
    }

  private def makeInfos(
      evals: List[Option[Evaluation[Uci]]],
      moves: List[Uci],
      startedAtTurn: Int
  ): List[Info] =
    (evals filterNot (_ ?? (_.isCheckmate)) sliding 2).toList.zip(moves).zipWithIndex map {
      case ((List(Some(before), Some(after)), move), index) =>
        val variation = before.cappedPv match {
          case first :: rest if first != move => first :: rest
          case _                              => Nil
        }
        val best = variation.headOption
        val info = Info(
          //can use startedAtTurn as startedAtPly as fishnet doesnt deal with multiaction
          ply = index + 1 + startedAtTurn,
          eval = Eval(
            after.score.cp,
            after.score.mate,
            best
          ),
          variation = variation.map(_.uci).map(Vector(_))
        )
        if (info.ply % 2 == 1) info.invert else info
      case ((_, _), index) => Info(index + 1 + startedAtTurn, Eval.empty)
    }
}
