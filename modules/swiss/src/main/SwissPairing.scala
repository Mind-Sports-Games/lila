package lila.swiss

import strategygames.{ Player => PlayerIndex }
import strategygames.format.FEN
import strategygames.variant.Variant
import lila.game.Game
import lila.user.User

case class SwissPairing(
    id: Game.ID,
    swissId: Swiss.Id,
    round: SwissRound.Number,
    p1: User.ID,
    p2: User.ID,
    status: SwissPairing.Status,
    matchStatus: SwissPairing.MatchStatus,
    isMicroMatch: Boolean,
    microMatchGameId: Option[Game.ID],
    multiMatchGameIds: Option[List[Game.ID]],
    useMatchScore: Boolean,
    isBestOfX: Boolean,
    isPlayX: Boolean,
    nbGamesPerRound: Int,
    openingFEN: Option[FEN],
    variant: Option[Variant] = None
) {
  def apply(c: PlayerIndex)          = c.fold(p1, p2)
  def gameId                         = id
  def players                        = List(p1, p2)
  def has(userId: User.ID)           = p1 == userId || p2 == userId
  def playerIndexOf(userId: User.ID) = PlayerIndex.fromP1(p1 == userId)
  def opponentOf(userId: User.ID)    = if (p1 == userId) p2 else p1
  def winner: Option[User.ID]        = (~status.toOption).map(apply)
  def isOngoing                      = status.isLeft
  def resultFor(userId: User.ID)     = winner.map(userId.==)
  def p1Wins                         = status == Right(Some(PlayerIndex.P1))
  def p2Wins                         = status == Right(Some(PlayerIndex.P2))
  def isDraw                         = status == Right(None)
  // matchScoreFor returns a two digit score (or empty string)
  def matchScoreFor(userId: User.ID) =
    if (useMatchScore)
      matchStatus match {
        case Left(_) => "00"
        case Right(l) =>
          l.zipWithIndex
            .map { case (outcome, index) =>
              outcome.fold(1)(c =>
                if ( //players swap playerindex each game of multi match
                  (c == playerIndexOf(userId) && (index % 2 == 0)) || (c != playerIndexOf(
                    userId
                  ) && (index % 2 == 1))
                ) 2
                else 0
              )
            }
            .foldLeft(0)(_ + _)
            .toString()
            .reverse
            .padTo(2, '0')
            .reverse
      }
    else ""
  def strResultOf(playerIndex: PlayerIndex) =
    if (nbGamesPerRound == 1) status.fold(_ => "*", _.fold("1/2")(c => if (c == playerIndex) "1" else "0"))
    else {
      matchStatus
        .fold(
          _ => "*",
          _.zipWithIndex
            .map { case (outcome, index) =>
              outcome.fold(1)(c =>
                if ((c == playerIndex && (index % 2 == 0)) || (c != playerIndex && (index % 2 == 1))) 2 else 0
              )
            }
            .foldLeft(0)(_ + _)
            .toString()
        )
    }

}

case class SwissPairingGameIds(
    id: Game.ID,
    isMicroMatch: Boolean,
    microMatchGameId: Option[Game.ID],
    multiMatchGameIds: Option[List[Game.ID]],
    useMatchScore: Boolean,
    isBestOfX: Boolean,
    isPlayX: Boolean,
    nbGamesPerRound: Int,
    openingFEN: Option[FEN]
)

case class SwissPairingGames(
    swissId: Swiss.Id,
    game: Game,
    isMicroMatch: Boolean,
    microMatchGame: Option[Game],
    multiMatchGames: Option[List[Game]],
    useMatchScore: Boolean,
    isBestOfX: Boolean,
    isPlayX: Boolean,
    nbGamesPerRound: Int,
    openingFEN: Option[FEN]
) {
  def finishedOrAborted =
    game.finishedOrAborted && (!isMicroMatch || microMatchGame.fold(false)(
      _.finishedOrAborted
    )) && (!isBestOfX || !requireMoreGamesInBestOfX) && (!isPlayX || !requireMoreGamesInPlayX)
  def requireMoreGamesInBestOfX: Boolean = {
    val nbGamesLeft = nbGamesPerRound - (multiMatchGames.fold(0)(x => x.length) + 1);
    nbGamesLeft != 0 && ((
      multiMatchGames
        .foldLeft(List(game))(_ ++ _)
        .map(g => g.winnerPlayerIndex)
      )
      .zipWithIndex
      .map { case (outcome, index) =>
        outcome.fold(0)(playerIndex =>
          if (index % 2 == 0) {
            if (playerIndex == PlayerIndex.P1) 1 else -1
          } else {
            if (playerIndex == PlayerIndex.P2) 1 else -1
          }
        )
      }
      .foldLeft(0)(_ + _) match {
      case x if x > 0 => x <= nbGamesLeft
      case x if x < 0 => -x <= nbGamesLeft
      case _          => true
    })
  }
  def requireMoreGamesInPlayX: Boolean = {
    val nbGamesLeft = nbGamesPerRound - (multiMatchGames.fold(0)(x => x.length) + 1);
    nbGamesLeft != 0
  }
  def outoftime = if (game.outoftime(true)) List(game)
  else
    List() ++ microMatchGame.filter(_.outoftime(true)) ++ multiMatchGames.fold[List[Game]](List())(
      _.filter(_.outoftime(true))
    )
  def winnerPlayerIndex: Option[PlayerIndex] =
    // Single games are easy.
    if (nbGamesPerRound == 1) game.winnerPlayerIndex
    else if (isMicroMatch) {
      // We'll always report the game1 playerIndex as the winner if they won, and if they haven't played the second
      // game yet, it's an unknown result.
      microMatchGame.flatMap(g2 =>
        (game.winnerPlayerIndex, g2.winnerPlayerIndex) match {
          // Same player winning both games is a win for that player
          case (Some(playerIndex1), Some(playerIndex2)) if playerIndex1 != playerIndex2 => Some(playerIndex1)
          // The first game was decisive, second game a draw, so first game winner playerIndex is the winner
          case (Some(playerIndex1), None) => Some(playerIndex1)
          // The second game was decisive, first game a draw, so second game winner's opposite playerIndex is the winner
          case (None, Some(playerIndex2)) => Some(!playerIndex2)
          case _                          => None
        }
      )
    } else { //multimatch
      (
        multiMatchGames
          .foldLeft(List(game))(_ ++ _)
          .map(g => g.winnerPlayerIndex)
        )
        .zipWithIndex
        .map { case (outcome, index) =>
          outcome.fold(0)(playerIndex =>
            if (index % 2 == 0) {
              if (playerIndex == PlayerIndex.P1) 1 else -1
            } else {
              if (playerIndex == PlayerIndex.P2) 1 else -1
            }
          )
        }
        .foldLeft(0)(_ + _) match {
        case x if x > 0 => Some(PlayerIndex.P1)
        case x if x < 0 => Some(PlayerIndex.P2)
        case _          => None
      }
    }
  def playersWhoDidNotMove =
    List() ++ game.playerWhoDidNotMove ++ microMatchGame.flatMap(_.playerWhoDidNotMove) ++ multiMatchGames
      .flatMap(_.last.playerWhoDidNotMove)
  def createdAt = if (isMicroMatch) {
    microMatchGame.fold(game.createdAt)(_.createdAt)
  } else if (isBestOfX || isPlayX) {
    multiMatchGames.fold(game.createdAt)(_.last.createdAt)
  } else game.createdAt
  def matchOutcome: List[Option[PlayerIndex]] =
    if (useMatchScore && isMicroMatch) {
      microMatchGame match {
        case Some(g2) => List(game.winnerPlayerIndex, g2.winnerPlayerIndex)
        case None     => List(game.winnerPlayerIndex, None)
      }
    } else if (nbGamesPerRound > 1) {
      multiMatchGames.foldLeft(List(game))(_ ++ _).map(_.winnerPlayerIndex)
    } else List(game.winnerPlayerIndex)
}

object SwissPairing {

  implicit def toSwissPairingGameIds(swissPairing: SwissPairing): SwissPairingGameIds =
    SwissPairingGameIds(
      swissPairing.id,
      swissPairing.isMicroMatch,
      swissPairing.microMatchGameId,
      swissPairing.multiMatchGameIds,
      swissPairing.useMatchScore,
      swissPairing.isBestOfX,
      swissPairing.isPlayX,
      swissPairing.nbGamesPerRound,
      swissPairing.openingFEN
    )

  sealed trait Ongoing
  case object Ongoing extends Ongoing
  type Status = Either[Ongoing, Option[PlayerIndex]]
  // The right side of either denotes the list of results for a multi match (Win by playerIndex or none for Draw)
  type MatchStatus = Either[Ongoing, List[Option[PlayerIndex]]]

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
    val id                = "_id"
    val swissId           = "s"
    val round             = "r"
    val gameId            = "g"
    val players           = "p"
    val status            = "t"
    val matchStatus       = "mt"
    val isMicroMatch      = "mm"
    val microMatchGameId  = "mmid"
    val multiMatchGameIds = "mmids"
    val useMatchScore     = "ms"
    val isBestOfX         = "x"
    val isPlayX           = "px"
    val nbGamesPerRound   = "gpr"
    val openingFEN        = "of"
    val variant           = "v"
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
