package views.html.search

import play.api.data.Form

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.common.paginator.Paginator

object index {

  import trans.search.*

  def apply(form: Form[?], paginator: Option[Paginator[lila.game.Game]] = None, nbGames: Long)(implicit
      ctx: Context
  ) = {
    val commons = bits.of(form)
    views.html.base.layout(
      title = searchInXGames.txt(nbGames.localize, nbGames),
      moreJs = frag(
        jsModule("gameSearch"),
        infiniteScrollTag
      ),
      moreCss = cssTag("search")
    ) {
      main(cls := "box page-small search")(
        h1(advancedSearch()),
        st.form(
          noFollow,
          cls    := "box__pad search__form",
          action := s"${routes.Search.index()}#results",
          method := "GET"
        )(commons.dataReqs)(
          globalError(form),
          table(
            tr(
              th(label(trans.players())),
              td(cls := "usernames")(List("a", "b").map { p =>
                div(cls := "half")(form3.input(form("players")(p))(tpe := "text"))
              })
            ),
            commons.playerIndexs(hide = true),
            commons.winner(hide = true),
            commons.loser(hide = true),
            commons.rating,
            commons.hasAi,
            commons.aiLevel,
            commons.source,
            commons.perf,
            commons.mode,
            commons.turns,
            commons.duration,
            commons.clockTime,
            commons.clockIncrement,
            commons.status,
            commons.winnerPlayerIndex,
            commons.date,
            commons.sort,
            commons.analysed,
            tr(
              th,
              td(cls := "action")(
                submitButton(cls := "button")(trans.search.search()),
                div(cls := "wait")(
                  spinner,
                  searchInXGames(nbGames.localize)
                )
              )
            )
          )
        ),
        div(cls := "search__result", id := "results")(
          paginator.map { pager =>
            val permalink =
              a(cls := "permalink", href := routes.Search.index(), noFollow)("Permalink")
            if pager.nbResults > 0 then
              frag(
                div(cls := "search__status box__pad")(
                  strong(xGamesFound(pager.nbResults.localize, pager.nbResults)),
                  " • ",
                  permalink
                ),
                div(cls := "search__rows infinite-scroll")(
                  views.html.game.widgets(pager.currentPageResults),
                  pagerNext(pager, np => routes.Search.index(np).url)
                )
              )
            else
              div(cls := "search__status box__pad")(
                strong(xGamesFound(0)),
                " • ",
                permalink
              )
          }
        )
      )
    }
  }
}
