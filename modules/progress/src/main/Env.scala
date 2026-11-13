package lila.progress

import com.softwaremill.macwire.*

import lila.common.config.*
import lila.game.Game
import lila.user.User

@Module
final class Env(
    db: lila.db.Db,
    cacheApi: lila.memo.CacheApi,
    notifyApi: lila.notify.NotifyApi,
    tournamentRepo: lila.tournament.TournamentRepo,
    swissApi: lila.swiss.SwissApi,
    studyApi: lila.study.StudyApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  private lazy val coll = db(CollName("progress"))

  lazy val api: ProgressApi = wire[ProgressApi]

  private val isMedleyTour = cacheApi[String, Boolean](256, "progress.isMedleyTour") {
    _.expireAfterWrite(1 hour)
      .buildAsyncFuture(id => tournamentRepo.byId(id).dmap(_.exists(_.isMedley)))
  }

  private val isMedleySwiss = cacheApi[String, Boolean](256, "progress.isMedleySwiss") {
    _.expireAfterWrite(1 hour)
      .buildAsyncFuture(id => swissApi.byId(lila.swiss.Swiss.Id(id)).dmap(_.exists(_.isMedley)))
  }

  private def onFinishGame(game: Game, users: List[User]): Unit =
    game.players.foreach { player =>
      player.userId.flatMap(id => users.find(_.id == id)).filterNot(_.isBot) foreach { user =>
        val opponent      = game.opponent(player)
        val opponentIsBot =
          opponent.isAi || opponent.isPSBot || opponent.userId.exists(id => users.exists(u => u.id == id && u.isBot))
        val inTournament  = game.tournamentId.isDefined || game.swissId.isDefined
        val steps = List(
          (if (opponentIsBot) Step.PlayBot else Step.PlayHuman).some,
          game.rated.option(Step.PlayRated),
          (game.isCorrespondence || game.daysPerTurn.isDefined).option(Step.PlayCorrespondence),
          inTournament.option(Step.PlayTournament)
        ).flatten
        (api.tickAll(user.id, steps) >>
          (inTournament so tickMedley(user.id, game)) >>
          api.playedVariant(user.id, game.ratingVariant.key)).discard
      }
    }

  private def tickMedley(userId: User.ID, game: Game): Funit =
    api.get(userId) flatMap { progress =>
      (!progress.has(Step.PlayMedley)) so {
        val isMedley = game.tournamentId.map(isMedleyTour.get) orElse game.swissId.map(isMedleySwiss.get)
        isMedley so { _ flatMap { _ so api.tick(userId, Step.PlayMedley) } }
      }
    }

  lila.common.Bus.subscribeFuns(
    "finishGame" -> {
      case finish @ lila.game.actorApi.FinishGame(game, p1, p2) if !game.aborted && !finish.isVsSelf =>
        onFinishGame(game, List(p1, p2).flatten)
    },
    "finishPuzzle" -> { case res: lila.puzzle.Puzzle.UserResult =>
      api.tick(res.userId, Step.SolvePuzzle).discard
    }
  )

  lila.common.Bus.subscribeFun("forumPost", "team", "relation", "startStudy") {
    case lila.forum.actorApi.CreatePost(post) =>
      post.userId foreach { api.tick(_, Step.ForumPost).discard }
    case lila.hub.actorApi.team.CreateTeam(_, _, userId) => api.tick(userId, Step.JoinTeam).discard
    case lila.hub.actorApi.team.JoinTeam(_, userId)      => api.tick(userId, Step.JoinTeam).discard
    case lila.hub.actorApi.relation.Follow(from, _)      => api.tick(from, Step.FollowPlayer).discard
    case lila.study.actorApi.StartStudy(id) =>
      studyApi.byId(id) foreach { _ foreach { study => api.tick(study.ownerId, Step.CreateStudy).discard } }
  }
}
