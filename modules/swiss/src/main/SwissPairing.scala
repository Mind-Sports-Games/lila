package lila.swiss

import strategygames.{ Player => SGPlayer }
import strategygames.format.FEN
import lila.game.Game
import lila.user.User

case class SwissPairing(
    id: Game.ID,
    swissId: Swiss.Id,
    round: SwissRound.Number,
    p1: User.ID,
    p2: User.ID,
    status: SwissPairing.Status,
    isMicroMatch: Boolean,
    microMatchGameId: Option[Game.ID],
    openingFEN: Option[FEN]
) {
  def apply(c: SGPlayer)             = c.fold(p1, p2)
  def gameId                      = id
  def players                     = List(p1, p2)
  def has(userId: User.ID)        = p1 == userId || p2 == userId
  def sgPlayerOf(userId: User.ID)    = SGPlayer.fromP1(p1 == userId)
  def opponentOf(userId: User.ID) = if (p1 == userId) p2 else p1
  def winner: Option[User.ID]     = (~status.toOption).map(apply)
  def isOngoing                   = status.isLeft
  def resultFor(userId: User.ID)  = winner.map(userId.==)
  def p1Wins                   = status == Right(Some(SGPlayer.P1))
  def p2Wins                   = status == Right(Some(SGPlayer.P2))
  def isDraw                      = status == Right(None)
  def strResultOf(sgPlayer: SGPlayer)   = status.fold(_ => "*", _.fold("1/2")(c => if (c == sgPlayer) "1" else "0"))
}

case class SwissPairingGameIds(
  id: Game.ID,
  isMicroMatch: Boolean,
  microMatchGameId: Option[Game.ID],
  openingFEN: Option[FEN]
)

case class SwissPairingGames(
    swissId: Swiss.Id,
    game: Game,
    isMicroMatch: Boolean,
    microMatchGame: Option[Game],
    openingFEN: Option[FEN]
) {
  def finishedOrAborted =
    game.finishedOrAborted && (!isMicroMatch || microMatchGame.fold(false)(_.finishedOrAborted))
  def outoftime = if (game.outoftime(true)) List(game) else List() ++ microMatchGame.filter(_.outoftime(true))
  def winnerSGPlayer =
    // Single games are easy.
    if (!isMicroMatch) game.winnerSGPlayer
    else
      // We'll always report the game1 sgPlayer as the winner if they won, and if they haven't played the second
      // game yet, it's an unknown result.
      microMatchGame.flatMap(g2 =>
        (game.winnerSGPlayer, g2.winnerSGPlayer) match {
          // Same player winning both games is a win for that player
          case (Some(sgPlayer1), Some(sgPlayer2)) if (sgPlayer1 != sgPlayer2) => Some(sgPlayer1)
          // The first game was decisive, second game a draw, so first game winner sgPlayer is the winner
          case (Some(sgPlayer1), None)         => Some(sgPlayer1)
          // The second game was decisive, first game a draw, so second game winner's opposite sgPlayer is the winner
          case (None, Some(sgPlayer2))         => Some(!sgPlayer2)
          case _ => None
        }
      )
  def playersWhoDidNotMove =
    List() ++ game.playerWhoDidNotMove ++ microMatchGame.flatMap(_.playerWhoDidNotMove)
  def createdAt = microMatchGame.fold(game.createdAt)(_.createdAt)
}

object SwissPairing {

  implicit def toSwissPairingGameIds(swissPairing: SwissPairing): SwissPairingGameIds =
    SwissPairingGameIds(swissPairing.id, swissPairing.isMicroMatch, swissPairing.microMatchGameId, swissPairing.openingFEN)

  sealed trait Ongoing
  case object Ongoing extends Ongoing
  type Status = Either[Ongoing, Option[SGPlayer]]

  val ongoing: Status = Left(Ongoing)

  case class Pending(
      p1: User.ID,
      p2: User.ID
  )
  case class Bye(player: User.ID)

  type ByeOrPending = Either[Bye, Pending]

  type PairingMap = Map[User.ID, Map[SwissRound.Number, SwissPairing]]

  case class View(pairing: SwissPairing, player: SwissPlayer.WithUser)

  object Fields {
    val id               = "_id"
    val swissId          = "s"
    val round            = "r"
    val gameId           = "g"
    val players          = "p"
    val status           = "t"
    val isMicroMatch     = "mm"
    val microMatchGameId = "mmid"
    val openingFEN       = "of"
  }
  def fields[A](f: Fields.type => A): A = f(Fields)

  def toMap(pairings: List[SwissPairing]): PairingMap =
    pairings.foldLeft[PairingMap](Map.empty) { case (acc, pairing) =>
      pairing.players.foldLeft(acc) { case (acc, player) =>
        acc.updatedWith(player) { acc =>
          (~acc).updated(pairing.round, pairing).some
        }
      }
    }
}
