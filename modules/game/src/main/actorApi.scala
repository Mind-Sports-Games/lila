package lila.game
package actorApi

import lila.user.User
import strategygames.Pos

case class StartGame(game: Game)

case class FinishGame(
    game: Game,
    p1: Option[User],
    p2: Option[User]
) {
  def isVsSelf = p1.isDefined && p1 == p2
}

case class InsertGame(game: Game)

case class AbortedBy(pov: Pov)

case class CorresAlarmEvent(pov: Pov)

private[game] case object NewCaptcha

case class MoveGameEvent(
    game: Game,
    fen: String,
    move: String
)
object MoveGameEvent {
  def makeChan(gameId: Game.ID) = s"moveEvent:$gameId"
}

case class BoardDrawOffer(pov: Pov)

case class BoardOfferSquares(pov: Pov)
