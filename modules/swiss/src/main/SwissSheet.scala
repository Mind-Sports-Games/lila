package lila.swiss
import strategygames.{ GameFamily }
import strategygames.variant.Variant

private case class SwissSheet(outcomes: List[List[SwissSheet.Outcome]]) {
  import SwissSheet._

  def points =
    Swiss.Points {
      outcomes.foldLeft(0) { case (acc, out) => acc + pointsFor(out) }
    }

  def pointsTrf =
    Swiss.Points {
      outcomes.foldLeft(0) { case (acc, out) => acc + pointsForTrf(out) }
    }
}

private object SwissSheet {

  sealed trait Outcome
  case object Bye     extends Outcome
  case object Absent  extends Outcome
  case object Ongoing extends Outcome
  case object Win     extends Outcome
  case object Loss    extends Outcome
  case object Draw    extends Outcome

  def pointsFor(outcome: Outcome): Int =
    outcome match {
      case Win | Bye => 2
      case Draw      => 1
      case _         => 0
    }

  def pointsFor(outcome: List[Outcome]): Int =
    outcome.foldLeft(0) { case (acc, out) => acc + pointsFor(out) }

  //BBpairings can only handle the same points for a win, loss or draw therefore we have to lie to it
  def pointsForTrf(outcome: List[Outcome]): Int =
    pointsFor(outcome) match {
      case score if score > outcome.length  => 2
      case score if score == outcome.length => 1
      case _                                => 0
    }

  def many(
      swiss: Swiss,
      players: List[SwissPlayer],
      pairingMap: SwissPairing.PairingMap
  ): List[SwissSheet] =
    players.map { player =>
      one(swiss, ~pairingMap.get(player.userId), player)
    }

  def one(
      swiss: Swiss,
      pairingMap: Map[SwissRound.Number, SwissPairing],
      player: SwissPlayer
  ): SwissSheet =
    SwissSheet {
      swiss.allRounds.map { round =>
        pairingMap get round match {
          case Some(pairing) =>
            pairing.status match {
              case Left(_) => List(Ongoing)
              case Right(None) =>
                if (swiss.settings.isMatchScore)
                  outcomeListFromMultiMatch(player, pairing)
                else List(Draw)
              case Right(Some(playerIndex)) =>
                if (swiss.settings.isMatchScore) {
                  outcomeListFromMultiMatch(player, pairing)
                } else if (pairing(playerIndex) == player.userId) List(Win)
                else List(Loss)
            }
          case None if player.byes(round) =>
            if (swiss.settings.isMatchScore) {
              if (swiss.settings.isBestOfX) {
                List.fill(swiss.settings.nbGamesPerRound / 2 + 1)(
                  Bye
                ) // odd nbGamesPerRound not allowed in form for this setup...
              } else {
                List.fill(swiss.settings.nbGamesPerRound)(Bye)
              }
            } else List(Bye)
          case None => List(Absent)
        }
      }
    }

  def outcomeListFromMultiMatch(player: SwissPlayer, pairing: SwissPairing): List[Outcome] =
    pairing.matchStatus match {
      case Left(_) => List(Ongoing)
      case Right(l) =>
        l.zipWithIndex
          .map { case (outcome, index) =>
            outcome.fold[Outcome](Draw)(c => {
              pairing.variant match {
                case Some(v) if v.gameFamily == GameFamily.Backgammon() => {
                  if (pairing(c) == player.userId) Win else Loss
                } //multimatch Backgammon games require players to keep colour/player for display same pieces
                case _ => {
                  if (
                    (pairing(c) == player.userId && index % 2 == 0) || (pairing(
                      c
                    ) != player.userId && index % 2 == 1)
                  ) Win
                  else Loss
                }
              }
            })
          }
    }

}

final private class SwissSheetApi(colls: SwissColls)(implicit
    ec: scala.concurrent.ExecutionContext,
    mat: akka.stream.Materializer
) {

  import akka.stream.scaladsl._
  import org.joda.time.DateTime
  import reactivemongo.akkastream.cursorProducer
  import reactivemongo.api.ReadPreference
  import lila.db.dsl._
  import BsonHandlers._

  def source(
      swiss: Swiss,
      sort: Bdoc
  ): Source[(SwissPlayer, Map[SwissRound.Number, SwissPairing], SwissSheet), _] = {
    val readPreference =
      if (swiss.finishedAt.exists(_ isBefore DateTime.now.minusSeconds(10)))
        ReadPreference.secondaryPreferred
      else ReadPreference.primary
    SwissPlayer
      .fields { f =>
        colls.player
          .find($doc(f.swissId -> swiss.id))
          .sort(sort)
      }
      .cursor[SwissPlayer](readPreference)
      .documentSource()
      .mapAsync(4) { player =>
        SwissPairing.fields { f =>
          colls.pairing.list[SwissPairing](
            $doc(f.swissId -> swiss.id, f.players -> player.userId),
            readPreference
          ) dmap { player -> _ }
        }
      }
      .map { case (player, pairings) =>
        val pairingMap = pairings.map { p =>
          p.round -> p
        }.toMap
        (player, pairingMap, SwissSheet.one(swiss, pairingMap, player))
      }
  }
}
