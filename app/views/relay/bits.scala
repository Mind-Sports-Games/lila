package views.html.relay

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*

object bits {

  @annotation.nowarn("msg=unused")
  def howToUse(implicit ctx: Context) =
    a(dataIcon := "", cls := "text", href := routes.Page.lonePage("broadcasts"))(
      "How to use PlayStrategy Broadcasts"
    )

  def jsI18n(implicit ctx: Context) =
    views.html.study.jsI18n() ++
      i18nJsObject(i18nKeys)

  val i18nKeys: List[lila.i18n.MessageKey] = {
    import trans.broadcast.*
    List(addRound, broadcastUrl, currentRoundUrl, currentGameUrl).map(_.key)
  }
}
