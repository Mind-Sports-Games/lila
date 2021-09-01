package lila.swiss

import strategygames.Color
import lila.game.Game
import lila.user.User

case class SwissPairing(
    id: Game.ID,
    swissId: Swiss.Id,
    round: SwissRound.Number,
    white: User.ID,
    black: User.ID,
    status: SwissPairing.Status,
    isMicroMatch: Boolean,
    microMatchGameId: Option[Game.ID]
) {
  def apply(c: Color)             = c.fold(white, black)
  def gameId                      = id
  def players                     = List(white, black)
  def has(userId: User.ID)        = white == userId || black == userId
  def colorOf(userId: User.ID)    = Color.fromWhite(white == userId)
  def opponentOf(userId: User.ID) = if (white == userId) black else white
  def winner: Option[User.ID]     = (~status.toOption).map(apply)
  def isOngoing                   = status.isLeft
  def resultFor(userId: User.ID)  = winner.map(userId.==)
  def whiteWins                   = status == Right(Some(Color.White))
  def blackWins                   = status == Right(Some(Color.Black))
  def isDraw                      = status == Right(None)
  def strResultOf(color: Color)   = status.fold(_ => "*", _.fold("1/2")(c => if (c == color) "1" else "0"))
}

case class SwissPairingGameIds(id: Game.ID, isMicroMatch: Boolean, microMatchGameId: Option[Game.ID])
case class SwissPairingGames(
    swissId: Swiss.Id,
    game: Game,
    isMicroMatch: Boolean,
    microMatchGame: Option[Game]
) {
  def finishedOrAborted =
    game.finishedOrAborted && (!isMicroMatch || microMatchGame.fold(false)(_.finishedOrAborted))
  def outoftime = if (game.outoftime(true)) List(game) else List() ++ microMatchGame.filter(_.outoftime(true))
  // TODO: properly test this function.
  def winnerColor =
    // Single games are easy.
    if (!isMicroMatch) game.winnerColor
    else
      // We'll always report the game1 color as the winner if they won, and if they haven't played the second
      // game yet, it's an unknown result.
      microMatchGame.flatMap(g2 =>
        (game.winnerColor, g2.winnerColor) match {
          // Same player winning both games is a win for that player
          case (Some(color1), Some(color2)) if (color1 == color2) => Some(color1)
          // The first game was decisive, second game a draw, so first game winner color is the winner
          case (Some(color1), None)         => Some(color1)
          // The second game was decisive, first game a draw, so second game winner's opposite color is the winner
          case (None, Some(color2))         => Some(!color2)
          case _ => None
        }
      )
  def playersWhoDidNotMove =
    List() ++ game.playerWhoDidNotMove ++ microMatchGame.flatMap(_.playerWhoDidNotMove)
  def createdAt = microMatchGame.fold(game.createdAt)(_.createdAt)
}

object SwissPairing {

  implicit def toSwissPairingGameIds(swissPairing: SwissPairing): SwissPairingGameIds =
    SwissPairingGameIds(swissPairing.id, swissPairing.isMicroMatch, swissPairing.microMatchGameId)

  sealed trait Ongoing
  case object Ongoing extends Ongoing
  type Status = Either[Ongoing, Option[Color]]

  val ongoing: Status = Left(Ongoing)

  case class Pending(
      white: User.ID,
      black: User.ID
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
