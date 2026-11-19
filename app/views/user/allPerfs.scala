package views.html.user

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.user.User

object allPerfs {

  def apply(u: User, rankMap: lila.rating.UserRankMap, ratingChart: Option[String])(implicit ctx: Context) =
    views.html.base.layout(
      title = s"${u.username} - ${trans.perfStat.allGamesRatingHistory.txt()}",
      robots = false,
      moreJs = frag(
        jsModule("user"),
        ratingChart.map { rc =>
          embedJsUnsafeLoadThen(
            s"playstrategy.lazyChart('.rating-history-container','chart.ratingHistory',()=>playstrategy.ratingHistoryChart($rc))"
          )
        }
      ),
      moreCss = cssTag("perf-stat")
    ) {
      main(cls := "page-menu")(
        st.aside(cls := "page-menu__menu")(show.side(u, rankMap, none, allGamesActive = true)),
        div(cls := "page-menu__content box perf-stat all-perfs")(
          div(cls := "box__top")(
            h1(
              a(href := routes.User.show(u.username))(u.username),
              span(trans.perfStat.allGamesRatingHistory())
            )
          ),
          ratingChart.isDefined.option(views.html.user.bits.ratingHistoryContainer)
        )
      )
    }
}
