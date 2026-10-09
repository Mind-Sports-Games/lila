package views.html

import play.api.data.Form

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*

object dev {

  def settings(settings: List[lila.memo.SettingStore[?]])(implicit ctx: Context) = {
    val title = "Settings"
    views.html.base.layout(
      title = title,
      moreCss = cssTag("mod.misc")
    )(
      main(cls := "page-menu")(
        mod.menu("setting"),
        div(id := "settings", cls := "page-menu__content box box-pad")(
          h1(title),
          p("Tread lightly."),
          settings.map { s =>
            postForm(action := routes.Dev.settingsPost(s.id))(
              p(s.text | s.id),
              s.form.value match {
                case Some(v: Boolean) =>
                  div(
                    span(cls := "form-check-input")(form3.cmnToggle(s.id, "v", v))
                  )
                case v =>
                  input(
                    name  := "v",
                    value := (v match {
                      case None    => ""
                      case Some(x) => x.toString
                    })
                  )
              },
              submitButton(cls := "button button-empty", dataIcon := "E")
            )
          }
        )
      )
    )
  }

  def cli(form: Form[?], res: Option[String])(implicit ctx: Context) = {
    val title = "Command Line Interface"
    views.html.base.layout(
      title = title,
      moreCss = cssTag("mod.misc")
    ) {
      main(cls := "page-menu")(
        views.html.mod.menu("cli"),
        div(id := "dev-cli", cls := "page-menu__content box box-pad")(
          h1(title),
          p(
            "Run arbitrary lila commands.",
            br,
            "Only use if you know exactly what you're doing."
          ),
          res map { pre(_) },
          postForm(action := routes.Dev.cliPost)(
            form3.input(form("command"))(autofocus)
          ),
          h2("Command examples:"),
          // one line per command, or per group of commands sharing their words
          p(
            "( ): one choice, of words (a/b) or of whole forms (a | b). ",
            "{x}: a value to fill in. [ ]: may be left out."
          ),
          pre("""announce 10 minutes PlayStrategy will restart!
announce cancel
bot-vs-bot (status | (start/stop/restart) [{stream}])
change asset version
eval-cache drop standard 8/8/1k6/8/2K5/1P6/8/8 w - - 0 1
fishnet client (create {username} | (delete/enable/disable) {key})
gdpr erase {username} forever
patron (lifetime/month/remove) {username}
shield-leaderboards recalc
study library (pin/hide/reset) {id}
swiss update score {id}
(swiss/tournament) dq {username} {id}
tournament (feature/unfeature) {id}
uptime
""")
        )
      )
    }
  }
}
