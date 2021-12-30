package lila.setup

import strategygames.GameLogic
import strategygames.format.FEN

case class ValidFen(fen: FEN, situation: strategygames.Situation) {

  def sgPlayer = situation.player
}

object ValidFen {

  def apply(strict: Boolean)(fen: FEN): Option[ValidFen] = {
    val lib = fen match {
      case FEN.Chess(_) => GameLogic.Chess()
      case FEN.Draughts(_) => GameLogic.Draughts()
      case FEN.FairySF(_) => GameLogic.FairySF()
    }
    for {
      parsed <- strategygames.format.Forsyth.<<<(lib, fen)
      if parsed.situation playable strict
      validated = strategygames.format.Forsyth.>>(lib, parsed)
    } yield ValidFen(validated, parsed.situation)
  }
}
