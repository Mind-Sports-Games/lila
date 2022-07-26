package lila.swiss

private case class SwissSheet(outcomes: List[SwissSheet.Outcome]) {
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
  case object Bye      extends Outcome
  case object Absent   extends Outcome
  case object Ongoing  extends Outcome
  case object Win      extends Outcome
  case object Loss     extends Outcome
  case object Draw     extends Outcome
  case object WinWin   extends Outcome
  case object WinDraw  extends Outcome
  case object LoseWin  extends Outcome
  case object WinLose  extends Outcome
  case object DrawDraw extends Outcome
  case object LoseDraw extends Outcome
  case object LoseLose extends Outcome

  def pointsFor(outcome: Outcome) =
    outcome match {
      case Win | Bye => 2
      case Draw      => 1
      case WinWin    => 4
      case WinDraw   => 3
      case WinLose   => 2
      case DrawDraw  => 2
      case LoseWin   => 2
      case LoseDraw  => 1
      case _         => 0
    }

  //BBpairings can only handle the same points for a win, loss or draw therefore we have to lie to it
  def pointsForTrf(outcome: Outcome): Int =
    outcome match {
      case Win | Bye => 2
      case Draw      => 1
      case WinWin    => 2
      case WinDraw   => 2
      case WinLose   => 1
      case LoseWin   => 1
      case _         => 0
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
        pairingMap.pp("pairing map") get round match {
          case Some(pairing) =>
            pairing.pp("pairing").status match {
              case Left(_) => Ongoing
              case Right(None) =>
                if (swiss.settings.useMatchScore) WinLose else Draw //todo get actual results not just WinLose
              case Right(Some(playerIndex)) =>
                if (swiss.settings.useMatchScore) {
                  pairing.matchOutcome
                    .map(outcome => outcome.fold(1)(c => if (c == playerIndex) 2 else 0))
                    .foldLeft(0)(_ + _) match {
                    case 4 => WinWin
                    case 3 => WinDraw
                    case 2 => DrawDraw
                    case 1 => LoseDraw
                    case 0 => LoseLose
                  }
                } else if (pairing(playerIndex) == player.userId) Win
                else Loss
            }
          case None if player.byes(round) => Bye
          case None                       => Absent
        }
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
