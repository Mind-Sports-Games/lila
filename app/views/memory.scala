package views.html

import play.api.libs.json.Json

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.String.html.safeJsonValue

import controllers.routes

//TODO add text to trans

object memory {

  def home(implicit ctx: Context) =
    views.html.base.layout(
      title = "Memory Game!",
      moreCss = cssTag("memory"),
      moreJs = jsModule("memory"),
      openGraph = lila.app.ui
        .OpenGraph(
          title = "Memory trainer",
          url = s"$netBaseUrl${routes.Memory.home.url}",
          description = "Improve your memory by trying to match pairs of pieces."
        )
        .some,
      zoomable = true
    )(
      main(
        id := "memory-app",
        cls := "memory-app init"
      )(
        h1(cls := "memory-title")("Find the matching icons!"),
        div(cls := "memory-grid")(
          div(cls := "memory-card", attr("data-framework") := "1")(
            i(cls := "image front-face", dataIcon := "{"),
            img(cls := "back-face", src := "/assets/piece/xiangqi/2dhanzi/Bbackface.svg")
          ),
          div(cls := "memory-card", attr("data-framework") := "1")(
            i(cls := "image front-face", dataIcon := "{"),
            img(cls := "back-face", src := "/assets/piece/xiangqi/2dhanzi/Bbackface.svg")
          ),
          div(cls := "memory-card", attr("data-framework") := "2")(
            i(cls := "image front-face", dataIcon := "T"),
            img(cls := "back-face", src := "/assets/piece/xiangqi/2dhanzi/Bbackface.svg")
          ),
          div(cls := "memory-card", attr("data-framework") := "2")(
            i(cls := "image front-face", dataIcon := "T"),
            img(cls := "back-face", src := "/assets/piece/xiangqi/2dhanzi/Bbackface.svg")
          ),
          div(cls := "memory-card", attr("data-framework") := "3")(
            i(cls := "image front-face", dataIcon := ")"),
            img(cls := "back-face", src := "/assets/piece/xiangqi/2dhanzi/Bbackface.svg")
          ),
          div(cls := "memory-card", attr("data-framework") := "3")(
            i(cls := "image front-face", dataIcon := ")"),
            img(cls := "back-face", src := "/assets/piece/xiangqi/2dhanzi/Bbackface.svg")
          ),
          div(cls := "memory-card", attr("data-framework") := "4")(
            i(cls := "image front-face", dataIcon := "C"),
            img(cls := "back-face", src := "/assets/piece/xiangqi/2dhanzi/Bbackface.svg")
          ),
          div(cls := "memory-card", attr("data-framework") := "4")(
            i(cls := "image front-face", dataIcon := "C"),
            img(cls := "back-face", src := "/assets/piece/xiangqi/2dhanzi/Bbackface.svg")
          ),
          div(cls := "memory-card", attr("data-framework") := "5")(
            i(cls := "image front-face", dataIcon := "+"),
            img(cls := "back-face", src := "/assets/piece/xiangqi/2dhanzi/Bbackface.svg")
          ),
          div(cls := "memory-card", attr("data-framework") := "5")(
            i(cls := "image front-face", dataIcon := "+"),
            img(cls := "back-face", src := "/assets/piece/xiangqi/2dhanzi/Bbackface.svg")
          ),
          div(cls := "memory-card", attr("data-framework") := "6")(
            i(cls := "image front-face", dataIcon := ";"),
            img(cls := "back-face", src := "/assets/piece/xiangqi/2dhanzi/Bbackface.svg")
          ),
          div(cls := "memory-card", attr("data-framework") := "6")(
            i(cls := "image front-face", dataIcon := ";"),
            img(cls := "back-face", src := "/assets/piece/xiangqi/2dhanzi/Bbackface.svg")
          ),
          div(cls := "memory-card", attr("data-framework") := "7")(
            i(cls := "image front-face", dataIcon := "("),
            img(cls := "back-face", src := "/assets/piece/xiangqi/2dhanzi/Bbackface.svg")
          ),
          div(cls := "memory-card", attr("data-framework") := "7")(
            i(cls := "image front-face", dataIcon := "("),
            img(cls := "back-face", src := "/assets/piece/xiangqi/2dhanzi/Bbackface.svg")
          ),
          div(cls := "memory-card", attr("data-framework") := "8")(
            i(cls := "image front-face", dataIcon := "@"),
            img(cls := "back-face", src := "/assets/piece/xiangqi/2dhanzi/Bbackface.svg")
          ),
          div(cls := "memory-card", attr("data-framework") := "8")(
            i(cls := "image front-face", dataIcon := "@"),
            img(cls := "back-face", src := "/assets/piece/xiangqi/2dhanzi/Bbackface.svg")
          )
        ),
        div(cls := "moves", id := "moves")("Moves: 0"),
        div(cls := "memory-buttons")(
          button(cls := "start button button-fat")("Play Again?")
        )
      )
    )

}
