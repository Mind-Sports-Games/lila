package lila.fishnet

import strategygames.format.{ FEN, Forsyth }
import strategygames.{ GameLib, Replay }
import JsonApi.Request.Evaluation

final private class FishnetEvalCache(
    evalCacheApi: lila.evalCache.EvalCacheApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  val maxPlies = 15

  // indexes of positions to skip
  def skipPositions(game: Work.Game): Fu[List[Int]] =
    rawEvals(game).dmap(_.map(_._1))

  def evals(work: Work.Analysis): Fu[Map[Int, Evaluation]] =
    rawEvals(work.game) map {
      _.map { case (i, eval) =>
        val pv = eval.pvs.head
        i -> Evaluation(
          pv = pv.moves.value.toList,
          score = Evaluation
            .Score(
              cp = pv.score.cp,
              mate = pv.score.mate
            )
            .invertIf((i + work.startPly) % 2 == 1), // fishnet evals are from POV
          time = none,
          nodes = eval.knodes.intNodes.some,
          nps = none,
          depth = eval.depth.some
        )
      }.toMap
    }

  private def rawEvals(game: Work.Game): Fu[List[(Int, lila.evalCache.EvalCacheEntry.Eval)]] =
    Replay
      .situationsFromUci(
        GameLib.Chess(),
        game.uciList.take(maxPlies - 1),
        game.initialFen.map(fen => FEN(GameLib.Chess(), fen)),
        game.variant
      )
      .fold(
        _ => fuccess(Nil),
        _.zipWithIndex
          .map { case (sit, index) =>
            evalCacheApi.getSinglePvEval(game.variant, Forsyth.>>(GameLib.Chess(), sit)) dmap2 { index -> _ }
          }
          .sequenceFu
          .map(_.flatten)
      )
}
