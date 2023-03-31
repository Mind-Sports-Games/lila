package lila.tournament

import akka.stream.scaladsl._
import reactivemongo.akkastream.cursorProducer
import reactivemongo.api._
import reactivemongo.api.bson._

import lila.db.dsl._

final private class LeaderboardIndexer(
    tournamentRepo: TournamentRepo,
    pairingRepo: PairingRepo,
    playerRepo: PlayerRepo,
    leaderboardRepo: LeaderboardRepo
)(implicit
    ec: scala.concurrent.ExecutionContext,
    mat: akka.stream.Materializer
) {

  import LeaderboardApi._
  import BSONHandlers._

  def generateAll: Funit =
    leaderboardRepo.coll.delete.one($empty) >>
      tournamentRepo.coll
        .find(tournamentRepo.finishedSelect)
        .sort($sort desc "startsAt")
        .cursor[Tournament](ReadPreference.secondaryPreferred)
        .documentSource()
        .via(lila.common.LilaStream.logRate[Tournament]("leaderboard index tour")(logger))
        .mapAsyncUnordered(1)(generateTourEntries)
        .mapConcat(identity)
        .via(lila.common.LilaStream.logRate[Entry]("leaderboard index entries")(logger))
        .grouped(500)
        .mapAsyncUnordered(1)(saveEntries)
        .toMat(Sink.ignore)(Keep.right)
        .run()
        .void

  def indexOne(tour: Tournament): Funit =
    leaderboardRepo.coll.delete.one($doc("t" -> tour.id)) >>
      generateTourEntries(tour) flatMap saveEntries

  private def saveEntries(entries: Seq[Entry]): Funit =
    entries.nonEmpty ?? leaderboardRepo.coll.insert
      .many(
        entries.flatMap(BSONHandlers.leaderboardEntryHandler.writeOpt)
      )
      .void

  private def metaPointsFromRank(category: Option[Schedule.Freq], rank: Int): Option[Int] =
    category match {
      case Some(c) if c == Schedule.Freq.Shield || c == Schedule.Freq.MedleyShield =>
        if (rank == 1) Some(3)
        else if (rank == 2) Some(1)
        else Some(0)
      case _ => None
    }

  private def shieldKeyFromTour(tour: Tournament): Option[String] =
    tour.schedule.map(_.freq) match {
      case Some(c) if c == Schedule.Freq.Shield =>
        s"${tour.variant.gameFamily.id}_${tour.variant.id}".some
      case Some(c) if c == Schedule.Freq.MedleyShield =>
        tour.trophy1st match {
          case Some(t) if t == "shieldDraughtsMedley"     => "sdm".some
          case Some(t) if t == "shieldChessMedley"        => "scm".some
          case Some(t) if t == "shieldPlayStrategyMedley" => "spm".some
          case _                                          => None
        }
      case _ => None
    }

  private def generateTourEntries(tour: Tournament): Fu[List[Entry]] =
    for {
      nbGames <- pairingRepo.countByTourIdAndUserIds(tour.id)
      players <- playerRepo.bestByTourWithRank(tour.id, nb = 9000, skip = 0)
    } yield players.flatMap { case RankedPlayer(rank, player) =>
      nbGames get player.userId map { nb =>
        Entry(
          id = player._id,
          tourId = tour.id,
          userId = player.userId,
          nbGames = nb,
          score = player.score,
          rank = rank,
          rankRatio = Ratio(if (tour.nbPlayers > 0) rank.toDouble / tour.nbPlayers else 0),
          metaPoints = metaPointsFromRank(tour.schedule.map(_.freq), rank),
          shieldKey = shieldKeyFromTour(tour),
          freq = tour.schedule.map(_.freq),
          speed = tour.schedule.map(_.speed),
          perf = tour.perfType,
          date = tour.startsAt
        )
      }
    }
}
