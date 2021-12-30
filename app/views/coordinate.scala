package views.html

import play.api.libs.json.Json

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.String.html.safeJsonValue
import lila.pref.Pref.SGPlayer
import play.api.i18n.Lang

import controllers.routes

object coordinate {

  def home(scoreOption: Option[lila.coordinate.Score])(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.coordinates.coordinateTraining.txt(),
      moreCss = cssTag("coordinate"),
      moreJs = jsModule("coordinate"),
      openGraph = lila.app.ui
        .OpenGraph(
          title = "Chess board coordinates trainer",
          url = s"$netBaseUrl${routes.Coordinate.home.url}",
          description =
            "Knowing the chessboard coordinates is a very important chess skill. A square name appears on the board and you must click on the correct square."
        )
        .some,
      zoomable = true,
      playing = true
    )(
      main(
        id := "trainer",
        cls := "coord-trainer training init",
        attr("data-sgPlayer-pref") := ctx.pref.coordSGPlayerName,
        attr("data-score-url") := ctx.isAuth.option(routes.Coordinate.score.url)
      )(
        div(cls := "coord-trainer__side")(
          div(cls := "box")(
            h1(trans.coordinates.coordinates()),
            if (ctx.isAuth) scoreOption.map { score =>
              div(cls := "scores")(scoreCharts(score))
            }
          ),
          form(cls := "sgPlayer buttons", action := routes.Coordinate.sgPlayer, method := "post")(
            st.group(cls := "radio")(
              List(SGPlayer.BLACK, SGPlayer.RANDOM, SGPlayer.WHITE).map { id =>
                div(
                  input(
                    tpe := "radio",
                    st.id := s"coord_sgPlayer_$id",
                    name := "sgPlayer",
                    value := id,
                    (id == ctx.pref.coordSGPlayer) option checked
                  ),
                  label(`for` := s"coord_sgPlayer_$id", cls := s"sgPlayer sgPlayer_$id")(i)
                )
              }
            )
          ),
          div(cls := "box current-status")(
            h1(trans.storm.score()),
            div(cls := "coord-trainer__score")(0)
          ),
          div(cls := "box current-status")(
            h1(trans.time()),
            div(cls := "coord-trainer__timer")(30.0)
          )
        ),
        div(cls := "coord-trainer__board main-board variant-standard")(
          div(cls := "next_coord", id := "next_coord0"),
          div(cls := "next_coord", id := "next_coord1"),
          chessgroundBoard
        ),
        div(cls := "coord-trainer__table")(
          div(cls := "explanation")(
            p(trans.coordinates.knowingTheChessBoard()),
            ul(
              li(trans.coordinates.mostChessCourses()),
              li(trans.coordinates.talkToYourChessFriends()),
              li(trans.coordinates.youCanAnalyseAGameMoreEffectively())
            ),
            p(trans.coordinates.aSquareNameAppears())
          ),
          button(cls := "start button button-fat")(trans.coordinates.startTraining())
        ),
        div(cls := "coord-trainer__progress")(div(cls := "progress_bar"))
      )
    )

  def scoreCharts(score: lila.coordinate.Score)(implicit ctx: Context) =
    frag(
      List(
        (trans.coordinates.averageScoreAsP1X, score.p1),
        (trans.coordinates.averageScoreAsP2X, score.p2)
      ).map { case (averageScoreX, s) =>
        div(cls := "chart_container")(
          s.nonEmpty option frag(
            p(averageScoreX(raw(s"""<strong>${"%.2f".format(s.sum.toDouble / s.size)}</strong>"""))),
            div(cls := "user_chart", attr("data-points") := safeJsonValue(Json toJson s))
          )
        )
      }
    )
}
