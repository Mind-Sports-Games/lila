package lila.setup

import strategygames.GameLogic
import strategygames.format.FEN

case class ValidFen(fen: FEN, situation: strategygames.Situation) {

  def playerIndex = situation.player
}

object ValidFen {

  def apply(strict: Boolean)(fen: FEN): Option[ValidFen] = {
    val lib = fen match {
      case FEN.Chess(_)        => GameLogic.Chess()
      case FEN.Draughts(_)     => GameLogic.Draughts()
      case FEN.FairySF(_)      => GameLogic.FairySF()
      case FEN.Samurai(_)      => GameLogic.Samurai()
      case FEN.Togyzkumalak(_) => GameLogic.Togyzkumalak()
      case FEN.Go(_)           => GameLogic.Go()
    }
    for {
      parsed <- strategygames.format.Forsyth.<<<(lib, fen)
      if parsed.situation playable strict
      validated = strategygames.format.Forsyth.>>(lib, parsed)
    } yield ValidFen(validated, parsed.situation)
  }
}
