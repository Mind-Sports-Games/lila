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
    studyApi: lila.study.StudyApi,
    userRepo: lila.user.UserRepo
)(implicit ec: scala.concurrent.ExecutionContext) {

  private lazy val coll = db(CollName("progress"))

  lazy val api: ProgressApi = wire[ProgressApi]

  api.revokeOutdatedAllVariants.discard

  private def onFinishGame(game: Game, users: List[User]): Unit =
    game.players.foreach { player =>
      player.userId.flatMap(id => users.find(_.id == id)) foreach { user =>
        val opponent      = game.opponent(player)
        val opponentIsBot =
          opponent.isPSBot || opponent.userId.exists(id => users.exists(u => u.id == id && u.isBot))
        val inTournament  = game.tournamentId.isDefined || game.swissId.isDefined
        val steps = List(
          (if (opponentIsBot) Step.PlayBot else Step.PlayHuman).some,
          game.rated.option(Step.PlayRated),
          (game.isCorrespondence || game.daysPerTurn.isDefined).option(Step.PlayCorrespondence),
          inTournament.option(Step.PlayTournament)
        ).flatten
        ((if (user.isBot) funit else api.tickAll(user.id, steps)) >>
          (game.rated so api.playedVariant(user.id, game.ratingVariant.key))).discard
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
