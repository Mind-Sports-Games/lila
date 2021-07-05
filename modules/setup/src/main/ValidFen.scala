package lila.setup

import strategygames.chess.format.FEN

case class ValidFen(fen: FEN, situation: strategygames.chess.Situation) {

  def color = situation.color
}

object ValidFen {
  def apply(strict: Boolean)(fen: FEN): Option[ValidFen] =
    for {
      parsed <- strategygames.chess.format.Forsyth <<< fen
      if parsed.situation playable strict
      validated = strategygames.chess.format.Forsyth >> parsed
    } yield ValidFen(validated, parsed.situation)
}
