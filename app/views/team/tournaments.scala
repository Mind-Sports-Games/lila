package views.html.team

import play.api.i18n.Lang

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.app.mashup.TeamInfo
import lila.i18n.VariantKeys

import controllers.routes

object tournaments {

  def page(t: lila.team.Team, tours: TeamInfo.PastAndNext)(implicit ctx: Context) = {
    views.html.base.layout(
      title = s"${t.name} • ${trans.tournaments.txt()}",
      moreCss = cssTag("team"),
      wrapClass = "full-screen-force"
    ) {
      main(
        div(cls := "box")(
          h1(
            views.html.team.bits.link(t),
            " • ",
            trans.tournaments()
          ),
          div(cls := "team-events team-tournaments team-tournaments--both")(
            div(cls := "team-tournaments__next")(
              h2("Upcoming tournaments"),
              table(cls := "slist slist-pad slist-invert")(
                renderList(tours.next)
              )
            ),
            div(cls := "team-tournaments__past")(
              h2("Completed tournaments"),
              table(cls := "slist slist-pad")(
                renderList(tours.past)
              )
            )
          )
        )
      )
    }
  }

  def renderList(tours: List[TeamInfo.AnyTour])(implicit ctx: Context) =
    tbody(
      tours map { any =>
        tr(
          cls := List(
            "enterable" -> any.isEnterable,
            "soon"      -> any.isNowOrSoon
          )
        )(
          td(cls := "icon")(iconTag(any.any.fold(tournamentIconChar, views.html.swiss.bits.iconChar))),
          td(cls := "header")(
            any.any.fold(
              t =>
                a(href := routes.Tournament.show(t.id))(
                  span(cls := "name")(t.name()),
                  span(cls := "setup")(
                    t.clock.show,
                    " • ",
                    if (t.variant.exotic) VariantKeys.variantName(t.variant) else t.perfType.trans,
                    t.position.isDefined option frag(" • ", trans.thematic()),
                    " • ",
                    if (t.handicapped) trans.handicappedTournament()
                    else t.mode.fold(trans.casualTournament, trans.ratedTournament)(),
                    " • ",
                    t.durationString
                  )
                ),
              s =>
                a(href := routes.Swiss.show(s.id.value))(
                  span(cls := "name")(s.name),
                  span(cls := "setup")(
                    s.clock.show,
                    " • ",
                    if (s.isMedley) trans.medley.txt()
                    else if (s.variant.exotic) VariantKeys.variantName(s.variant)
                    else s.perfType.trans,
                    " • ",
                    if (s.settings.handicapped) trans.handicappedTournament()
                    else if (s.settings.mcmahon) trans.mcmahon()
                    else if (s.settings.rated) trans.ratedTournament()
                    else trans.casualTournament()
                  )
                )
            )
          ),
          td(cls := "infos")(
            any.any.fold(
              t =>
                frag(
                  t.teamBattle map { battle =>
                    frag(battle.teams.size, " teams battle")
                  } getOrElse "Inner team",
                  br,
                  renderStartsAt(any)
                ),
              s =>
                frag(
                  s.settings.nbRounds,
                  " rounds swiss",
                  br,
                  renderStartsAt(any)
                )
            )
          ),
          td(cls := "text", dataIcon := "r")(any.nbPlayers.localize)
        )
      }
    )

  private def renderStartsAt(any: TeamInfo.AnyTour)(implicit lang: Lang): Frag =
    if (any.isEnterable && any.startsAt.isBeforeNow) trans.playingRightNow()
    else momentFromNowOnce(any.startsAt)
}
