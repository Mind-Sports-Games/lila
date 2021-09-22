package lila.setup

import strategygames.GameLogic
import strategygames.format.FEN

case class ValidFen(fen: FEN, situation: strategygames.Situation) {

  def color = situation.color
}

object ValidFen {

  def apply(strict: Boolean)(fen: FEN): Option[ValidFen] = {
    val lib = fen match {
      case FEN.Chess(_) => GameLogic.Chess()
      case FEN.Draughts(_) => GameLogic.Draughts()
    }
    for {
      parsed <- strategygames.format.Forsyth.<<<(lib, fen)
      if parsed.situation playable strict
      validated = strategygames.format.Forsyth.>>(lib, parsed)
    } yield ValidFen(validated, parsed.situation)
  }
}
