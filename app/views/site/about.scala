package views
package html.site

import controllers.routes
import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object about {

  def apply()(implicit ctx: Context) =
    page.layout(
      title = trans.about.txt() + " playstrategy.org",
      active = "about",
    ) {
      div(cls := "small-page box box-pad")(
        h1(cls := "playstrategy_title")(trans.about.txt() + " playstrategy.org"),
        p(
          trans.aboutUs(
            a(href := "https://msoworld.com/about/")("Mind Sports Olympiad"),
            a(href := "https://lichess.org/")("lichess.org")
          )
        )
      )
    }
    
}
