package views.html.user.show

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.progress.{ Badge, Progress, ProgressIcon, Step, StepGroup }
import lila.rating.PerfType
import lila.user.User

object checklist {

  def apply(u: User, progress: Progress)(implicit ctx: Context) =
    div(cls := "checklist")(
      (!u.isBot).option(onboarding(u, progress)),
      achievements(progress)
    )

  def full(u: User, progress: Progress)(implicit ctx: Context) =
    div(cls := "checklist checklist--full")(
      (!u.isBot).option(fullOnboarding(u, progress)),
      fullAchievements(progress)
    )

  private def onboarding(u: User, progress: Progress)(implicit ctx: Context) =
    div(cls := "checklist__onboarding")(
      top(
        onboardingTitle(progress),
        a(cls := "checklist__toggle button button-empty", href := routes.User.progress(u.username))(
          trans.onboarding.viewAllProgress()
        )
      ),
      onboardingProgress(u, progress),
      progress.nextStep(u.kid).fold(p(cls := "checklist__done")(trans.onboarding.allStepsDone())) { next =>
        frag(
          nextStep(next),
          StepGroup.all.map { group =>
            progress.remainingIn(group, u.kid).filterNot(_ == next) match {
              case Nil   => emptyFrag
              case steps =>
                div(cls := s"checklist__group checklist__group--${group.key}")(
                  h3(groupName(group)),
                  ul(steps.map(step => li(a(href := url(step))(label(step)))))
                )
            }
          }
        )
      }
    )

  private def fullOnboarding(u: User, progress: Progress)(implicit ctx: Context) =
    div(cls := "checklist__onboarding")(
      top(
        onboardingTitle(progress),
        a(
          cls      := "checklist__toggle button button-empty text",
          dataIcon := "I",
          href     := routes.User.show(u.username)
        )(trans.onboarding.backToProfile())
      ),
      progressBar(
        progress.nbCoreDone,
        Step.core.size,
        trans.onboarding.coreProgress.txt(progress.nbCoreDone, Step.core.size)
      ),
      progressBar(
        progress.nbDone(u.kid),
        Step.allFor(u.kid).size,
        trans.onboarding.exploreProgress.txt(progress.nbDone(u.kid), Step.allFor(u.kid).size)
      ),
      progress.nextStep(u.kid).fold(p(cls := "checklist__done")(trans.onboarding.allStepsDone()))(nextStep),
      div(cls := "checklist__groups")(
        StepGroup.all.map { group =>
          Step.ofGroup(group, u.kid) match {
            case Nil   => emptyFrag
            case steps =>
              div(cls := s"checklist__group checklist__group--${group.key}")(
                h3(
                  groupName(group),
                  span(cls := "checklist__group-count")(s"${steps.count(progress.has)}/${steps.size}")
                ),
                ul(steps.map { step =>
                  progress.steps.get(step.key) match {
                    case Some(date) =>
                      li(
                        cls      := "done",
                        dataIcon := "E",
                        title    := trans.onboarding.stepDoneOn.txt(showDate(date))
                      )(label(step))
                    case None => li(a(href := url(step))(label(step)))
                  }
                })
              )
          }
        }
      )
    )

  private def top(title: Frag, toggle: Frag) =
    div(cls := "checklist__top")(h2(title), toggle)

  private def onboardingTitle(progress: Progress)(implicit ctx: Context) =
    if (progress.isOnboarded) trans.onboarding.keepExploring()
    else trans.onboarding.checklistTitle()

  private def onboardingProgress(u: User, progress: Progress)(implicit ctx: Context) =
    if (progress.isOnboarded)
      progressBar(
        progress.nbDone(u.kid),
        Step.allFor(u.kid).size,
        trans.onboarding.exploreProgress.txt(progress.nbDone(u.kid), Step.allFor(u.kid).size)
      )
    else
      progressBar(
        progress.nbCoreDone,
        Step.core.size,
        trans.onboarding.coreProgress.txt(progress.nbCoreDone, Step.core.size)
      )

  private def nextStep(next: Step)(implicit ctx: Context) =
    a(cls := "checklist__next", href := url(next))(
      span(cls := "checklist__next-label")(trans.onboarding.nextStep()),
      strong(label(next))
    )

  private def achievements(progress: Progress)(implicit ctx: Context) =
    div(cls := "checklist__achievements")(
      h2(trans.onboarding.achievements()),
      allVariantsAchievement(progress)
    )

  private def fullAchievements(progress: Progress)(implicit ctx: Context) = {
    val (played, unplayed) = PerfType.allVariant.partition(pt => progress.hasPlayed(pt.key))
    div(cls := "checklist__achievements")(
      h2(trans.onboarding.achievements()),
      allVariantsAchievement(progress),
      if (unplayed.isEmpty) emptyFrag
      else if (played.isEmpty || played.size * 2 > Progress.nbVariants)
        variantList(trans.onboarding.variantsStillToPlay(), unplayed)
      else variantList(trans.onboarding.variantsPlayedSoFar(), played)
    )
  }

  private def allVariantsAchievement(progress: Progress)(implicit ctx: Context) =
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

  private def variantList(heading: Frag, variants: List[PerfType])(implicit ctx: Context) =
    div(cls := "checklist__variants")(
      h3(heading),
      ul(variants.map(pt => li(span(dataIcon := pt.iconString, cls := "text")(pt.trans))))
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
      case Step.KidMode            => trans.onboarding.stepKidMode()
      case Step.ChooseGames        => trans.onboarding.stepChooseGames()
      case Step.PlayHuman          => trans.onboarding.stepPlayHuman()
      case Step.JoinTeam           => trans.onboarding.stepJoinTeam()
      case Step.PlayTournament     => trans.onboarding.stepPlayTournament()
      case Step.PlayBot            => trans.onboarding.stepPlayBot()
      case Step.InviteFriend       => trans.onboarding.stepInviteFriend()
      case Step.PlayCorrespondence => trans.onboarding.stepPlayCorrespondence()
      case Step.PlayRated          => trans.onboarding.stepPlayRated()
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

  private val correspondenceSetup = s"${routes.Lobby.home}?time=correspondence#game"

  private def url(step: Step): String =
    step match {
      case Step.KidMode            => routes.Account.kid.url
      case Step.ChooseGames        => routes.Library.home.url
      case Step.PlayHuman          => lobbySetup
      case Step.JoinTeam           => routes.Team.all(1).url
      case Step.PlayTournament     => routes.Tournament.home.url
      case Step.PlayBot            => routes.PlayApi.botOnline.url
      case Step.InviteFriend       => lobbySetup
      case Step.PlayCorrespondence => correspondenceSetup
      case Step.PlayRated          => lobbySetup
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
