package views.html.tournament

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.i18n.VariantKeys
import lila.swiss.Swiss
import lila.tournament.Tournament

object msoHistory {

  type Edition = Either[Tournament, Swiss]

  // the admins, and the account the MSO runs its tournaments from
  def canRefresh(me: lila.user.User) =
    lila.security.Granter(_.Admin)(me) || me.id == views.html.library.bits.msoTeamId

  private def startsAt(e: Edition) = e.fold(_.startsAt, _.startsAt)

  def apply(editions: List[Edition])(implicit ctx: Context) = {
    val title = "Mind Sports Olympiad online tournaments — every MSO Grand Prix edition on PlayStrategy"
    views.html.base.layout(
      title = title,
      moreCss = frag(cssTag("slist"), cssTag("tournament.history")),
      canonicalPath = routes.Tournament.msoHistory.url.some,
      openGraph = lila.app.ui
        .OpenGraph(
          title = title,
          url = s"$netBaseUrl${routes.Tournament.msoHistory.url}",
          description =
            "Every online tournament the Mind Sports Olympiad has run on PlayStrategy, from the 2021 arenas to the Grand Prix swisses, grouped by year with their winners."
        )
        .some
    ) {
      main(cls := "page-small box box-pad")(
        h1("Mind Sports Olympiad online tournaments"),
        p(
          "Every online tournament of the ",
          a(href := routes.Page.lonePage("mind-sports-olympiad"))("Mind Sports Olympiad"),
          " on PlayStrategy: the 2021 arenas and the MSO Grand Prix since. New tournaments are announced in ",
          a(href := routes.Team.show(views.html.library.bits.msoTeamId))("the Mind Sports Olympiad group"),
          "."
        ),
        ctx.me.exists(canRefresh).option(
          postForm(cls := "mso-history__refresh", action := routes.Tournament.msoHistoryRefresh)(
            submitButton(cls := "button button-empty")("Refresh the list")
          )
        ),
        editions.groupBy(e => startsAt(e).getYear).toList.sortBy(-_._1).map { case (year, inYear) =>
          st.section(
            h2(year),
            div(cls := "arena-list")(
              table(cls := "slist slist-pad")(
                tbody(
                  inYear.map {
                    case Left(t)  => finishedList.withType(t, " Arena")
                    case Right(s) => swissRow(s)
                  }
                )
              )
            )
          )
        }
      )
    }
  }

  // arenas and swisses are listed together, and the end of the line under the name says which is which
  private def swissRow(s: Swiss)(implicit ctx: Context) =
    tr(cls := "paginated")(
      td(cls := "icon")(iconTag(s.variant.perfIcon)),
      td(cls := "header")(
        a(href := routes.Swiss.show(s.id.value))(
          span(cls := "name")(s.name),
          span(
            s.clock.show,
            " • ",
            VariantKeys.variantName(s.variant),
            " • ",
            if (s.settings.rated) trans.ratedTournament() else trans.casualTournament(),
            " • ",
            s"${s.actualNbRounds}r Swiss"
          )
        )
      ),
      td(cls := "date")(momentFromNow(s.startsAt)),
      td(cls := "players")(
        span(
          iconTag('g')(cls := "text"),
          userIdLink(s.winnerId, withOnline = false)
        ),
        span(trans.nbPlayers.plural(s.nbPlayers, s.nbPlayers.localize))
      )
    )
}
