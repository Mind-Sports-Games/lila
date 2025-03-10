package views.html.tournament

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.i18n.{ I18nKeys => trans, VariantKeys }
import lila.tournament.Tournament
import strategygames.variant.Variant

import controllers.routes

object bits {

  def notFound()(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.tournamentNotFound.txt()
    ) {
      main(cls := "page-small box box-pad")(
        h1(trans.tournamentNotFound()),
        p(trans.tournamentDoesNotExist()),
        p(trans.tournamentMayHaveBeenCanceled()),
        br,
        br,
        a(href := routes.Tournament.home)(trans.returnToTournamentsHomepage())
      )
    }

  def enterable(tours: List[Tournament]) =
    table(cls := "tournaments")(
      tours map { tour =>
        tr(
          td(cls := "name")(
            a(cls := "text", dataIcon := tournamentIconChar(tour), href := routes.Tournament.show(tour.id))(
              tour.name
            )
          ),
          tour.schedule.fold(td) { s =>
            td(momentFromNow(s.at))
          },
          td(tour.durationString),
          td(dataIcon := "r", cls := "text")(tour.nbPlayers)
        )
      }
    )

  def userPrizeDisclaimer(ownerId: lila.user.User.ID) =
    !env.prizeTournamentMakers.get().value.contains(ownerId) option
      div(cls := "tour__prize")(
        "This tournament is NOT organized by PlayStrategy.",
        br,
        "If it has prizes, PlayStrategy is NOT responsible for paying them."
      )

  def medleyGames(
      variants: List[Variant],
      tourLength: Int,
      medleyInterval: Int,
      pairingsClosedMins: Double
  )(implicit ctx: Context) =
    div(cls := "medley__info")(
      table(cls := "medley__rounds")(
        tbody(
          tr(
            td(cls := "medley__table__time__col")("Time Remaining"),
            td("Variant")
          ),
          List
            .tabulate((tourLength / medleyInterval) + 1)(n => tourLength - (n * medleyInterval))
            .filter(_ > pairingsClosedMins)
            .zip(variants)
            .map { case (i, v) =>
              tr(
                td(cls := "medley__table__time__col")(s"${i} minutes"),
                td(a(href := routes.Page.variant(v.key))(VariantKeys.variantName(v)))
              )
            }
        )
      )
    )

  def jsI18n(implicit ctx: Context) = i18nJsObject(i18nKeys)

  private val i18nKeys = List(
    trans.standing,
    trans.starting,
    trans.tournamentIsStarting,
    trans.youArePlaying,
    trans.medleyVariantsXMinutesEach,
    trans.nowPairingX,
    trans.standByX,
    trans.standByXForY,
    trans.tournamentPairingsAreNowClosed,
    trans.join,
    trans.withdraw,
    trans.joinTheGame,
    trans.signIn,
    trans.averageElo,
    trans.gamesPlayed,
    trans.nbPlayers,
    trans.winRate,
    trans.berserkRate,
    trans.performance,
    trans.tournamentComplete,
    trans.movesPlayed,
    trans.turnsPlayed,
    trans.playerIndexWins,
    trans.draws,
    trans.nextXTournament,
    trans.averageOpponent,
    trans.ratedTournament,
    trans.casualTournament,
    trans.password,
    trans.arena.viewAllXTeams
  ).map(_.key)
}
