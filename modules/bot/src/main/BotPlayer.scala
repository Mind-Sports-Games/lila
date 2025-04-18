package lila.bot

import strategygames.format.Uci
import strategygames.Pos
import scala.concurrent.duration._
import scala.concurrent.Promise

import lila.common.Bus
import lila.game.Game.PlayerId
import lila.game.{ Game, GameRepo, Pov }
import lila.hub.actorApi.map.Tell
import lila.hub.actorApi.round.{ Abort, BotPlay, RematchNo, RematchYes, Resign }
import lila.round.actorApi.round.{
  DrawNo,
  DrawYes,
  PlayerSelectSquares,
  SelectSquaresAccept,
  SelectSquaresDecline
}
import lila.user.User

final class BotPlayer(
    chatApi: lila.chat.ChatApi,
    gameRepo: GameRepo,
    isOfferingRematch: lila.round.IsOfferingRematch,
    spam: lila.security.Spam
)(implicit
    ec: scala.concurrent.ExecutionContext,
    scheduler: akka.actor.Scheduler
) {

  private def clientError[A](msg: String): Fu[A] = fufail(lila.round.ClientError(msg))

  def apply(pov: Pov, me: User, uciStr: String, offeringDraw: Option[Boolean]): Funit =
    lila.common.Future.delay((pov.game.hasAi ?? 500) millis) {
      Uci(
        pov.game.variant.gameLogic,
        pov.game.variant.gameFamily,
        uciStr
      ).fold(clientError[Unit](s"Invalid UCI: $uciStr")) { uci =>
        lila.mon.bot.moves(me.username).increment()
        if (!pov.isMyTurn) clientError("Not your turn, or game already over")
        else {
          val promise = Promise[Unit]()
          if (pov.player.isOfferingDraw && offeringDraw.has(false)) declineDraw(pov)
          else if (!pov.player.isOfferingDraw && ~offeringDraw) offerDraw(pov)
          tellRound(pov.gameId, BotPlay(pov.playerId, uci, promise.some))
          promise.future recover {
            case _: lila.round.GameIsFinishedError if ~offeringDraw && pov.game.drawn => ()
          }
        }
      }
    }

  def chat(gameId: Game.ID, me: User, d: BotForm.ChatData) =
    !spam.detect(d.text) ??
      fuccess {
        lila.mon.bot.chats(me.username).increment()
        val chatId = lila.chat.Chat.Id {
          if (d.room == "player") gameId else s"$gameId/w"
        }
        val source = d.room == "spectator" option {
          lila.hub.actorApi.shutup.PublicSource.Watcher(gameId)
        }
        chatApi.userChat.write(chatId, me.id, d.text, publicSource = source, _.Round)
      }

  def rematchAccept(id: Game.ID, me: User): Fu[Boolean] = rematch(id, me, accept = true)

  def rematchDecline(id: Game.ID, me: User): Fu[Boolean] = rematch(id, me, accept = false)

  private def rematch(id: Game.ID, me: User, accept: Boolean): Fu[Boolean] =
    gameRepo game id map {
      _.flatMap(Pov(_, me)).filter(p => isOfferingRematch(!p)) ?? { pov =>
        // delay so it feels more natural
        lila.common.Future.delay(if (accept) 100.millis else 2.seconds) {
          fuccess {
            tellRound(pov.gameId, (if (accept) RematchYes else RematchNo)(pov.playerId))
          }
        }
        true
      }
    }

  private def tellRound(id: Game.ID, msg: Any) =
    Bus.publish(Tell(id, msg), "roundSocket")

  def abort(pov: Pov): Funit =
    if (!pov.game.abortable) clientError("This game can no longer be aborted")
    else
      fuccess {
        tellRound(pov.gameId, Abort(pov.playerId))
      }

  def resign(pov: Pov): Funit =
    if (pov.game.abortable) abort(pov)
    else if (pov.game.resignable) fuccess {
      tellRound(pov.gameId, Resign(pov.playerId))
    }
    else clientError("This game cannot be resigned")

  def declineDraw(pov: Pov): Unit =
    if (pov.game.drawable && pov.opponent.isOfferingDraw)
      tellRound(pov.gameId, DrawNo(PlayerId(pov.playerId)))

  def offerDraw(pov: Pov): Unit =
    if (pov.game.drawable && (pov.game.playerCanOfferDraw(pov.playerIndex) || pov.opponent.isOfferingDraw))
      tellRound(pov.gameId, DrawYes(PlayerId(pov.playerId)))

  def setDraw(pov: Pov, v: Boolean): Unit =
    if (v) offerDraw(pov) else declineDraw(pov)

  def declineSelectSquares(pov: Pov): Unit =
    if (pov.opponent.isOfferingSelectSquares)
      tellRound(pov.gameId, SelectSquaresDecline(PlayerId(pov.playerId)))

  def acceptSelectSquares(pov: Pov): Unit =
    if (pov.opponent.isOfferingSelectSquares)
      tellRound(pov.gameId, SelectSquaresAccept(PlayerId(pov.playerId)))

  def decideSelectSquares(pov: Pov, v: Boolean): Unit =
    if (v) acceptSelectSquares(pov) else declineSelectSquares(pov)

  def selectSquares(pov: Pov, squares: List[Pos]): Unit =
    tellRound(pov.gameId, PlayerSelectSquares(PlayerId(pov.playerId), squares))
}
