package lila.activity

import com.softwaremill.macwire._
import scala.concurrent.duration._

import lila.common.config._
import lila.hub.actorApi.round.CorresMoveEvent

final class Env(
    db: lila.db.Db,
    practiceApi: lila.practice.PracticeApi,
    gameRepo: lila.game.GameRepo,
    postApi: lila.forum.PostApi,
    simulApi: lila.simul.SimulApi,
    studyApi: lila.study.StudyApi,
    tourLeaderApi: lila.tournament.LeaderboardApi,
    getTourName: lila.tournament.GetTourName,
    getTeamName: lila.team.GetTeamName,
    swissApi: lila.swiss.SwissApi
)(implicit
    ec: scala.concurrent.ExecutionContext,
    scheduler: org.apache.pekko.actor.Scheduler
) {

  private lazy val coll = db(CollName("activity"))

  lazy val write: ActivityWriteApi = wire[ActivityWriteApi]

  lazy val read: ActivityReadApi = wire[ActivityReadApi]

  lazy val jsonView = wire[JsonView]

  lila.common.Bus.subscribeFuns(
    "finishGame" -> {
      case lila.game.actorApi.FinishGame(game, _, _) if !game.aborted => write.game(game).discard
    },
    "finishPuzzle" -> { case res: lila.puzzle.Puzzle.UserResult =>
      write.puzzle(res).discard
    },
    "stormRun" -> { case lila.hub.actorApi.puzzle.StormRun(userId, score) =>
      write.storm(userId, score).discard
    },
    "racerRun" -> { case lila.hub.actorApi.puzzle.RacerRun(userId, score) =>
      write.racer(userId, score).discard
    },
    "streakRun" -> { case lila.hub.actorApi.puzzle.StreakRun(userId, score) =>
      write.streak(userId, score).discard
    }
  )

  lila.common.Bus.subscribeFun(
    "forumPost",
    "finishPractice",
    "team",
    "startSimul",
    "moveEventCorres",
    "plan",
    "relation",
    "startStudy",
    "streamStart",
    "swissFinish"
  ) {
    case lila.forum.actorApi.CreatePost(post)             => write.forumPost(post).discard
    case prog: lila.practice.PracticeProgress.OnComplete  => write.practice(prog).discard
    case lila.simul.Simul.OnStart(simul)                  => write.simul(simul).discard
    case CorresMoveEvent(move, Some(userId), _, _, false) => write.corresMove(move.gameId, userId).discard
    case lila.hub.actorApi.plan.MonthInc(userId, months)  => write.plan(userId, months).discard
    case lila.hub.actorApi.relation.Follow(from, to)      => write.follow(from, to).discard
    case lila.study.actorApi.StartStudy(id)               =>
      // wait some time in case the study turns private
      { val _ = scheduler.scheduleOnce(5 minutes) { write.study(id).discard } }
    case lila.hub.actorApi.team.CreateTeam(id, _, userId) => write.team(id, userId).discard
    case lila.hub.actorApi.team.JoinTeam(id, userId)      => write.team(id, userId).discard
    case lila.hub.actorApi.streamer.StreamStart(userId)   => write.streamStart(userId).discard
    case lila.swiss.SwissFinish(swissId, ranking)         => write.swiss(swissId, ranking).discard
  }
}
