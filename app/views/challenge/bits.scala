package views.html.challenge

import play.api.libs.json.Json

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.challenge.Challenge
import lila.common.String.html.safeJsonValue
import strategygames.GameFamily
import strategygames.format.FEN

import controllers.routes

object bits {

  def js(
      c: Challenge,
      json: play.api.libs.json.JsObject,
      owner: Boolean,
      playerIndex: Option[strategygames.Player] = None
  )(implicit
      ctx: Context
  ) =
    frag(
      jsModule("challengePage"),
      embedJsUnsafeLoadThen(s"""PlayStrategyChallenge(${safeJsonValue(
        Json.obj(
          "socketUrl" -> s"/challenge/${c.id}/socket/v$apiVersion",
          "xhrUrl"    -> routes.Challenge.show(c.id, playerIndex.map(_.name)).url,
          "owner"     -> owner,
          "data"      -> json
        )
      )})""")
    )

  def details(c: Challenge, requestedPlayerIndex: Option[strategygames.Player])(implicit ctx: Context) = frag(
    div(cls := "details")(
      div(cls := "variant", dataIcon := (if (c.initialFen.isDefined) '*' else c.perfType.iconChar))(
        div(
          if (c.variant.exotic)
            views.html.game.bits.variantLink(c.variant, variantName(c.variant))
          else
            c.perfType.trans,
          (c.initialFen, c.variant.gameFamily) match {
            case (Some(f), GameFamily.Go()) => " " + c.variant.toGo.setupInfo(f.toGo).getOrElse("")
            case (Some(FEN.Backgammon(f)), GameFamily.Backgammon()) if f.pp("bfen").cubeData.nonEmpty =>
              " Multipoint" //TODO put point information in here, but this won't be in the fen
            case _ => ""
          },
          br,
          span(cls := "clock")(
            c.daysPerTurn map { days =>
              if (days == 1) trans.oneDay()
              else trans.nbDays.pluralSame(days)
            } getOrElse shortClockName(c.clock.map(_.config))
          )
        )
      ),
      div(cls := "mode")(
        c.open.fold(c.playerIndexChoice.some)(_ =>
          requestedPlayerIndex.map(Challenge.PlayerIndexChoice(_))
        ) map { playerIndexChoice =>
          frag(c.playerChoiceTrans(playerIndexChoice).toString(), " • ")
        },
        modeName(c.mode)
      )
    ),
    c.isMultiMatch option div(cls := "multi-match")(
      trans.multiMatchChallenge(),
      " ",
      trans.multiMatchDefinition()
    )
  )

}
