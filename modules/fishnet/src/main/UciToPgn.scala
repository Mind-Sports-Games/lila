package lila.fishnet

import cats.data.Validated
import cats.data.Validated.valid
import cats.implicits._
import strategygames.format.pgn.Dumper
import strategygames.format.Uci
import strategygames.{ Action, Drop, GameFamily, GameLogic, Move, Pass, Replay, SelectSquares, Situation }
import strategygames.variant.Variant

import lila.analyse.{ Analysis, Info, PgnMove }
import lila.base.LilaException

// convert variations from UCI to PGN.
// also drops extra variations
// This has not been upgraded to multiaction - should it be?
private object UciToPgn {

  type WithErrors[A] = (A, List[Exception])

  def apply(replay: Replay, analysis: Analysis, variant: Variant): WithErrors[Analysis] = {

    val pliesWithAdviceAndVariation: Set[Int] = analysis.advices.view.collect {
      case a if a.info.hasVariation => a.ply
    } to Set

    val onlyMeaningfulVariations: List[Info] = analysis.infos map { info =>
      if (pliesWithAdviceAndVariation(info.ply)) info
      else info.dropVariation
    }
    val logic  = variant.gameLogic
    val family = variant.gameFamily

    def uciToPgn(ply: Int, variation: List[String]): Validated[String, List[PgnMove]] =
      for {
        situation <-
          if (ply == replay.setup.startedAtPlies + 1) valid(replay.setup.situation)
          else
            replay
              .moveAtPly(ply)
              .map(action => {
                action match {
                  case m: Move           => m.situationBefore
                  case d: Drop           => d.situationBefore
                  case p: Pass           => p.situationBefore
                  case ss: SelectSquares => ss.situationBefore
                }
              }) toValid "No move found"
        ucis <- variation.map(v => Uci(logic, family, v)).sequence toValid "Invalid UCI moves " + variation
        moves <-
          ucis.foldLeft[Validated[String, (Situation, List[Action])]](valid(situation -> Nil)) {
            case (Validated.Valid((sit, moves)), uci: Uci.Move) =>
              sit.move(uci.orig, uci.dest, uci.promotion).leftMap(e => s"ply $ply $e") map { move =>
                move.situationAfter -> (move :: moves)
              }
            case (Validated.Valid((sit, moves)), uci: Uci.Drop) =>
              sit.drop(uci.role, uci.pos).leftMap(e => s"ply $ply $e") map { drop =>
                drop.situationAfter -> (drop :: moves)
              }
            case (Validated.Valid((sit, moves)), _: Uci.Pass) =>
              sit.pass.leftMap(e => s"ply $ply $e") map { pass =>
                pass.situationAfter -> (pass :: moves)
              }
            case (Validated.Valid((sit, moves)), uci: Uci.SelectSquares) =>
              sit.selectSquares(uci.squares).leftMap(e => s"ply $ply $e") map { ss =>
                ss.situationAfter -> (ss :: moves)
              }
            case (failure, _) => failure
          }
      } yield moves._2.reverse map (Dumper(logic, _))

    onlyMeaningfulVariations.foldLeft[WithErrors[List[Info]]]((Nil, Nil)) {
      case ((infos, errs), info) if info.variation.isEmpty => (info :: infos, errs)
      case ((infos, errs), info) =>
        uciToPgn(info.ply, info.variation.flatten.toList).fold(
          err => (info.dropVariation :: infos, LilaException(err) :: errs),
          pgn => (info.copy(variation = Vector(pgn)) :: infos, errs)
        )
    } match {
      case (infos, errors) => analysis.copy(infos = infos.reverse) -> errors
    }
  }
}
