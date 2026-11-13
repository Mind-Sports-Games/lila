package lila.progress

sealed abstract class StepGroup(val key: String)

object StepGroup {
  case object Core      extends StepGroup("core")
  case object Play      extends StepGroup("play")
  case object Learn     extends StepGroup("learn")
  case object Community extends StepGroup("community")
  case object Watch     extends StepGroup("watch")
  case object Customise extends StepGroup("customise")
  case object Support   extends StepGroup("support")

  val all: List[StepGroup]     = List(Core, Play, Learn, Community, Watch, Customise, Support)
  val explore: List[StepGroup] = all.filterNot(_ == Core)
}

sealed abstract class Step(val key: String, val group: StepGroup) {
  def isCore = group == StepGroup.Core
}

object Step {
  import StepGroup.*

  case object ChooseGames        extends Step("chooseGames", Core)
  case object PlayHuman          extends Step("playHuman", Core)
  case object JoinTeam           extends Step("joinTeam", Core)
  case object PlayTournament     extends Step("playTournament", Core)
  case object PlayBot            extends Step("playBot", Play)
  case object InviteFriend       extends Step("inviteFriend", Play)
  case object PlayCorrespondence extends Step("playCorrespondence", Play)
  case object PlayRated          extends Step("playRated", Play)
  case object PlayMedley         extends Step("playMedley", Play)
  case object CreateTournament   extends Step("createTournament", Play)
  case object SolvePuzzle        extends Step("solvePuzzle", Learn)
  case object LearnRules         extends Step("learnRules", Learn)
  case object Coordinates        extends Step("coordinates", Learn)
  case object MemoryGame         extends Step("memoryGame", Learn)
  case object AnalyseGame        extends Step("analyseGame", Learn)
  case object BoardEditor        extends Step("boardEditor", Learn)
  case object CreateStudy        extends Step("createStudy", Learn)
  case object FollowPlayer       extends Step("followPlayer", Community)
  case object ForumPost          extends Step("forumPost", Community)
  case object CompleteProfile    extends Step("completeProfile", Community)
  case object WatchTv            extends Step("watchTv", Watch)
  case object WatchStreamer      extends Step("watchStreamer", Watch)
  case object CustomiseBoard     extends Step("customiseBoard", Customise)
  case object CheckPreferences   extends Step("checkPreferences", Customise)
  case object Patron             extends Step("patron", Support)

  val all: List[Step] = List(
    ChooseGames,
    PlayHuman,
    JoinTeam,
    PlayTournament,
    PlayBot,
    InviteFriend,
    PlayCorrespondence,
    PlayRated,
    PlayMedley,
    CreateTournament,
    SolvePuzzle,
    LearnRules,
    Coordinates,
    MemoryGame,
    AnalyseGame,
    BoardEditor,
    CreateStudy,
    FollowPlayer,
    ForumPost,
    CompleteProfile,
    WatchTv,
    WatchStreamer,
    CustomiseBoard,
    CheckPreferences,
    Patron
  )

  val core: List[Step]    = all.filter(_.isCore)
  val explore: List[Step] = all.filterNot(_.isCore)

  val byKey: Map[String, Step] = all.map(s => s.key -> s).toMap

  def ofGroup(group: StepGroup): List[Step] = all.filter(_.group == group)
}
