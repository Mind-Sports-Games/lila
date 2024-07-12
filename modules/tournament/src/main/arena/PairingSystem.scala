package lila.tournament
package arena

import lila.common.LightUser
import lila.user.{ User, UserRepo }

import scala.concurrent.Future

final private[tournament] class PairingSystem(
    pairingRepo: PairingRepo,
    playerRepo: PlayerRepo,
    userRepo: UserRepo,
    playerIndexHistoryApi: PlayerIndexHistoryApi
)(implicit
    ec: scala.concurrent.ExecutionContext,
    idGenerator: lila.game.IdGenerator
) {

  import PairingSystem._
  import lila.tournament.Tournament.tournamentUrl

  private val minPlayersForNoBots = 4

  // if waiting users can make pairings
  // then pair all users
  def createPairings(
      tour: Tournament,
      users: WaitingUsers,
      ranking: Ranking
  ): Fu[Pairings] = {
    for {
      inPlayUsers       <- pairingRepo.inPlayUsers(tour.id)
      justFinishedUsers <- pairingRepo.justFinishedUsers(tour.id, tour.waitForPlayerReturnSeconds)
      activeUsers   = inPlayUsers ++ justFinishedUsers -- LightUser.tourBotsIDs.toSet
      activePlayers = users.activePlayers(activeUsers)
      botsToAdd <- botsToAdd(tour, activePlayers)
      usersWithBots = users.addBotUsers(botsToAdd)
      lastOpponents <- limitLastOpponents(tour, users, activePlayers)
      data = Data(tour, lastOpponents, ranking, activePlayers == 2)
      preps <- readyToRunPreps(lastOpponents, usersWithBots, activePlayers) ?? evenOrAll(
        data,
        usersWithBots
      )
      pairings <- prepsToPairings(preps)
    } yield pairings
  }.chronometer
    .logIfSlow(500, pairingLogger) { pairings =>
      s"createPairings ${tournamentUrl(tour.id)} ${pairings.size} pairings"
    }
    .result

  private def limitLastOpponents(
      tour: Tournament,
      users: WaitingUsers,
      activePlayers: Int
  ): Fu[Pairing.LastOpponents] =
    //this check enables pairing to happen with a bot when we have low numbers
    if (
      activePlayers <= 2 &&
      users.size % 2 == 1 &&
      users.size < minPlayersForNoBots &&
      (!tour.botsAllowed || activePlayers % 2 == 1)
    )
      fuccess(Pairing.LastOpponents.empty)
    else pairingRepo.lastOpponents(tour.id, users.all, users.size * 4)

  private def readyToRunPreps(
      lastOpponents: Pairing.LastOpponents,
      users: WaitingUsers,
      activePlayers: Int
  ): Boolean =
    lastOpponents.hash.isEmpty || users.haveWaitedEnough(Math.min(2, activePlayers))

  private def isBotAvailable(tourId: Tournament.ID)(botId: User.ID): Fu[Option[User.ID]] =
    pairingRepo.isPlaying(tourId, botId).fold(_ => None, if (_) None else Some(botId))

  private def availableBots(tourId: Tournament.ID)(joinedBots: List[Player]): Fu[Set[User.ID]] =
    Future
      .traverse(
        //headOption used as we've only ever tested with one bot
        //in a tournament at any one time and would want to work
        //out some sytem for cycling through bots
        joinedBots.filterNot(_.withdraw).map(_.userId).headOption.toList
      )(
        isBotAvailable(tourId)
      )
      .map(_.flatten.toSet)

  private def botsToAdd(tour: Tournament, activePlayers: Int): Fu[Set[User.ID]] =
    if (tour.botsAllowed && activePlayers < minPlayersForNoBots)
      playerRepo
        .byTourAndUserIds(tour.id, LightUser.tourBotsIDs)
        .flatMap { availableBots(tour.id) }
    else fuccess(Set())

  private def evenOrAll(data: Data, users: WaitingUsers) = {
    makePreps(data, users.evenNumber) flatMap {
      case Nil if users.isOdd => makePreps(data, users.all)
      case x                  => fuccess(x)
    }
  }

  private val maxGroupSize = 100

  private def makePreps(data: Data, users: Set[User.ID]): Fu[List[Pairing.Prep]] = {
    import data._
    if (users.sizeIs < 2) fuccess(Nil)
    else
      playerRepo.rankedByTourAndUserIds(tour.id, users, ranking) map { idles =>
        val nbIdles = idles.size
        if (data.tour.isRecentlyStarted && !data.tour.isTeamBattle) proximityPairings(tour, idles)
        else if (nbIdles > maxGroupSize) {
          // make sure groupSize is even with / 4 * 2
          val groupSize = (nbIdles / 4 * 2) atMost maxGroupSize
          bestPairings(data, idles take groupSize) :::
            bestPairings(data, idles.slice(groupSize, groupSize + groupSize))
        } else if (nbIdles > 1) bestPairings(data, idles)
        else Nil
      }
  }.monSuccess(_.tournament.pairing.prep)
    .chronometer
    .logIfSlow(200, pairingLogger) { preps =>
      s"makePreps ${tournamentUrl(data.tour.id)} ${users.size} users, ${preps.size} preps"
    }
    .result

  private def prepsToPairings(preps: List[Pairing.Prep]): Fu[List[Pairing]] =
    idGenerator.games(preps.size) map { ids =>
      preps.zip(ids).map { case (prep, id) =>
        //playerIndex was chosen in prepWithPlayerIndex function
        prep.toPairing(id)
      }
    }

  private def proximityPairings(tour: Tournament, players: List[RankedPlayer]): List[Pairing.Prep] =
    addPlayerIndexHistory(players) grouped 2 collect { case List(p1, p2) =>
      Pairing.prepWithPlayerIndex(tour, p1, p2)
    } toList

  private def bestPairings(data: Data, players: RankedPlayers): List[Pairing.Prep] =
    (players.sizeIs > 1) ?? AntmaPairing(data, addPlayerIndexHistory(players))

  private def addPlayerIndexHistory(players: RankedPlayers) =
    players.map(_ withPlayerIndexHistory playerIndexHistoryApi.get)
}

private object PairingSystem {

  case class Data(
      tour: Tournament,
      lastOpponents: Pairing.LastOpponents,
      ranking: Map[User.ID, Int],
      onlyTwoActivePlayers: Boolean
  )

  /* Was previously static 1000.
   * By increasing the factor for high ranked players,
   * we increase pairing quality for them.
   * The higher ranked, and the more ranking is relevant.
   * For instance rank 1 vs rank 5
   * is better thank 300 vs rank 310
   * This should increase leader vs leader pairing chances
   *
   * top rank factor = 2000
   * bottom rank factor = 300
   */
  def rankFactorFor(
      players: List[RankedPlayerWithPlayerIndexHistory]
  ): (RankedPlayerWithPlayerIndexHistory, RankedPlayerWithPlayerIndexHistory) => Int = {
    val maxRank = players.maxBy(_.rank).rank
    (a, b) => {
      val rank = Math.min(a.rank, b.rank)
      300 + 1700 * (maxRank - rank) / maxRank
    }
  }
}
