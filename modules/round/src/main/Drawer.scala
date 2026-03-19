package lila.round

import strategygames.Centis

import lila.common.Bus
import lila.game.{ Event, Game, Pov, Progress }
import lila.i18n.{ I18nKeys => trans, defaultLang }
import lila.pref.{ Pref, PrefApi }
import play.api.i18n.Lang

final private[round] class Drawer(
    messenger: Messenger,
    finisher: Finisher,
    prefApi: PrefApi,
    isBotSync: lila.common.LightUser.IsBotSync
)(implicit ec: scala.concurrent.ExecutionContext) {

  implicit private val chatLang: Lang = defaultLang

  def autoThreefold(game: Game): Fu[Option[Pov]] = if (game.playable)
    Future.sequence(Pov(game)
      .map { pov =>
        import Pref.PrefZero
        if (game.playerHasOfferedDrawRecently(pov.playerIndex)) fuccess(pov.some)
        else
          pov.player.userId so prefApi.getPref map { pref =>
            pref.autoThreefold == Pref.AutoThreefold.ALWAYS || {
              pref.autoThreefold == Pref.AutoThreefold.TIME &&
              game.clock.exists(_.remainingTime(pov.playerIndex) < Centis.ofSeconds(30))
            } || pov.player.userId.exists(isBotSync)
          } map { if (_) pov.some else none }
      })
      .dmap(_.flatten.headOption)
  else fuccess(none)

  def yes(pov: Pov)(implicit proxy: GameProxy): Fu[Events] = pov.game.playable so {
    pov match {
      case pov if pov.game.situation.threefoldRepetition =>
        finisher.other(pov.game, _.Draw, None)
      case pov if pov.opponent.isOfferingDraw =>
        finisher.other(pov.game, _.Draw, None, Some(trans.drawOfferAccepted.txt()))
      case Pov(g, playerIndex) if g playerCanOfferDraw playerIndex =>
        proxy.save {
          messenger.system(g, trans.playerIndexOffersDraw(pov.game.playerTrans(playerIndex)).v)
          Progress(g) map { _ offerDraw playerIndex }
        }.andDo(publishDrawOffer(pov)) inject List(Event.DrawOffer(by = playerIndex.some))
      case _ => fuccess(List(Event.ReloadOwner))
    }
  }

  def no(pov: Pov)(implicit proxy: GameProxy): Fu[Events] = if (!pov.game.playable) fuccess(Nil) else {
    pov match {
      case Pov(g, playerIndex) if pov.player.isOfferingDraw =>
        proxy.save {
          messenger.system(g, trans.drawOfferCanceled.txt())
          Progress(g) map { g =>
            g.updatePlayer(playerIndex, _.removeDrawOffer)
          }
        } inject List(Event.DrawOffer(by = none))
      case Pov(g, playerIndex) if pov.opponent.isOfferingDraw =>
        proxy.save {
          messenger.system(g, trans.playerIndexDeclinesDraw(pov.game.playerTrans(playerIndex)).v)
          Progress(g) map { g =>
            g.updatePlayer(!playerIndex, _.removeDrawOffer)
          }
        } inject List(Event.DrawOffer(by = none))
      case _ => fuccess(List(Event.ReloadOwner))
    }
  }

  def claim(pov: Pov)(implicit proxy: GameProxy): Fu[Events] =
    if (pov.game.playable && pov.game.situation.threefoldRepetition) finisher.other(
      pov.game,
      _.Draw,
      None
    )
    else fuccess(Nil)

  def force(game: Game)(implicit proxy: GameProxy): Fu[Events] = finisher.other(game, _.Draw, None, None)

  private def publishDrawOffer(pov: Pov)(implicit proxy: GameProxy): Unit = {
    if (pov.game.isCorrespondence && pov.game.nonAi)
      Bus.publish(
        lila.hub.actorApi.round.CorresDrawOfferEvent(pov.gameId),
        "offerEventCorres"
      )
    if (lila.game.Game.isBoardOrBotCompatible(pov.game))
      proxy
        .withPov(pov.playerIndex) { p =>
          fuccess(
            Bus.publish(
              lila.game.actorApi.BoardDrawOffer(p),
              s"boardDrawOffer:${pov.gameId}"
            )
          )
        }
        .discard
  }
}
