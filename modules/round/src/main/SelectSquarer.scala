package lila.round

import strategygames.{ Centis, Pos }

import lila.common.Bus
import lila.game.{ Event, Game, Pov, Progress }
import lila.i18n.{ I18nKeys => trans, defaultLang }
import lila.pref.{ Pref, PrefApi }

final private[round] class SelectSquarer(
    messenger: Messenger,
    finisher: Finisher,
    prefApi: PrefApi,
    isBotSync: lila.common.LightUser.IsBotSync
)(implicit ec: scala.concurrent.ExecutionContext) {

  implicit private val chatLang = defaultLang

  def accept(pov: Pov)(implicit proxy: GameProxy): Fu[Events] = pov.game.playable ?? {
    val squares: List[Pos] = pov.game.selectedSquares.getOrElse(List[Pos]().empty)
    pov match {
      case Pov(g, playerIndex) if pov.opponent.isOfferingSelectSquares =>
        proxy.save {
          messenger.system(g, trans.selectSquareOfferAccepted.txt())
          Progress(g) map { _.acceptSelectSquares(playerIndex) }
        } >>- publishSquareOfferEvent(pov) inject List(
          Event.SelectSquaresOffer(playerIndex, squares, Some(true))
        )
      case _ => fuccess(List(Event.ReloadOwner))
    }
  }

  def decline(pov: Pov)(implicit proxy: GameProxy): Fu[Events] = pov.game.playable ?? {
    val squares: List[Pos] = pov.game.selectedSquares.getOrElse(List[Pos]().empty)
    pov match {
      case Pov(g, playerIndex) if pov.opponent.isOfferingSelectSquares =>
        proxy.save {
          messenger.system(g, trans.selectSquareOfferDeclined.txt())
          Progress(g) map { _.declineSelectSquares(playerIndex) }
        } >>- publishSquareOfferEvent(pov) inject List(
          Event.SelectSquaresOffer(playerIndex, squares, Some(false))
        )
      case _ => fuccess(List(Event.ReloadOwner))
    }
  }

  def selectSquares(squares: List[Pos])(pov: Pov)(implicit proxy: GameProxy): Fu[Events] =
    pov.game.playable ?? {
      pov match {
        case Pov(g, playerIndex) if g playerCanOfferSelectSquares playerIndex =>
          proxy.save {
            messenger.system(g, trans.playerIndexOffersSelectSquares(pov.game.playerTrans(playerIndex)).v)
            Progress(g) map { _.offerSelectSquares(playerIndex, squares) }
          } >>- publishSquareOfferEvent(pov) inject List(
            Event.SelectSquaresOffer(playerIndex, squares)
          )
        case _ => fuccess(List(Event.ReloadOwner))
      }
    }

  private def publishSquareOfferEvent(pov: Pov)(implicit
      proxy: GameProxy
  ) = {
    if (pov.game.isCorrespondence && pov.game.nonAi)
      Bus.publish(
        lila.hub.actorApi.round.CorresSelectSquaresOfferEvent(pov.gameId),
        "offerEventCorres"
      )
    if (lila.game.Game.isBoardOrBotCompatible(pov.game))
      proxy
        .withPov(pov.playerIndex) { p =>
          fuccess(
            Bus.publish(lila.game.actorApi.BoardOfferSquares(p), s"boardSelectSquaresOffer:${pov.gameId}")
          )
        }
        .unit
  }

}
