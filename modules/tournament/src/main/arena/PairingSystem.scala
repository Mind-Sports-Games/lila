package lila.tournament
package arena

import lila.common.LightUser
import lila.user.{ User, UserRepo }

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

  // if waiting users can make pairings
  // then pair all users
  def createPairings(
      tour: Tournament,
      users: WaitingUsers,
      ranking: Ranking
  ): Fu[Pairings] = {
    for {
      lastOpponents <- pairingRepo.lastOpponents(tour.id, users.allIds, Math.min(300, users.size * 4))
      activePlayers <- playerRepo.countActive(tour.id).thenPp("activep")
      data = Data(tour, lastOpponents, ranking, activePlayers == 2)
      preps    <- (lastOpponents.hash.isEmpty || users.haveWaitedEnough) ?? evenOrAll(data, users, activePlayers)
      pairings <- prepsToPairings(preps)
    } yield pairings
  }.chronometer
    .logIfSlow(500, pairingLogger) { pairings =>
      s"createPairings ${tournamentUrl(tour.id)} ${pairings.size} pairings"
    }
    .result

  private def evenOrAll(data: Data, users: WaitingUsers, activePlayers: Int) = {
    val usersWithBots =
      if (activePlayers <= WaitingUsers.minPlayersForNoBots)
        users.pp("origusers").addBotUsers(activePlayers)
      else users
    makePreps(data, usersWithBots.pp("usersWithBots").evenNumber.pp("evenNo")) flatMap {
      //case Nil if users.isOddNoBots => makePreps(data, users.allIdsNoBots)
      case Nil if usersWithBots.isOdd => makePreps(data, usersWithBots.allIds)
      case x                          => fuccess(x)
    }
  }

  private val maxGroupSize = 100

  private def makePreps(data: Data, users: Set[User.ID]): Fu[List[Pairing.Prep]] = {
    import data._
    if (users.sizeIs < 2) fuccess(Nil)
    else
      playerRepo.rankedByTourAndUserIds(tour.id, users, ranking).thenPp("ranked") map { idles =>
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
