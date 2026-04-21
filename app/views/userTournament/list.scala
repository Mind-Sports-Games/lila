package views.html
package userTournament

import play.api.i18n.Lang

import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.common.paginator.Paginator
import lila.user.User
import lila.i18n.VariantKeys

object list {

  def apply(
      u: User,
      path: String,
      pager: Paginator[lila.tournament.LeaderboardApi.TourEntry],
      count: String,
      shieldLeaderboard: Boolean = false
  )(implicit lang: Lang) =
    if pager.nbResults == 0 then div(cls := "box-pad")(u.username, " hasn't played in any tournament yet!")
    else
      div(cls := "tournament-list")(
        table(cls := "slist")(
          thead(
            tr(
              th(cls := "count")(count),
              th(
                h1(
                  userLink(u, withOnline = true),
                  if shieldLeaderboard then a(href := routes.Tournament.shields)(" shield leaderboard"),
                  " tournaments"
                )
              ),
              th("Games"),
              th("Points"),
              th("Rank")
            )
          ),
          tbody(cls := "infinite-scroll")(
            pager.currentPageResults.map { e =>
              tr(cls := List("paginated" -> true, "scheduled" -> e.tour.isScheduled))(
                td(cls := "icon")(iconTag(tournamentIconChar(e.tour))),
                td(cls := "header")(
                  a(href := routes.Tournament.show(e.tour.id))(
                    span(cls := "name")(e.tour.name()),
                    span(cls := "setup")(
                      e.tour.clock.show,
                      " • ",
                      if e.tour.variant.exotic then VariantKeys.variantName(e.tour.variant)
                      else e.tour.perfType.trans,
                      " • ",
                      momentFromNow(e.tour.startsAt)
                    )
                  )
                ),
                td(cls := "games")(e.entry.nbGames),
                td(cls := "score")(if shieldLeaderboard then e.entry.metaPoints else e.entry.score),
                td(cls := "rank")(strong(e.entry.rank), " / ", e.tour.nbPlayers)
              )
            },
            pagerNextTable(pager, np => routes.UserTournament.path(u.username, path, np).url)
          )
        )
      )
}
