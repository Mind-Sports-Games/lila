package lila.round

import strategygames.{ Player => PlayerIndex, Situation }
import lila.common.Bus
import lila.game.{ Event, Game, GameRepo, Pov, Progress, Rewind, UciMemo }
import lila.pref.{ Pref, PrefApi }
import lila.i18n.{ defaultLang, I18nKeys => trans }

final private class Undoer(
    messenger: Messenger,
    gameRepo: GameRepo,
    uciMemo: UciMemo,
    prefApi: PrefApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  implicit private val chatLang = defaultLang

  def apply(pov: Pov)(implicit proxy: GameProxy): Fu[Events] =
    IfAllowed(pov.game) {
      pov match {
        case Pov(game, playerIndex) if playerIndex == pov.game.turnPlayerIndex =>
          rewindPly(game)
        case _ => fufail(ClientError("[undoer] invalid undo " + pov))
      }
    }

  private def IfAllowed[A](game: Game)(f: => Fu[A]): Fu[A] =
    if (!game.playable) fufail(ClientError("[undoer] game is over " + game.id))
    else if (!game.situation.canUndo)
      fufail(ClientError("[undoer] situation (canUndo) disallows it " + game.id))
    else f

  private def rewindPly(game: Game)(implicit proxy: GameProxy): Fu[Events] =
    for {
      fen      <- gameRepo.initialFen(game)
      progress <- Rewind.undo(game, fen).toFuture
      _        <- uciMemo.set(progress.game, fen)
      events   <- save(progress)
    } yield events

  private def save(p1: Progress)(implicit proxy: GameProxy): Fu[Events] = {
    val p2 = p1 + Event.Reload
    messenger.system(p2.game, "Undo action taken")
    proxy.save(p2) inject p2.events
  }
}
