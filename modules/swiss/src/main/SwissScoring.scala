package lila.swiss

import reactivemongo.api.bson._
import scala.concurrent.duration._

import lila.db.dsl._

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
            player.copy(points = sheet.points)
          }
          playerMap = SwissPlayer.toMap(withPoints)
          players = withPoints.map { p =>
            val playerPairings = (~pairingMap.get(p.userId)).values
            // TODO: calculate based on rounds, rather than pairings
            val (sbTieBreak, bhTieBreak, perfSum) = playerPairings.foldLeft((0f, 0f, 0f)) {
              case ((sbTieBreak, bhTieBreak, perfSum), pairing) =>
                val opponent       = playerMap.get(pairing opponentOf p.userId)
                val opponentPoints = opponent.??(_.points.value)
                val result         = pairing.resultFor(p.userId)
                val newSbTieBreak  = sbTieBreak + result.fold(opponentPoints / 2) { _ ?? opponentPoints }
                val newBhTieBreak  = bhTieBreak + opponentPoints // TODO: make sure this is how we calculate
                val newPerf = perfSum + opponent.??(_.rating) + result.?? { win =>
                  if (win) 500 else -500
                }
                (newSbTieBreak, newBhTieBreak, newPerf)
            }
            p.copy(
              sbTieBreak = Swiss.SonnenbornBerger(sbTieBreak),
              bhTieBreak = Some(Swiss.Buccholz(bhTieBreak)),
              performance = playerPairings.nonEmpty option Swiss.Performance(perfSum / playerPairings.size)
            ).recomputeScore
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
                      f.sbTieBreak    -> player.sbTieBreak,
                      f.bhTieBreak    -> player.bhTieBreak,
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
