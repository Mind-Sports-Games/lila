package views.html.user.show

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.progress.{ Badge, Progress, ProgressIcon, Step, StepGroup }
import lila.user.User

object checklist {

  def apply(u: User, progress: Progress)(implicit ctx: Context) =
    div(cls := "checklist")(
      onboarding(u, progress),
      achievements(progress)
    )

  private def onboarding(u: User, progress: Progress)(implicit ctx: Context) =
    div(cls := "checklist__onboarding")(
      h2(
        if (progress.isOnboarded) trans.onboarding.keepExploring()
        else trans.onboarding.checklistTitle()
      ),
      if (progress.isOnboarded)
        progressBar(
          progress.nbExploreDone,
          Step.explore.size,
          trans.onboarding.exploreProgress.txt(progress.nbExploreDone, Step.explore.size)
        )
      else
        progressBar(
          progress.nbCoreDone,
          Step.core.size,
          trans.onboarding.coreProgress.txt(progress.nbCoreDone, Step.core.size)
        ),
      progress.nextStep.fold(p(cls := "checklist__done")(trans.onboarding.allStepsDone())) { next =>
        frag(
          a(cls := "checklist__next", href := url(next))(
            span(cls := "checklist__next-label")(trans.onboarding.nextStep()),
            strong(label(next))
          ),
          StepGroup.all.map { group =>
            progress.remainingIn(group).filterNot(_ == next) match {
              case Nil   => emptyFrag
              case steps =>
                div(cls := s"checklist__group checklist__group--${group.key}")(
                  h3(groupName(group)),
                  ul(steps.map(step => li(a(href := url(step))(label(step)))))
                )
            }
          }
        )
      },
      (!u.kid && !progress.isOnboarded).option(
        p(cls := "checklist__kid")(
          trans.onboarding.willAChildUse(a(href := routes.Account.kid)(trans.onboarding.kidMode()))
        )
      )
    )

  private def achievements(progress: Progress)(implicit ctx: Context) =
    div(cls := "checklist__achievements")(
      h2(trans.onboarding.achievements()),
      div(cls := List("checklist__achievement" -> true, "done" -> progress.hasPlayedAllVariants))(
        strong(dataIcon := ProgressIcon(Badge.AllVariants, 1).toString, cls := "text")(
          trans.onboarding.playEveryVariant()
        ),
        progressBar(
          progress.nbVariantsPlayed,
          Progress.nbVariants,
          trans.onboarding.variantsPlayed.txt(progress.nbVariantsPlayed, Progress.nbVariants)
        )
      )
    )

  private def progressBar(done: Int, total: Int, text: String) =
    div(cls := "checklist__progress")(
      div(cls := "checklist__bar")(
        span(style := s"width:${if (total > 0) done * 100 / total else 0}%")
      ),
      span(cls := "checklist__count")(text)
    )

  private def groupName(group: StepGroup)(implicit ctx: Context) =
    group match {
      case StepGroup.Core      => trans.onboarding.groupCore()
      case StepGroup.Play      => trans.onboarding.groupPlay()
      case StepGroup.Learn     => trans.onboarding.groupLearn()
      case StepGroup.Community => trans.onboarding.groupCommunity()
      case StepGroup.Watch     => trans.onboarding.groupWatch()
      case StepGroup.Customise => trans.onboarding.groupCustomise()
      case StepGroup.Support   => trans.onboarding.groupSupport()
    }

  private def label(step: Step)(implicit ctx: Context) =
    step match {
      case Step.ChooseGames        => trans.onboarding.stepChooseGames()
      case Step.PlayHuman          => trans.onboarding.stepPlayHuman()
      case Step.JoinTeam           => trans.onboarding.stepJoinTeam()
      case Step.PlayTournament     => trans.onboarding.stepPlayTournament()
      case Step.PlayBot            => trans.onboarding.stepPlayBot()
      case Step.InviteFriend       => trans.onboarding.stepInviteFriend()
      case Step.PlayCorrespondence => trans.onboarding.stepPlayCorrespondence()
      case Step.PlayRated          => trans.onboarding.stepPlayRated()
      case Step.PlayMedley         => trans.onboarding.stepPlayMedley()
      case Step.CreateTournament   => trans.onboarding.stepCreateTournament()
      case Step.SolvePuzzle        => trans.onboarding.stepSolvePuzzle()
      case Step.LearnRules         => trans.onboarding.stepLearnRules()
      case Step.Coordinates        => trans.onboarding.stepCoordinates()
      case Step.MemoryGame         => trans.onboarding.stepMemoryGame()
      case Step.AnalyseGame        => trans.onboarding.stepAnalyseGame()
      case Step.BoardEditor        => trans.onboarding.stepBoardEditor()
      case Step.CreateStudy        => trans.onboarding.stepCreateStudy()
      case Step.FollowPlayer       => trans.onboarding.stepFollowPlayer()
      case Step.ForumPost          => trans.onboarding.stepForumPost()
      case Step.CompleteProfile    => trans.onboarding.stepCompleteProfile()
      case Step.WatchTv            => trans.onboarding.stepWatchTv()
      case Step.WatchStreamer      => trans.onboarding.stepWatchStreamer()
      case Step.CustomiseBoard     => trans.onboarding.stepCustomiseBoard()
      case Step.CheckPreferences   => trans.onboarding.stepCheckPreferences()
      case Step.Patron             => trans.onboarding.stepPatron()
    }

  private val lobbySetup = s"${routes.Lobby.home}#game"

  private def url(step: Step): String =
    step match {
      case Step.ChooseGames        => routes.Library.home.url
      case Step.PlayHuman          => lobbySetup
      case Step.JoinTeam           => routes.Team.all(1).url
      case Step.PlayTournament     => routes.Tournament.home.url
      case Step.PlayBot            => routes.PlayApi.botOnline.url
      case Step.InviteFriend       => lobbySetup
      case Step.PlayCorrespondence => lobbySetup
      case Step.PlayRated          => lobbySetup
      case Step.PlayMedley         => routes.Tournament.home.url
      case Step.CreateTournament   => routes.Tournament.form.url
      case Step.SolvePuzzle        => routes.Puzzle.base.url
      case Step.LearnRules         => routes.Page.variantHome.url
      case Step.Coordinates        => routes.Coordinate.home.url
      case Step.MemoryGame         => routes.Memory.home.url
      case Step.AnalyseGame        => routes.UserAnalysis.index.url
      case Step.BoardEditor        => routes.Editor.index.url
      case Step.CreateStudy        => routes.Study.allDefault(1).url
      case Step.FollowPlayer       => routes.User.list.url
      case Step.ForumPost          => routes.ForumCateg.show("weekly-challenges").url
      case Step.CompleteProfile    => routes.Account.profile.url
      case Step.WatchTv            => routes.Tv.index.url
      case Step.WatchStreamer      => routes.Streamer.index().url
      case Step.CustomiseBoard     => "#dasher"
      case Step.CheckPreferences   => routes.Pref.form(lila.pref.PrefCateg.GameDisplay.slug).url
      case Step.Patron             => routes.Plan.index.url
    }
}
