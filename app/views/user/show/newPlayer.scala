package views.html.user.show

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.user.User

import controllers.routes

object newPlayer {

  def apply(u: User)(implicit ctx: Context) =
    div(cls := "new-player")(
      h2(trans.onboarding.welcome.txt()),
      p(
        trans.onboarding.profilePage.txt(),
        u.profile.isEmpty option frag(
          br,
          trans.onboarding.wouldYou(
            a(href := routes.Report.form)(trans.onboarding.improveIt())
          )
        )
      ),
      p(
        if (u.kid) trans.onboarding.kidModeEnabled.txt()
        else
          frag(
            trans.onboarding.willAChildUse(
              a(href := routes.Account.kid)(trans.onboarding.kidMode())
            )
          )
      ),
      p(
        trans.onboarding.suggestions.txt()
      ),
      ul(
        li(a(href := routes.Learn.index)(trans.onboarding.learnRules.txt())),
        li(a(href := routes.PlayApi.botOnline)(trans.onboarding.playBot.txt())),
        li(a(href := s"${routes.Lobby.home}#hook")(trans.onboarding.playOthers.txt())),
        li(a(href := routes.User.list)(trans.onboarding.follow.txt())),
        li(a(href := routes.Team.all(1))(trans.onboarding.joinCommunities.txt())),
        li(a(href := routes.Tournament.home)(trans.onboarding.playTournaments.txt())),
        li(
          trans.onboarding.learnFrom(
            a(href := routes.Study.allDefault(1))(trans.onboarding.studies())
          )
        ),
        li(a(href := routes.ForumCateg.show("weekly-challenges"))(trans.onboarding.weeklyChallenge.txt())),
        li(a(href := routes.Pref.form("game-display"))(trans.onboarding.configure.txt())),
        li(trans.onboarding.explore.txt())
      )
    )
}
