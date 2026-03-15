package lila.tournament

import org.apache.pekko.actor._
import org.joda.time.DateTime

import lila.game.actorApi.FinishGame
import lila.user.User
import scala.concurrent.ExecutionContextExecutor

final private[tournament] class ApiActor(
    api: TournamentApi,
    shieldApi: TournamentShieldApi,
    winnersApi: WinnersApi,
    tournamentRepo: TournamentRepo
) extends Actor {

  implicit def ec: ExecutionContextExecutor = context.dispatcher

  lila.common.Bus.subscribe(
    self,
    "finishGame",
    "adjustCheater",
    "adjustBooster",
    "playban",
    "teamKick"
  )

  val markCheaterDQs = true

  def receive = {

    case FinishGame(game, _, _) => api.finishGame(game).discard

    case lila.playban.SittingDetected(game, player) => api.sittingDetected(game, player).discard

    case lila.hub.actorApi.mod.MarkCheater(userId, true) =>
      ejectFromEnterable(userId) >>
        api.ejectRecent(userId, markCheaterDQs) >>
        shieldApi.clearAfterMarking(userId) >>
        winnersApi.clearAfterMarking(userId)
      ()

    case lila.hub.actorApi.mod.MarkBooster(userId) => ejectFromEnterable(userId).discard

    case lila.hub.actorApi.round.Berserk(gameId, userId) => api.berserk(gameId, userId).discard

    case lila.hub.actorApi.playban.Playban(userId, _) => api.pausePlaybanned(userId).discard

    case lila.hub.actorApi.team.KickFromTeam(teamId, userId) => api.kickFromTeam(teamId, userId).discard
  }

  private def ejectFromEnterable(userId: User.ID) =
    tournamentRepo.withdrawableIds(userId) flatMap {
      ids =>
        Future.sequence(ids.map {
          api.ejectLameFromEnterable(_, userId)
        })
    }
}
