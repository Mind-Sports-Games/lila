package lila.analyse

import strategygames.format.pgn.{ FullTurn, Glyphs, Pgn, Tag, Turn }
import strategygames.opening.FullOpening
import strategygames.{ Player => PlayerIndex, Status }
import strategygames.variant.Variant

import lila.game.GameDrawOffers
import lila.game.Game

final class Annotator(netDomain: lila.common.config.NetDomain) {

  def apply(p: Pgn, game: Game, analysis: Option[Analysis]): Pgn =
    annotateStatus(game.winnerPlayerIndex, game.status, game.variant) {
      annotateOpening(game.opening) {
        annotateTurns(
          annotateDrawOffers(p, game.drawOffers, game.variant),
          analysis.??(_.advices)
        )
      }.copy(
        tags = p.tags + Tag(_.Annotator, netDomain)
      )
    }

  private def annotateStatus(winner: Option[PlayerIndex], status: Status, variant: Variant)(p: Pgn) =
    lila.game.StatusText(status, winner, variant) match {
      case ""   => p
      case text => p.updateLastTurnCount(_.copy(result = text.some))
    }

  private def annotateOpening(opening: Option[FullOpening.AtPly])(p: Pgn) =
    opening.fold(p) { o =>
      //Ply can be passed here as Openings aren't supported for multiaction
      p.updateTurnCount(o.ply, _.copy(opening = o.opening.toString().some))
    }

  private def annotateTurns(p: Pgn, advices: List[Advice]): Pgn =
    advices.foldLeft(p) { case (pgn, advice) =>
      pgn.updateFullTurn(
        advice.fullTurnNumber,
        fullTurn =>
          fullTurn.update(
            advice.playerIndex,
            turn =>
              turn.copy(
                glyphs = Glyphs.fromList(advice.judgment.glyph :: Nil),
                comments = advice.makeComment(withEval = true, withBestMove = true) :: turn.comments,
                variations = makeVariation(fullTurn, advice) :: Nil
              )
          )
      )
    }

  private def annotateDrawOffers(pgn: Pgn, drawOffers: GameDrawOffers, variant: Variant): Pgn =
    if (drawOffers.isEmpty) pgn
    else
      drawOffers.normalizedTurns.foldLeft(pgn) { case (pgn, turnCount) =>
        pgn.updateTurnCount(
          turnCount,
          turn => {
            val playerIndex = !PlayerIndex.fromTurnCount(turnCount)
            turn.copy(comments = s"$playerIndex offers draw" :: turn.comments)
          }
        )
      }

  private def makeVariation(fullTurn: FullTurn, advice: Advice): List[FullTurn] =
    FullTurn.fromTurns(
      //TODO Need to fix variation for multiaction
      advice.info.variation.take(20).flatten.toList map { san =>
        Turn(san)
      },
      fullTurn.turnOf(advice.playerIndex)
    )
}
