package lila.round

import strategygames.{ Player => PlayerIndex }
import lila.common.Bus
import lila.game.{ Event, Game, GameRepo, Pov, Progress, Rewind, UciMemo }
import lila.pref.{ Pref, PrefApi }
import lila.i18n.{ defaultLang, I18nKeys => trans }
import RoundDuct.TakebackSituation

final private class Takebacker(
    messenger: Messenger,
    gameRepo: GameRepo,
    uciMemo: UciMemo,
    prefApi: PrefApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  implicit private val chatLang = defaultLang

  def yes(
      situation: TakebackSituation
  )(pov: Pov)(implicit proxy: GameProxy): Fu[(Events, TakebackSituation)] =
    IfAllowed(pov.game) {
      pov match {
        case Pov(game, playerIndex) if pov.opponent.isProposingTakeback =>
          {
            val povTurn = playerIndex == pov.game.turnPlayerIndex
            if (pov.opponent.proposeTakebackAt == pov.game.plies && povTurn)
              // go back until the playerindex switches
              takebackSwitchPlayer(game)
            else
              // go back one ply. if playerindex has not switched, continue going back
              takebackRetainPlayer(game)
          } dmap (_ -> situation.reset)
        case Pov(game, _) if pov.game.playableByAi => takebackSwitchPlayer(game) dmap (_ -> situation)
        case Pov(game, _) if pov.opponent.isAi     => takebackRetainPlayer(game) dmap (_ -> situation)
        case Pov(game, playerIndex) if (game playerCanProposeTakeback playerIndex) && situation.offerable =>
          {
            messenger.system(game, trans.takebackPropositionSent.txt())
            val progress = Progress(game) map { g =>
              g.updatePlayer(playerIndex, _ proposeTakeback g.plies)
            }
            proxy.save(progress) >>- publishTakebackOffer(pov) inject
              List(Event.TakebackOffers(playerIndex.p1, playerIndex.p2))
          } dmap (_ -> situation)
        case _ => fufail(ClientError("[takebacker] invalid yes " + pov))
      }
    }

  def no(situation: TakebackSituation)(pov: Pov)(implicit proxy: GameProxy): Fu[(Events, TakebackSituation)] =
    pov match {
      case Pov(game, playerIndex) if pov.player.isProposingTakeback =>
        proxy.save {
          messenger.system(game, trans.takebackPropositionCanceled.txt())
          Progress(game) map { g =>
            g.updatePlayer(playerIndex, _.removeTakebackProposition)
          }
        } inject {
          List(Event.TakebackOffers(p1 = false, p2 = false)) -> situation.decline
        }
      case Pov(game, playerIndex) if pov.opponent.isProposingTakeback =>
        proxy.save {
          messenger.system(game, trans.takebackPropositionDeclined.txt())
          Progress(game) map { g =>
            g.updatePlayer(!playerIndex, _.removeTakebackProposition)
          }
        } inject {
          List(Event.TakebackOffers(p1 = false, p2 = false)) -> situation.decline
        }
      case _ => fufail(ClientError("[takebacker] invalid no " + pov))
    }

  def isAllowedIn(game: Game): Fu[Boolean] =
    if (game.isMandatory || !game.situation.takebackable) fuFalse
    else isAllowedByPrefs(game)

  private def isAllowedByPrefs(game: Game): Fu[Boolean] =
    if (game.hasAi) fuTrue
    else
      game.userIds.map {
        prefApi.getPref(_, (p: Pref) => p.takeback)
      }.sequenceFu dmap {
        _.forall { p =>
          p == Pref.Takeback.ALWAYS || (p == Pref.Takeback.CASUAL && game.casual)
        }
      }

  private def publishTakebackOffer(pov: Pov): Unit =
    if (pov.game.isCorrespondence && pov.game.nonAi && pov.player.hasUser)
      Bus.publish(
        lila.hub.actorApi.round.CorresTakebackOfferEvent(pov.gameId),
        "offerEventCorres"
      )

  private def IfAllowed[A](game: Game)(f: => Fu[A]): Fu[A] =
    if (!game.playable) fufail(ClientError("[takebacker] game is over " + game.id))
    else if (game.isMandatory) fufail(ClientError("[takebacker] game disallows it " + game.id))
    else
      isAllowedByPrefs(game) flatMap {
        case true => f
        case _    => fufail(ClientError("[takebacker] disallowed by preferences " + game.id))
      }

  private def currentPlayerTakingBack(g: Game) =
    g.turnPlayerIndex == PlayerIndex.fromTurnCount(g.actionStrs.size + g.startPlayerIndex.hashCode - 1)

  // Would be nice to test these methods with a multimove game that has > 2 plies in a turn
  private def takebackSwitchPlayer(game: Game)(implicit proxy: GameProxy): Fu[Events] =
    if (currentPlayerTakingBack(game)) rewindPly(game)
    else rewindTurnAndPly(game)
  private def takebackRetainPlayer(game: Game)(implicit proxy: GameProxy): Fu[Events] =
    if (currentPlayerTakingBack(game)) rewindTurnAndPly(game)
    else rewindPly(game)

  private def rewindPly(game: Game)(implicit proxy: GameProxy): Fu[Events] =
    for {
      fen      <- gameRepo.initialFen(game)
      progress <- Rewind(game, fen, true).toFuture
      _        <- uciMemo.set(progress.game, fen)
      events   <- saveAndNotify(progress)
    } yield events

  private def rewindTurnAndPly(game: Game)(implicit proxy: GameProxy): Fu[Events] =
    for {
      fen   <- gameRepo.initialFen(game)
      prog1 <- Rewind(game, fen, false).toFuture
      prog2 <- Rewind(prog1.game, fen, true).toFuture dmap { progress =>
        prog1.withGame(progress.game)
      }
      _      <- uciMemo.set(prog2.game, fen)
      events <- saveAndNotify(prog2)
    } yield events

  // private def double(game: Game)(implicit proxy: GameProxy): Fu[Events] =
  //  for {
  //    fen   <- gameRepo initialFen game
  //    prog1 <- Rewind(game, fen, true).toFuture
  //    prog2 <- Rewind(prog1.game, fen, true).toFuture dmap { progress =>
  //      prog1 withGame progress.game
  //    }
  //    _      <- fuccess { uciMemo.drop(game, 2) }
  //    events <- saveAndNotify(prog2)
  //  } yield events

  private def saveAndNotify(p1: Progress)(implicit proxy: GameProxy): Fu[Events] = {
    val p2 = p1 + Event.Reload
    messenger.system(p2.game, trans.takebackPropositionAccepted.txt())
    proxy.save(p2) inject p2.events
  }
}
