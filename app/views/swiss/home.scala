package views.html.swiss

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.swiss.{ FeaturedSwisses, Swiss }
import lila.i18n.VariantKeys

import controllers.routes

object home {

  def apply(featured: FeaturedSwisses)(implicit ctx: Context) =
    views.html.base.layout(
      title = "Swiss tournaments",
      moreCss = cssTag("swiss.home")
    ) {
      main(cls := "page-small box box-pad page swiss-home")(
        h1("Swiss tournaments"),
        renderList("Now playing")(featured.started),
        renderList("Starting soon")(featured.created),
        div(cls := "swiss-home__infos")(
          div(cls := "wiki")(
            iconTag(""),
            p(
              "In a Swiss tournament ",
              a(href := "https://en.wikipedia.org/wiki/Swiss-system_tournament")("(wiki)"),
              ", each competitor does not necessarily play all other entrants. Competitors meet one-on-one in each round and are paired using a set of rules designed to ensure that each competitor plays opponents with a similar running score, but not the same opponent more than once. The winner is the competitor with the highest aggregate points earned in all rounds. All competitors play in each round unless there is an odd number of players."
            )
          ),
          div(cls := "team")(
            iconTag("f"),
            p(
              "Swiss tournaments can only be created by team leaders, and can only be played by team members.",
              br,
              a(href := routes.Team.home())("Join or create a team"),
              " to start playing in swiss tournaments."
            )
          ),
          comparison,
          div(id := "faq")(faq)
        )
      )
    }

  private def renderList(name: String)(swisses: List[Swiss])(implicit ctx: Context) =
    table(cls := "slist swisses")(
      thead(tr(th(colspan := 4)(name))),
      tbody(
        swisses map { s =>
          tr(
            td(cls := "icon")(iconTag(bits.iconChar(s))),
            td(cls := "header")(
              a(href := routes.Swiss.show(s.id.value))(
                span(cls := "name")(s.name),
                trans.by(span(cls := "team")(teamIdToName(s.teamId)))
              )
            ),
            td(cls := "infos")(
              span(cls := "rounds")(
                s.isStarted option frag(s.round.value, " / "),
                s.settings.nbRounds,
                " rounds",
                if (s.settings.isBestOfX) {
                  s" (best of ${s.settings.nbGamesPerRound} games"
                } else if (s.settings.isPlayX) {
                  s" (${s.settings.nbGamesPerRound} games per round"
                },
                if (s.settings.isMatchScore)
                  " using match score",
                if (s.settings.isBestOfX || s.settings.isPlayX) ")"
                else ""
              ),
              span(cls := "setup")(
                s.clock.show,
                " • ",
                if (s.isMedley) trans.medley.txt()
                else if (s.variant.exotic)
                  s.settings.backgammonPoints.fold("")(p => s"${p}pt ") + VariantKeys.variantName(s.variant)
                else s.perfType.trans,
                " • ",
                if (s.settings.handicapped) trans.handicappedTournament()
                else if (s.settings.mcmahon) trans.mcmahon()
                else if (s.settings.rated) trans.ratedTournament()
                else trans.casualTournament()
              )
            ),
            td(
              momentFromNow(s.startsAt),
              br,
              span(cls := "players text", dataIcon := "r")(s.nbPlayers.localize)
            )
          )
        }
      )
    )

  private lazy val comparison = table(cls := "comparison slist")(
    thead(
      tr(
        th("Comparison"),
        th(strong("Arena"), " tournaments"),
        th(strong("Swiss"), " tournaments")
      )
    ),
    tbody(
      tr(
        th("Duration of the tournament"),
        td("Predefined duration in minutes"),
        td("Predefined max rounds, but duration unknown")
      ),
      tr(
        th("Number of games"),
        td("As many as can be played in the allotted duration"),
        td("Decided in advance, same for all players")
      ),
      tr(
        th("Pairing system"),
        td("Any available opponent with similar ranking"),
        td("Best pairing based on points and tiebreaks")
      ),
      tr(
        th("Pairing wait time"),
        td("Fast: doesn't wait for all players"),
        td("Slow: waits for all players")
      ),
      tr(
        th("Identical pairing"),
        td("Possible, but not consecutive"),
        td("Forbidden")
      ),
      tr(
        th("Late join"),
        td("Yes"),
        td("Yes until more than half the rounds have started")
      ),
      tr(
        th("Pause"),
        td("Yes"),
        td("Yes but might reduce the number of rounds")
      ),
      tr(
        th("Streaks & Berserk"),
        td("Yes"),
        td("No")
      ),
      tr(
        th("Similar to OTB tournaments"),
        td("No"),
        td("Yes")
      ),
      tr(
        th("Unlimited and free"),
        td("Yes"),
        td("Yes")
      )
    )
  )

  private lazy val faq = frag(
    div(cls := "faq")(
      i("?"),
      p(
        strong("When to use swiss tournaments instead of arenas?"),
        "In a swiss tournament, all participants play the same number of games, and can only play each other once.",
        br,
        "It can be a good option for clubs and official tournaments."
      )
    ),
    div(cls := "faq")(
      i("?"),
      p(
        strong("How are points calculated?"),
        "A win is worth one point, a draw is a half point, and a loss is zero points.",
        br,
        "When a player can't be paired during a round, they receive a bye worth one point."
      )
    ),
    div(cls := "faq", id := "bestofx")(
      i("?"),
      p(
        strong("What do the Swiss options 'best of x' and 'play x' do?"),
        "In a swiss tournament there is normally 1 game per round. If using play x, there will instead be x games per round, all played against the same opponent.",
        br,
        "The winner will be the player who won the most games and the final result will be recorded as normal e.g. 1-0.",
        br,
        "In best of x, players will also play x rounds, but if one player gets too far ahead then the remaining games will not get played."
      )
    ),
    div(cls := "faq", id := "faqMatchScore")(
      i("?"),
      p(
        strong("In a Swiss tournament what does it mean by using match score?"),
        "In a swiss tournament, if there is more than 1 game per round (see above), then the match score between the players is used instead of the usual scoring system.",
        br,
        "For example, if you won 2.5-1.5 (in a best of 4), you would score 2.5 (and opponent 1.5) instead of the usual 1 (and opponent 0)",
        br,
        "A bye will score the maximum points possible when using match score."
      )
    ),
    div(cls := "faq")(
      i("?"),
      p(
        strong("How are tiebreaks calculated?"),
        "From April 2022, the primary tiebreaker is the ",
        a(
          href := "https://en.wikipedia.org/wiki/Buchholz_system"
        )("Buchholz System [BH]"),
        " in full (without dropping any opponent scores). Put simply, this is the sum of all opponents tournament scores. Byes and opponents who are absent for rounds are handled as per FIDE guidelines.",
        br,
        "The secondary tiebreaker is the ",
        a(
          href := "https://en.wikipedia.org/wiki/Tie-breaking_in_Swiss-system_tournaments#Sonneborn%E2%80%93Berger_score"
        )("Sonneborn–Berger score [SB]"),
        ". Put simply, this is calculated by adding the scores of every opponent the player beats and half of the score of every opponent the player draws.",
        br,
        "The Sonneborn-Berger score is the primary tiebreaker for any Swiss tournaments run before April 2022."
      )
    ),
    div(cls := "faq")(
      i("?"),
      p(
        strong("How are pairings decided?"),
        "With the ",
        a(
          href := "https://en.wikipedia.org/wiki/Swiss-system_tournament#Dutch_system"
        )("Dutch system"),
        ", implemented by ",
        a(href := "https://github.com/BieremaBoyzProgramming/bbpPairings")("bbPairings"),
        " in accordance with the ",
        a(href := "https://handbook.fide.com/chapter/C0403")("FIDE handbook"),
        "."
      )
    ),
    div(cls := "faq")(
      i("?"),
      p(
        strong("What happens if the tournament has more rounds than players?"),
        "When all possible pairings have been played, the tournament will be ended and a winner declared."
      )
    ),
    div(cls := "faq")(
      i("?"),
      p(
        strong("Why is it restricted to teams?"),
        "Swiss tournaments were not designed for online chess. They demand punctuality, dedication and patience from players.",
        br,
        "We think these conditions are more likely to be met within a team than in global tournaments."
      )
    ),
    div(cls := "faq")(
      i("?"),
      p(
        strong("How many byes can a player get?"),
        "A player gets a bye of one point every time the pairing system can't find a pairing for them.",
        br,
        "Additionally, a single bye of half a point is attributed when a player late-joins a tournament."
      )
    ),
    div(cls := "faq")(
      i("?"),
      p(
        strong("What happens if a player doesn't play a game?"),
        "Their clock will tick, they will flag, and lose the game.",
        br,
        "Then the system will withdraw the player from the tournament, so they don't lose more games.",
        br,
        "They can re-join the tournament at any time."
      )
    ),
    div(cls := "faq")(
      i("?"),
      p(
        strong("Can players late-join?"),
        "Yes, until more than half the rounds have started; for example in a 11-rounds swiss players can join before round 6 starts and in a 12-rounds before round 7 starts.",
        br
      )
    ),
    div(cls := "faq")(
      i("?"),
      p(
        strong("Will swiss replace arena tournaments?"),
        "No. They're complementary features."
      )
    ),
    div(cls := "faq")(
      i("?"),
      p(
        strong("What about Round Robin?"),
        "We'd like to add it, but unfortunately Round Robin doesn't work online.",
        br,
        "The reason is that it has no fair way of dealing with people leaving the tournament early. ",
        "We cannot expect that all players will play all their games in an online event. ",
        "It just won't happen, and as a result most Round Robin tournaments would be flawed and unfair, ",
        "which defeats their very reason to exist.",
        br,
        "The closest you can get to Round Robin online is to play a Swiss tournament with a very high ",
        "number of rounds. Then all possible pairings will be played before the tournament ends."
      )
    ),
    div(cls := "faq", id := "handicaps")(
      i("?"),
      p(
        strong("What is a Handicapped Style Tournament?"),
        "A Handicapped style tournament provides an environment to play games against stronger or weaker opponents on a level footing. ",
        "To do this the initial starting fen is changed to reflect the strength difference between the two players.",
        br,
        "For more information see our ",
        a(href := routes.Page.loneBookmark("handicaps"))("handicaps page.")
      )
    ),
    div(cls := "faq", id := "mcmahon")(
      i("?"),
      p(
        strong("What is a McMahon Style Tournament?"),
        "A McMahon style tournament is typical in European Go tournaments, and helps reduce the probability of a very strong player playing against ",
        "a very weak player in the initial rounds.",
        br,
        "To create better pairings, an initial score (MMS) is given to players based on their grade and if they are above a cutoff value. ",
        "For example, a cutoff value of 3k (~1800), gives all players at and above this grade -3 points, and each Kyu grade below an additional -1 point.",
        "This means that it is impossible for weaker players to win a tournament even if they win all their games."
      )
    ),
    div(cls := "faq")(
      i("?"),
      p(
        strong("What about other tournament systems?"),
        "We don't plan to add more tournament systems to PlayStrategy at the moment."
      )
    )
  )
}
