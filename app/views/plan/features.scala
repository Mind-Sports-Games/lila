package views
package html.plan

import play.api.i18n.Lang

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

import controllers.routes

object features {

  def apply()(implicit ctx: Context) =
    views.html.base.layout(
      title = title,
      moreCss = cssTag("feature"),
      openGraph = lila.app.ui
        .OpenGraph(
          title = title,
          url = s"$netBaseUrl${routes.Plan.features.url}",
          description = "All of PlayStrategy features are free for all. We do it for the games!"
        )
        .some
    ) {
      main(cls := "box box-pad features")(
        table(
          //header(h1(dataIcon := "")("Website")),
          header(h1("Website")),
          tbody(
            tr(check)(
              strong("Zero ads, no tracking")
            ),
            tr(unlimited)(
              "Play and create ",
              a(href := routes.Tournament.home)("tournaments")
            ),
            tr(check)(
              "Tournament features including: ",
              a(href := routes.Page.lonePage("medley"))("medley"),
              ", ",
              a(href := routes.Page.lonePage("handicaps"))("handicapped"),
              ", ",
              a(href := s"${routes.Swiss.home}#bestofx")("best of X/play X")
            ),
            tr(unlimited)(
              "Play and create ",
              a(href := routes.Simul.home)("simultaneous exhibitions")
            ),
            tr(unlimited)(
              "Bullet, Blitz, Rapid, Classical and Correspondence games"
            ),
            tr(check)(
              "A variety of clock types; ",
              a(href := routes.Page.lonePage("clocks"))(
                "Fischer, Byoyomi, Bronstein, Simple Delay, Correspondence and unlimited"
              )
            ),
            tr(check)(
              "Standard chess and ",
              a(href := routes.Page.variantHome)("10 chess variants (Chess960, Crazyhouse, ...)")
            ),
            tr(unlimited)(
              "Over 30 different games/variants including draughts, othello, backgammon, go..."
            ),
            tr(unlimited)(
              "Play against ",
              a(href := routes.PlayApi.botOnline)("PlayStrategy Bots")
            ),
            tr(custom("35 per day"))(
              "Deep Stockfish 13+ server analysis on chess games"
            ),
            tr(unlimited)(
              "Instant local Stockfish 13+ analysis on chess games"
            ),
            tr(unlimited)(
              "FairyStockfish server analysis on shogi, xiangqi, othello, amazons and breakthrough games"
            ),
            tr(unlimited)("Cloud engine analysis (chess)"),
            tr(unlimited)("Learn from your mistakes"),
            tr(unlimited)(
              a(href := routes.Study.allDefault())(
                "Studies (shared and persistent analysis)"
              )
            ),
            // tr(unlimited)(
            //   a(href := "https://lichess.org/blog/VmZbaigAABACtXQC/chess-insights")(
            //     "Chess insights (detailed analysis of your play)"
            //   )
            // ),
            // tr(check)(
            //   a(href := routes.Learn.index)("All chess basics lessons")
            // ),
            // tr(unlimited)(
            //   a(href := routes.Puzzle.home)("Chess Tactics Puzzles")
            // ),
            // tr(unlimited)(
            //   a(href := routes.Puzzle.streak)("Chess Puzzle Streak")
            // ),
            // tr(unlimited)(
            //   a(href := routes.Storm.home)("Chess Puzzle Storm")
            // ),
            // tr(unlimited)(
            //   a(href := routes.Racer.home)("Chess Puzzle Racer")
            // ),
            tr(unlimited)(
              a(href := s"${routes.UserAnalysis.index}#explorer")("Chess Opening Explorer")
            ),
            tr(unlimited)(
              a(href := s"${routes.UserAnalysis.parseArg("QN4n1/6r1/3k4/8/b2K4/8/8/8_b_-_-")}#explorer")(
                "7-piece chess endgame tablebase"
              )
            ),
            tr(check)(
              "Download/Upload chess game as PGN"
            ),
            tr(check)(
              "Download supported games as SGF"
            ),
            // tr(unlimited)(
            //   a(href := routes.Search.index(1))("Advanced search"),
            //   " through PlayStrategy games library"
            // ),
            /*tr(unlimited)(
              a(href := routes.Video.index)("Chess video library")
            ),*/
            tr(check)(
              "Forum, teams, TV, messaging, friends, challenges"
            ),
            tr(check)(
              /*"Available in ",
              a(href := "https://crowdin.com/project/playstrategy")("80+ languages")*/
              "Available in 80+ languages"
            ),
            tr(check)(
              "Light/dark theme, custom boards, pieces and background"
            ),
            tr(check)(
              strong("New features to come!")
            )
          ),
          /*header(h1(dataIcon := "")("Mobile")),
          tbody(
            tr(unlimited)(
              "Online and offline games, with 8 variants"
            ),
            tr(unlimited)(
              a(href := routes.Tournament.home)("Arena tournaments")
            ),
            tr(check)(
              "Board editor and analysis board with Stockfish 12+"
            ),
            tr(unlimited)(
              a(href := routes.Puzzle.home)("Tactics puzzles")
            ),
            tr(check)(
              "Available in 80+ languages"
            ),
            tr(check)(
              "Light and dark theme, custom boards and pieces"
            ),
            tr(check)(
              "iPhone & Android phones and tablets, landscape support"
            ),
            tr(check)(
              strong("Zero ads, no tracking")
            ),
            tr(check)(
              strong("All features to come, forever")
            )
          ),*/
          header(h1("Support PlayStrategy")),
          tbody(cls := "support")(
            st.tr(
              th(
                "Contribute to PlayStrategy and",
                br,
                "get a cool looking Patron icon"
              ),
              td("-"),
              td(span(dataIcon := patronIconChar, cls := "is is-green text check")("Yes"))
            ),
            st.tr(cls := "price")(
              th,
              td(cls := "green")("$0"),
              td(a(href := routes.Plan.index, cls := "green button")("$5/month"))
            )
          )
        ),
        p(cls := "explanation")(
          strong("Yes, both accounts have the same features!"),
          br,
          "That is because PlayStrategy is built for the love of games.",
          br,
          "We believe every mind sports games player deserves the best, and so:",
          br,
          br,
          strong("All features are free for everybody!"),
          br,
          "If you love PlayStrategy, ",
          a(cls := "button", href := routes.Plan.index)("Support us with a Patron account!")
        )
      )
    }

  private def header(name: Frag)(implicit lang: Lang) =
    thead(
      st.tr(th(name), th(trans.patron.freeAccount()), th(trans.patron.playstrategyPatron()))
    )

  private val unlimited = span(dataIcon := "E", cls := "is is-green text unlimited")("Unlimited")

  private val check = span(dataIcon := "E", cls := "is is-green text check")("Yes")

  private def custom(str: String) = span(dataIcon := "E", cls := "is is-green text check")(str)

  private def all(content: Frag) = frag(td(content), td(content))

  private def tr(value: Frag)(text: Frag*) = st.tr(th(text), all(value))

  private val title = "PlayStrategy features"
}
