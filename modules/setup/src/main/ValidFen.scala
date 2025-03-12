package lila.setup

import strategygames.GameLogic
import strategygames.format.FEN

case class ValidFen(fen: FEN, situation: strategygames.Situation) {

  def playerIndex = situation.player
}

object ValidFen {

  def apply(strict: Boolean)(fen: FEN): Option[ValidFen] = {
    val lib = fen.gameLogic
    for {
      parsed <- strategygames.format.Forsyth.<<<(lib, fen)
      if parsed.situation playable strict
      validated = strategygames.format.Forsyth.>>(lib, parsed)
    } yield ValidFen(validated, parsed.situation)
  }
}
