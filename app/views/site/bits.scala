package views.html.site

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import controllers.routes

object bits {

  def getFishnet()(implicit ctx: Context) =
    views.html.base.layout(
      title = "fishnet API key request",
      csp = defaultCsp.withGoogleForm.some
    ) {
      main(
        iframe(
          src := "https://docs.google.com/forms/d/e/1FAIpQLSeGgDHgWGP0uobQknF92eCMXqebyNBTyzJoJqbeGjRezlbWOw/viewform?embedded=true",
          style := "width:100%;height:1400px",
          st.frameborder := 0
        )(spinner)
      )
    }

//<script src="https://cdn.jsdelivr.net/npm/redoc@next/bundles/redoc.standalone.js"></script>
//<script src="https://cdn.redocly.com/redoc/v2.5.0/bundles/redoc.standalone.js"></script>

//    <meta http-equiv="Content-Security-Policy" content="default-src 'self'; style-src 'unsafe-inline'; script-src https://cdn.jsdelivr.net blob:; child-src blob:; connect-src https://raw.githubusercontent.com; img-src data: https://lichess.org https://lichess1.org;">
//    <meta http-equiv="Content-Security-Policy" content="default-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-eval' https://cdn.redocly.com blob:; child-src blob:; connect-src https://raw.githubusercontent.com; img-src data: https://playstrategy.org https://assets.playstrategy.org;">

  def api =
    raw(
      """<!DOCTYPE html>
<html>
  <head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Security-Policy" content="default-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-eval' https://cdn.redocly.com blob:; child-src blob:; connect-src https://raw.githubusercontent.com; img-src data: https://playstrategy.org https://assets.playstrategy.org;">
    <title>PlayStrategy.org API reference</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>body { margin: 0; padding: 0; }</style>
  </head>
  <body>
    <redoc spec-url="https://raw.githubusercontent.com/Mind-Sports-Games/api/master/doc/specs/playstrategy-api.yaml"></redoc>
    <script src="https://cdn.redocly.com/redoc/v2.5.0/bundles/redoc.standalone.js"></script>
  </body>
</html>"""
    )

  def errorPage(implicit ctx: Context) =
    views.html.base.layout(
      title = "Internal server error"
    ) {
      main(cls := "page-small box box-pad")(
        h1("Something went wrong on this page"),
        p(
          "If the problem persists, please ",
          a(href := s"${routes.Main.contact}#help-error-page")("report the bug"),
          "."
        )
      )
    }

  def ghost(implicit ctx: Context) =
    views.html.base.layout(
      moreCss = cssTag("ghost"),
      title = "Deleted user"
    ) {
      main(cls := "page-small box box-pad page")(
        h1("Deleted user"),
        div(
          p("This player account is gone!"),
          p("Nothing to see here, move along.")
        )
      )
    }
}
