package lila.swiss

import strategygames.{ Player => PlayerIndex, GameFamily, GameLogic, Status => SGStatus }
import strategygames.format.FEN
import strategygames.variant.Variant
import lila.game.{ Game, MultiPointState }
import lila.user.User

case class SwissPairing(
    id: Game.ID,
    swissId: Swiss.Id,
    round: SwissRound.Number,
    p1: User.ID,
    p2: User.ID,
    bbpPairingP1: User.ID,
    status: SwissPairing.Status,
    matchStatus: SwissPairing.MatchStatus,
    startPlayerWinners: Option[SwissPairing.MatchStatus],
    multiMatchGameIds: Option[List[Game.ID]],
    isMatchScore: Boolean,
    isBestOfX: Boolean,
    isPlayX: Boolean,
    nbGamesPerRound: Int,
    openingFEN: Option[FEN],
    variant: Option[Variant] = None
) {
  def apply(c: PlayerIndex)                    = c.fold(p1, p2)
  def gameId                                   = id
  def players                                  = List(p1, p2)
  def has(userId: User.ID)                     = p1 == userId || p2 == userId
  def playerIndexOf(userId: User.ID)           = PlayerIndex.fromP1(p1 == userId)
  def bbpPairingPlayerIndexOf(userId: User.ID) = PlayerIndex.fromP1(bbpPairingP1 == userId)
  def opponentOf(userId: User.ID)              = if (p1 == userId) p2 else p1
  def winner: Option[User.ID]                  = (~status.toOption).map(apply)
  def isOngoing                                = status.isLeft
  def resultFor(userId: User.ID)               = winner.map(userId.==)
  def p1Wins                                   = status == Right(Some(PlayerIndex.P1))
  def p2Wins                                   = status == Right(Some(PlayerIndex.P2))
  def isDraw                                   = status == Right(None)

  def numFirstPlayerWins =
    startPlayerWinners.fold(matchStatus.fold(_ => 0, l => l.count(Some(PlayerIndex.P1).==)))(spw =>
      spw.fold(_ => 0, s => s.count(Some(PlayerIndex.P1).==))
    )
  def numSecondPlayerWins =
    startPlayerWinners.fold(matchStatus.fold(_ => 0, l => l.count(Some(PlayerIndex.P2).==)))(spw =>
      spw.fold(_ => 0, s => s.count(Some(PlayerIndex.P2).==))
    )
  def numDraws = matchStatus.fold(_ => 0, l => l.count(None.==))
  def numGames = matchStatus.fold(_ => 0, l => l.length)

  def multiMatchResultsFor(userId: User.ID): Option[List[String]] = {
    if (nbGamesPerRound > 1 || multiMatchGameIds.nonEmpty)
      matchStatus.fold(
        _ => None,
        SwissPairing.matchResultsMap(variant)("draw", "win", "loss")(playerIndexOf(userId))(_).some
      )
    else None
  }

  // matchScoreFor returns a two digit score (or empty string)
  def matchScoreFor(userId: User.ID) =
    if (isMatchScore)
      matchStatus.fold(
        _ => "00",
        SwissPairing
          .matchResultsMap(variant)(1, 2, 0)(playerIndexOf(userId))(_)
          .foldLeft(0)(_ + _)
          .toString()
          .reverse
          .padTo(2, '0')
          .reverse
      )
    else ""

  def strResultOf(playerIndex: PlayerIndex) =
    if (nbGamesPerRound == 1)
      status.fold(_ => "*", _.fold("1/2")(c => if (c == playerIndex) "1" else "0"))
    else {
      matchStatus
        .fold(
          _ => "*",
          SwissPairing
            .matchResultsMap(variant)(1, 2, 0)(playerIndex)(_)
            .foldLeft(0)(_ + _)
            .toString()
        )
    }

  //works because we can't change variant midway through a multipoint match
  //TODO convert fenFromSetupConfig into a wrapped function
  def fenForNextGame(prevGame: Game): Option[FEN] =
    if (prevGame.metadata.multiPointState.isEmpty) openingFEN
    else
      prevGame.variant match {
        case Variant.Backgammon(v) =>
          Some(
            FEN(
              GameLogic.Backgammon(),
              v.fenFromSetupConfig(!MultiPointState.nextGameIsCrawford(prevGame)).value
            )
          )
        case _ => openingFEN
      }

}

case class SwissPairingGameIds(
    id: Game.ID,
    multiMatchGameIds: Option[List[Game.ID]],
    isMatchScore: Boolean,
    isBestOfX: Boolean,
    isPlayX: Boolean,
    nbGamesPerRound: Int,
    openingFEN: Option[FEN]
)

case class SwissPairingGames(
    swissId: Swiss.Id,
    game: Game,
    multiMatchGames: Option[List[Game]],
    isMatchScore: Boolean,
    isBestOfX: Boolean,
    isPlayX: Boolean,
    nbGamesPerRound: Int,
    openingFEN: Option[FEN]
) {

  def lastGame: Game =
    multiMatchGames.fold(game)(_.reverse.headOption.getOrElse(game))

  def isMultiPoint: Boolean = game.metadata.multiPointState.nonEmpty

  def finishedOrAborted =
    game.finishedOrAborted &&
      (!isBestOfX || !requireMoreGamesInBestOfX) &&
      (!isPlayX || !requireMoreGamesInPlayX) &&
      !requireMoreGamesInMultipoint

  private def multiMatchGamesScoreDiff: Int =
    multiMatchGames
      .foldLeft(List(game))(_ ++ _)
      .map(g => g.winnerPlayerIndex)
      .zipWithIndex
      .map { case (outcome, index) =>
        outcome.fold(0)(playerIndex =>
          game.variant match {
            case v if v.gameFamily == GameFamily.Backgammon() => if (playerIndex == PlayerIndex.P1) 1 else -1
            case _ => {
              if (index % 2 == 0) {
                if (playerIndex == PlayerIndex.P1) 1 else -1
              } else {
                if (playerIndex == PlayerIndex.P2) 1 else -1
              }
            }
          }
        )
      }
      .foldLeft(0)(_ + _)

  private def nbGamesLeft = nbGamesPerRound - (multiMatchGames.fold(0)(_.length) + 1)

  def requireMoreGamesInBestOfX: Boolean =
    nbGamesLeft != 0 && (multiMatchGamesScoreDiff match {
      case x if x > 0 => x <= nbGamesLeft
      case x if x < 0 => -x <= nbGamesLeft
      case _          => true
    })

  def requireMoreGamesInPlayX: Boolean = nbGamesLeft != 0

  def requireMoreGamesInMultipoint: Boolean =
    MultiPointState.requireMoreGamesInMultipoint(lastGame)

  def outoftime = if (game.outoftime(true)) List(game)
  else
    List() ++ multiMatchGames.fold[List[Game]](List())(
      _.filter(_.outoftime(true))
    )

  def winnerPlayerIndex: Option[PlayerIndex] =
    if (nbGamesPerRound > 1) { //multimatch
      multiMatchGamesScoreDiff match {
        case x if x > 0 => Some(PlayerIndex.P1)
        case x if x < 0 => Some(PlayerIndex.P2)
        case _          => None
      }
    } else if (
      isMultiPoint && List(SGStatus.RuleOfGin, SGStatus.GinGammon, SGStatus.GinBackgammon).contains(
        lastGame.status
      )
    ) {
      lastGame.metadata.multiPointState.flatMap { mps =>
        lastGame.winnerPlayerIndex.map { p =>
          if (
            (if (p == PlayerIndex.P1) mps.p1Points else mps.p2Points) + lastGame.pointValue.getOrElse(
              0
            ) >= mps.target
          )
            p
          else !p
        }
      }
    } else lastGame.winnerPlayerIndex

  def playersWhoDidNotMove = lastGame.playersWhoDidNotMove

  def createdAt = if (isBestOfX || isPlayX) {
    multiMatchGames.fold(game.createdAt)(_.last.createdAt)
  } else game.createdAt

  def matchOutcome: List[Option[PlayerIndex]] =
    if (nbGamesPerRound > 1 || multiMatchGames.exists(_.length > 0)) {
      multiMatchGames.foldLeft(List(game))(_ ++ _).map(_.winnerPlayerIndex)
    } else List(lastGame.winnerPlayerIndex)

  private def startPlayerNormalisation(g: Game): Option[PlayerIndex] =
    if (g.startPlayerIndex == PlayerIndex.P2 && g.variant.recalcStartPlayerForStats)
      g.winnerPlayerIndex.map(!_)
    else
      g.winnerPlayerIndex

  def startPlayerWinners: List[Option[PlayerIndex]] =
    if (nbGamesPerRound > 1 || multiMatchGames.exists(_.length > 0)) {
      multiMatchGames.foldLeft(List(game))(_ ++ _).map(g => startPlayerNormalisation(g))
    } else List(startPlayerNormalisation(lastGame))

  def strResultOf(playerIndex: PlayerIndex) =
    if (isMultiPoint) {
      lastGame.metadata.multiPointState
        .fold(0) { mps =>
          (lastGame.status, lastGame.winnerPlayerIndex == Some(playerIndex)) match {
            case (s, true)
                if List(SGStatus.RuleOfGin, SGStatus.GinGammon, SGStatus.GinBackgammon).contains(s) =>
              if (
                playerIndex.fold(mps.p1Points, mps.p2Points) + lastGame.pointValue.getOrElse(0) < mps.target
              ) {
                playerIndex.fold(mps.p1Points, mps.p2Points) + lastGame.pointValue.getOrElse(0)
              } else {
                mps.target
              }
            case (s, false)
                if List(SGStatus.RuleOfGin, SGStatus.GinGammon, SGStatus.GinBackgammon).contains(s) =>
              if (
                (!playerIndex)
                  .fold(mps.p1Points, mps.p2Points) + lastGame.pointValue.getOrElse(0) < mps.target
              ) {
                mps.target
              } else {
                playerIndex.fold(mps.p1Points, mps.p2Points)
              }
            case (s, true) if SGStatus.flagged.contains(s)  => mps.target
            case (s, false) if SGStatus.flagged.contains(s) => playerIndex.fold(mps.p1Points, mps.p2Points)
            case (_, true) =>
              Math.min(
                playerIndex.fold(mps.p1Points, mps.p2Points) + lastGame.pointValue.getOrElse(0),
                mps.target
              )
            case (_, false) => playerIndex.fold(mps.p1Points, mps.p2Points)
          }
        }
        .toString()
    } else
      SwissPairing
        .matchResultsMap(game.variant.some)(1, 2, 0)(playerIndex)(
          multiMatchGames
            .foldLeft(List(game))(_ ++ _)
            .filter(g => g.finished)
            .map(g => g.winnerPlayerIndex)
        )
        .foldLeft(0)(_ + _) match {
        case x if x % 2 == 0 => s"${(x / 2)}"
        case x if x % 2 == 1 => s"${(x / 2)}.5"
        case _ => "*"
      }

}

object SwissPairing {

  implicit def toSwissPairingGameIds(swissPairing: SwissPairing): SwissPairingGameIds =
    SwissPairingGameIds(
      swissPairing.id,
      swissPairing.multiMatchGameIds,
      swissPairing.isMatchScore,
      swissPairing.isBestOfX,
      swissPairing.isPlayX,
      swissPairing.nbGamesPerRound,
      swissPairing.openingFEN
    )

  def matchResultsMap[A](variant: Option[Variant])(draw: A, win: A, loss: A)(
      playerIndex: PlayerIndex
  )(l: List[Option[PlayerIndex]]): List[A] = {
    l.zipWithIndex.map { case (outcome, index) =>
      outcome.fold(draw)(c =>
        variant match {
          case Some(v) if v.gameFamily == GameFamily.Backgammon() => if (c == playerIndex) win else loss
          case _ => {
            if ( //players swap playerindex each game of multi match
              (c == playerIndex && (index % 2 == 0)) || (c != playerIndex && (index % 2 == 1))
            ) win
            else loss
          }
        }
      )
    }
  }

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
    val id                 = "_id"
    val swissId            = "s"
    val round              = "r"
    val gameId             = "g"
    val players            = "p"
    val bbpPairingP1       = "bbp"
    val status             = "t"
    val matchStatus        = "mt"
    val startPlayerWinners = "spw"
    val multiMatchGameIds  = "mmids"
    val isMatchScore       = "ms"
    val isBestOfX          = "x"
    val isPlayX            = "px"
    val nbGamesPerRound    = "gpr"
    val openingFEN         = "of"
    val variant            = "v"
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
