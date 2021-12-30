package views.html.base

import controllers.routes
import play.api.libs.json.Json
import scala.language.reflectiveCalls

import strategygames.{ Player => SGPlayer, GameLogic }
import strategygames.format.FEN

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.String.html.safeJsonValue

object captcha {

  private val dataCheckUrl = attr("data-check-url")
  private val dataMoves    = attr("data-moves")
  private val dataPlayable = attr("data-playable")

  def apply(form: lila.common.Form.FormLike, captcha: lila.common.Captcha)(implicit ctx: Context) =
    frag(
      form3.hidden(form("gameId"), captcha.gameId.some),
      if (ctx.blind) form3.hidden(form("move"), captcha.solutions.head.some)
      else {
        val url = netBaseUrl + routes.Round.watcher(captcha.gameId, if (captcha.p1) "p1" else "p2")
        div(
          cls := List(
            "captcha form-group" -> true,
            "is-invalid"         -> lila.common.Captcha.isFailed(form)
          ),
          dataCheckUrl := routes.Main.captchaCheck(captcha.gameId)
        )(
          div(cls := "challenge")(
            views.html.board.bits.mini(
              FEN(GameLogic.Chess(), captcha.fenBoard),
              SGPlayer.fromP1(captcha.p1),
              variantKey = "standard"
            ) {
              div(
                dataMoves := safeJsonValue(Json.toJson(captcha.moves)),
                dataPlayable := 1
              )
            }
          ),
          div(cls := "captcha-explanation")(
            label(cls := "form-label")(
              if (captcha.p1) trans.p1CheckmatesInOneMove()
              else trans.p2CheckmatesInOneMove()
            ),
            br,
            br,
            trans.thisIsAChessCaptcha(),
            br,
            trans.clickOnTheBoardToMakeYourMove(),
            br,
            br,
            trans.help(),
            " ",
            a(title := trans.viewTheSolution.txt(), targetBlank, href := s"${url}#last")(url),
            div(cls := "result success text", dataIcon := "E")(trans.checkmate()),
            div(cls := "result failure text", dataIcon := "k")(trans.notACheckmate()),
            form3.hidden(form("move"))
          )
        )
      }
    )
}
