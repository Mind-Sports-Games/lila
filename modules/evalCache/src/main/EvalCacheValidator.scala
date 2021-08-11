package lila.evalCache

import strategygames.{ GameLib, Replay }

private object Validator {

  case class Error(message: String) extends AnyVal

  def apply(in: EvalCacheEntry.Input): Option[Error] =
    in.eval.pvs.toList.foldLeft(none[Error]) {
      case (None, pv) =>
        Replay.boardsFromUci(
          in.id.variant.gameLib,
          pv.moves.value.toList,
          in.fen.some,
          in.id.variant,
          in.id.variant.gameLib match {
            case GameLib.Draughts() => true
            case _ => false
          }
        )
        .fold(err => Error(err).some, _ => none)
      case (error, _) => error
    }
}
