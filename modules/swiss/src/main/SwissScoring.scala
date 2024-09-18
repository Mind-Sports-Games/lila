package lila.swiss

import reactivemongo.api.bson._
import scala.concurrent.duration._

import lila.db.dsl._

import strategygames.tiebreaks.{ Player => TiebreakPlayer, Result => TiebreakResult, Tiebreak, Tournament }

final private class SwissTiebreak(
    swiss: Swiss,
    playerMap: SwissPlayer.PlayerMap,
    pairingMap: SwissPairing.PairingMap
) extends Tournament {
  val rounds   = swiss.tieBreakRounds
  val nbRounds = rounds.length
  def resultsForPlayer(hero: TiebreakPlayer): List[TiebreakResult] = {
    playerMap
      .get(hero.id)
      .fold[List[TiebreakResult]](List())(player => {
        val playerPairingMap = ~pairingMap.get(hero.id)
        rounds.flatMap { round =>
          {
            // We use 1-based indexing here,
            // but the tiebreakers use 0 based indexing
            val r = Tiebreak.Round(round.value - 1)
            playerPairingMap get round match {
              case Some(pairing) => {
                val foe = TiebreakPlayer(pairing opponentOf hero.id)
                pairing.status match {
                  case Left(_)     => None
                  case Right(None) => Some(r.draw(hero, foe))
                  case Right(Some(playerIndex)) =>
                    if (pairing(playerIndex) == hero.id)
                      Some(r.win(hero, foe))
                    else
                      Some(r.lose(hero, foe))
                }
              }
              case None if player.byes(round) => Some(r.bye(hero))
              case None if round.value == 1   => Some(r.absentLoss(hero))
              case None                       => Some(r.withdrawn(hero))
            }
          }
        }
      })
  }
}

final private class SwissScoring(
    colls: SwissColls
)(implicit system: akka.actor.ActorSystem, ec: scala.concurrent.ExecutionContext, mode: play.api.Mode) {

  import BsonHandlers._

  def apply(id: Swiss.Id): Fu[Option[SwissScoring.Result]] = sequencer(id).monSuccess(_.swiss.scoringGet)

  private val sequencer =
    new lila.hub.AskPipelines[Swiss.Id, Option[SwissScoring.Result]](
      compute = recompute,
      expiration = 1 minute,
      timeout = 10 seconds,
      name = "swiss.scoring"
    )

  private def recompute(id: Swiss.Id): Fu[Option[SwissScoring.Result]] =
    colls.swiss.byId[Swiss](id.value) flatMap {
      _.?? { (swiss: Swiss) =>
        for {
          (prevPlayers, pairings) <- fetchPlayers(swiss) zip fetchPairings(swiss)
          pairingMap = SwissPairing.toMap(pairings)
          sheets     = SwissSheet.many(swiss, prevPlayers, pairingMap)
          withPoints = (prevPlayers zip sheets).map { case (player, sheet) =>
            player.copy(points =
              if (!swiss.settings.mcmahon) sheet.points
              else
                sheet.points + Swiss.Points(
                  (player.mcMahonStartingScore(swiss.settings.mcmahonCutoffGrade) * 2).toInt
                )
            )
          }
          playerMap = SwissPlayer.toMap(withPoints)
          tiebreaks = new Tiebreak(new SwissTiebreak(swiss, playerMap, pairingMap))
          players = withPoints.map { p =>
            {
              val playerPairings = (~pairingMap.get(p.userId)).values
              val sbTieBreak     = 0.5 * tiebreaks.lilaSonnenbornBerger(TiebreakPlayer(p.userId))
              val bhTieBreak     = 0.5 * tiebreaks.fideBuchholz(TiebreakPlayer(p.userId))
              // TODO: should the perf rating be in stratgames too?
              val perfSum = playerPairings.foldLeft(0f) { case (perfSum, pairing) =>
                val opponent = playerMap.get(pairing opponentOf p.userId)
                val result   = pairing.resultFor(p.userId)
                val newPerf = perfSum + opponent.??(_.actualRating) + result.?? { win =>
                  if (win) 500 else -500
                }
                newPerf
              }
              p.copy(
                sbTieBreak = Swiss.SonnenbornBerger(sbTieBreak),
                bhTieBreak = Some(Swiss.Buchholz(bhTieBreak)),
                performance = playerPairings.nonEmpty option Swiss.Performance(perfSum / playerPairings.size)
              ).recomputeScore
            }
          }
          _ <- SwissPlayer.fields { f =>
            prevPlayers
              .zip(players)
              .withFilter { case (a, b) =>
                a != b
              }
              .map { case (_, player) =>
                colls.player.update
                  .one(
                    $id(player.id),
                    $set(
                      f.points      -> player.points,
                      f.sbTieBreak  -> player.sbTieBreak,
                      f.bhTieBreak  -> player.bhTieBreak,
                      f.performance -> player.performance,
                      f.score       -> player.score
                    )
                  )
                  .void
              }
              .sequenceFu
              .void
          }
        } yield SwissScoring
          .Result(
            swiss,
            players.zip(sheets).sortBy(-_._1.score.value),
            SwissPlayer toMap players,
            pairingMap
          )
          .some
      }.monSuccess(_.swiss.scoringRecompute)
    }

  private def fetchPlayers(swiss: Swiss) =
    SwissPlayer.fields { f =>
      colls.player
        .find($doc(f.swissId -> swiss.id))
        .sort($sort asc f.score)
        .cursor[SwissPlayer]()
        .list()
    }

  private def fetchPairings(swiss: Swiss) =
    !swiss.isCreated ?? SwissPairing.fields { f =>
      colls.pairing.list[SwissPairing]($doc(f.swissId -> swiss.id))
    }
}

private object SwissScoring {

  case class Result(
      swiss: Swiss,
      leaderboard: List[(SwissPlayer, SwissSheet)],
      playerMap: SwissPlayer.PlayerMap,
      pairings: SwissPairing.PairingMap
  )
}
