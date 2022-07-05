package lila.tv

import strategygames.{ Player => PlayerIndex }
import scala.concurrent.duration._
import scala.concurrent.Promise

import lila.common.LightUser
import lila.game.Game
import lila.hub.Trouper

final private[tv] class ChannelTrouper(
    channel: Tv.Channel,
    onSelect: TvTrouper.Selected => Unit,
    proxyGame: Game.ID => Fu[Option[Game]],
    rematchOf: Game.ID => Option[Game.ID],
    lightUserSync: LightUser.GetterSync
)(implicit ec: scala.concurrent.ExecutionContext)
    extends Trouper {

  import ChannelTrouper._

  // games featured on this channel
  // first entry is the current game
  private var history = List.empty[Game.ID]

  private def oneId = history.headOption

  // the list of candidates by descending rating order
  private var manyIds = List.empty[Game.ID]

  private val candidateIds = new lila.memo.ExpireSetMemo(10 minutes)

  protected val process: Trouper.Receive = {

    case GetGameId(promise) => promise success oneId

    case GetGameIdAndHistory(promise) => promise success GameIdAndHistory(oneId, history drop 1)

    case GetGameIds(max, promise) => promise success manyIds.take(max)

    case SetGame(game) =>
      onSelect(TvTrouper.Selected(channel, game))
      history = game.id :: history.take(2)

    case TvTrouper.Select =>
      candidateIds.keys
        .map(proxyGame)
        .sequenceFu
        .map(_.view.collect {
          case Some(g) if channel isOngoingGame g => g
        }.toList)
        .foreach { candidates =>
          oneId ?? proxyGame foreach {
            case Some(current) if channel isOngoingGame current =>
              fuccess(wayBetter(current, candidates)) orElse rematch(current) foreach elect
            case Some(current) => rematch(current) orElse fuccess(bestOf(candidates)) foreach elect
            case _             => elect(bestOf(candidates))
          }
          manyIds = candidates
            .sortBy { g =>
              -(~g.averageUsersRating)
              -(if (!g.olderThan(30)) 5000 else 0)
              -(if (g.hasClock) 10000 else 0)
            }
            .take(50)
            .map(_.id)
        }
  }

  def addCandidate(game: Game): Unit = candidateIds put game.id

  private def elect(gameOption: Option[Game]): Unit = gameOption foreach { this ! SetGame(_) }

  private def wayBetter(game: Game, candidates: List[Game]) =
    bestOf(candidates) filter { isWayBetter(game, _) }

  private def isWayBetter(g1: Game, g2: Game) = score(g2.resetTurns) > (score(g1.resetTurns) * 1.17)

  private def rematch(game: Game): Fu[Option[Game]] = rematchOf(game.id) ?? proxyGame

  private def bestOf(candidates: List[Game]) = {
    import cats.implicits._
    candidates.maximumByOption(score)
  }

  private def score(game: Game): Int =
    heuristics.foldLeft(0) { case (score, fn) =>
      score + fn(game)
    }

  private type Heuristic = Game => Int

  private val heuristics: List[Heuristic] = List(
    ratingHeuristic(PlayerIndex.P1),
    ratingHeuristic(PlayerIndex.P2),
    titleHeuristic(PlayerIndex.P1),
    titleHeuristic(PlayerIndex.P2)
  )

  private def ratingHeuristic(playerIndex: PlayerIndex): Heuristic =
    game => game.player(playerIndex).stableRating | 1300

  private def titleHeuristic(playerIndex: PlayerIndex): Heuristic = game =>
    ~game
      .player(playerIndex)
      .some
      .flatMap { p =>
        p.stableRating.exists(2100 <) ?? p.userId
      }
      .flatMap(lightUserSync)
      .flatMap(_.title)
      .flatMap(Tv.titleScores.get)
}

object ChannelTrouper {

  case class GetGameId(promise: Promise[Option[Game.ID]])
  case class GetGameIds(max: Int, promise: Promise[List[Game.ID]])
  private case class SetGame(game: Game)

  case class GetGameIdAndHistory(promise: Promise[GameIdAndHistory])
  case class GameIdAndHistory(gameId: Option[Game.ID], history: List[Game.ID])
}
