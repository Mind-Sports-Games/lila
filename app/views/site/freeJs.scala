package views
package html.site

import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object freeJs {

  private lazy val agpl = a(href := "https://www.gnu.org/licenses/agpl-3.0.en.html")("AGPL-3.0+")

  private def github(path: String) =
    a(href := s"https://github.com/Mind-Sports-Games/lila/tree/master/$path")(path)

  private val uiModules = List(
    "analyse",
    "challenge",
    "chat",
    "cli",
    "dasher",
    "dgt",
    "draughts",
    "draughtsground",
    "draughtsround",
    "editor",
    "lobby",
    "msg",
    "notify",
    "palantir",
    "racer",
    "round",
    "serviceWorker",
    "simul",
    "site",
    "speech",
    "swiss",
    "tournament",
    "tournamentCalendar",
    "tournamentSchedule"
  );

  private val renames = Map(
    "analyse"            -> "analysisBoard",
    "tournamentCalendar" -> "tournament.calendar",
    "tournamentSchedule" -> "tournament.schedule"
  )

  def apply(): Frag =
    frag(
      div(cls := "box__top")(
        h1("JavaScript modules")
      ),
      p(cls := "box__pad")(
        "Here are all frontend modules from ",
        a(href := "https://github.com/Mind-Sports-Games/lila/tree/master/ui")("Mind-Sports-Games/lila ui"),
        " in ",
        a(href := "https://www.gnu.org/licenses/javascript-labels.en.html")("Web Labels"),
        " compatible format:"
      ),
      table(id := "jslicense-labels1", cls := "slist slist-pad")(
        thead(
          tr(List("Script File", "License", "Source Code").map(th(_)))
        ),
        tbody(
          uiModules map { module =>
            val name = renames.getOrElse(module, module)
            tr(
              td(a(href := jsUrl(name))(s"$name.js")),
              td(agpl),
              td(github(s"ui/$module/src"))
            )
          }
        )
      )
    )
}
