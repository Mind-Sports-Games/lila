package lila.setup

import strategygames.GameLib
import strategygames.format.FEN

case class ValidFen(fen: FEN, situation: strategygames.Situation) {

  def color = situation.color
}

object ValidFen {

  def apply(strict: Boolean)(fen: FEN): Option[ValidFen] = {
    val lib = fen match {
      case FEN.Chess(_) => GameLib.Chess()
      case FEN.Draughts(_) => GameLib.Draughts()
    }
    for {
      parsed <- strategygames.format.Forsyth.<<<(lib, fen)
      if parsed.situation playable strict
      validated = strategygames.format.Forsyth.>>(lib, parsed)
    } yield ValidFen(validated, parsed.situation)
  }
}
