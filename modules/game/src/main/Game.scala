package lila.game

import scala.annotation.nowarn

import strategygames.format.{ FEN, Forsyth, Uci }
import strategygames.opening.{ FullOpening, FullOpeningDB }
import strategygames.chess.{ Castles, CheckCount }
import strategygames.chess.format.{ Uci => ChessUci }
import strategygames.{
  Action,
  ActionStrs,
  Centis,
  ByoyomiClock,
  Clock,
  ClockConfig,
  ClockBase,
  Player => PlayerIndex,
  Game => StratGame,
  GameLogic,
  GameFamily,
  Mode,
  MultiPointState => StratMultiPointState,
  Move,
  Drop,
  Lift,
  Pass,
  DiceRoll,
  CubeAction,
  EndTurn,
  Undo,
  SelectSquares,
  Pos,
  Speed,
  Status,
  P1,
  P2,
  Score,
  Situation
}
import strategygames.variant.Variant
import org.joda.time.DateTime

import lila.common.Sequence
import lila.db.ByteArray
import lila.rating.PerfType
import lila.user.User
import lila.i18n.{ I18nKeys => trans }
import play.api.i18n.Lang

case class Game(
    id: Game.ID,
    p1Player: Player,
    p2Player: Player,
    stratGame: StratGame,
    loadClockHistory: ClockBase => Option[ClockHistory] = Game.someEmptyClockHistory,
    status: Status,
    daysPerTurn: Option[Int],
    binaryPlyTimes: Option[ByteArray] = None,
    mode: Mode = Mode.default,
    bookmarks: Int = 0,
    createdAt: DateTime = DateTime.now,
    updatedAt: DateTime = DateTime.now,
    turnAt: DateTime = DateTime.now,
    metadata: Metadata
) {

  lazy val clockHistory = stratGame.clock.flatMap(loadClockHistory)

  def situation    = stratGame.situation
  def board        = stratGame.situation.board
  def history      = stratGame.situation.board.history
  def variant      = stratGame.situation.board.variant
  def turnCount    = stratGame.turnCount
  def plies        = stratGame.plies
  def clock        = stratGame.clock
  def actionStrs   = stratGame.actionStrs
  def activePlayer = stratGame.situation.player

  val gameRecordFormat = variant match {
    case Variant.FairySF(_) | Variant.Go(_) | Variant.Backgammon(_) => "sgf"
    case _                                                          => "pgn"
  }

  val players = List(p1Player, p2Player)

  def player(playerIndex: PlayerIndex): Player = playerIndex.fold(p1Player, p2Player)

  def player(playerId: Player.ID): Option[Player] =
    players find (_.id == playerId)

  def player(user: User): Option[Player] =
    players find (_ isUser user)

  def player(c: PlayerIndex.type => PlayerIndex): Player = player(c(PlayerIndex))

  def isPlayerFullId(player: Player, fullId: String): Boolean =
    (fullId.lengthIs == Game.fullIdSize) && player.id == (fullId drop Game.gameIdSize)

  def player: Player = player(turnPlayerIndex)

  def playerByUserId(userId: User.ID): Option[Player]   = players.find(_.userId contains userId)
  def opponentByUserId(userId: User.ID): Option[Player] = playerByUserId(userId) map opponent

  def hasUserIds(userId1: User.ID, userId2: User.ID) =
    playerByUserId(userId1).isDefined && playerByUserId(userId2).isDefined

  def opponent(p: Player): Player = opponent(p.playerIndex)

  def opponent(c: PlayerIndex): Player = player(!c)

  lazy val naturalOrientation = P1

  def turnPlayerIndex = stratGame.player

  //For the front end - whose 'turn' is it? (SG + Go select squares status)
  def activePlayerIndex = playerToOfferSelectSquares.getOrElse(turnPlayerIndex)

  def turnOf(p: Player): Boolean      = p == player
  def turnOf(c: PlayerIndex): Boolean = c == turnPlayerIndex
  def turnOf(u: User): Boolean        = player(u) ?? turnOf

  def playedTurns = turnCount - stratGame.startedAtTurn
  //once draughts is converted to multiaction we should be able to use actionStrs.flatten.size
  def playedPlies = plies - stratGame.startedAtPly

  def flagged = (Status.flagged.contains(status)).option(turnPlayerIndex)

  def fullIdOf(player: Player): Option[String] =
    (players contains player) option s"$id${player.id}"

  def fullIdOf(playerIndex: PlayerIndex): String = s"$id${player(playerIndex).id}"

  def swapPlayersOnRematch: Boolean =
    variant.key != "backgammon" && variant.key != "hyper" && variant.key != "nackgammon"

  def fromHandicappedTournament = metadata.fromHandicappedTournament
  def tournamentId              = metadata.tournamentId
  def simulId                   = metadata.simulId
  def swissId                   = metadata.swissId
  def multiPointState           = metadata.multiPointState

  def isTournament = tournamentId.isDefined
  def isSimul      = simulId.isDefined
  def isSwiss      = swissId.isDefined
  def isMandatory  = isTournament || isSimul || isSwiss
  def isClassical = perfType match {
    case Some(pt) => pt.key == "classical"
    case _        => false
  }
  def nonMandatory = !isMandatory

  def hasChat = !isTournament && !isSimul && nonAi

  // we can't rely on the clock, because if moretime was given,
  // elapsed time is no longer representing the game duration
  def durationSeconds: Option[Int] =
    updatedAt.getSeconds - createdAt.getSeconds match {
      case seconds if seconds > 60 * 60 * 12 => none // no way it lasted more than 12 hours, come on.
      case seconds                           => seconds.toInt.some
    }

  private def everyOther[A](l: List[A]): List[A] =
    l match {
      case a :: _ :: tail => a :: everyOther(tail)
      case _              => l
    }

  def plyTimes(playerIndex: PlayerIndex): Option[List[Centis]] = {
    for {
      clk <- clock
      grace = Centis(clk.graceOf(playerIndex) * 100)
      byo = clk match {
        case bc: ByoyomiClock => bc.byoyomiOf(playerIndex)
        case _                => Centis(0)
      }
      history <- clockHistory
      plyTimes = history
        .plyTimes(
          playerIndex,
          turnPlayerIndex,
          playedPlies,
          stratGame.startedAtTurn,
          finished,
          status,
          grace,
          byo
        )
    } yield plyTimes
  } orElse binaryPlyTimes.map { binary =>
    // Thibault TODO: make plyTime.read return List after writes are disabled.
    //TODO fix for multiaction, when does this get called?
    val base = BinaryFormat.plyTime.read(binary, playedPlies)
    val mts  = if (playerIndex == startPlayerIndex) base else base.drop(1)
    everyOther(mts.toList)
  }

  def plyTimes: Option[Vector[Centis]] =
    for {
      a <- plyTimes(startPlayerIndex)
      b <- plyTimes(!startPlayerIndex)
    } yield {
      val who =
        actionStrs.zipWithIndex.flatMap { case (t, i) =>
          t.map(_ => { if (i % 2 == 0) "a" else "b" })
        }
      Game.combinePlyTimes(a, b, who, Vector.empty)
    }

  def bothClockStates: Option[Vector[Centis]] = {
    val who: Vector[String] =
      actionStrs.zipWithIndex.flatMap { case (t, i) =>
        t.map(_ => { if (i % 2 == 0) "a" else "b" })
      }
    clockHistory.map(_.bothClockStates(startPlayerIndex, who))
  }

  def draughtsActionStrsConcat(fullCaptures: Boolean = false, dropGhosts: Boolean = false): ActionStrs =
    stratGame match {
      case StratGame.Draughts(game) => game.actionStrsConcat(fullCaptures, dropGhosts)
      case _                        => sys.error("Cant call actionStrsConcat for a gamelogic other than draughts")
    }

  def actionStrs(playerIndex: PlayerIndex): ActionStrs = {
    val pivot = if (playerIndex == startPlayerIndex) 0 else 1
    (variant.gameLogic match {
      case GameLogic.Draughts() => draughtsActionStrsConcat()
      case _                    => actionStrs
    }).zipWithIndex.collect {
      case (e, i) if (i % 2) == pivot => e
    }
  }

  // apply an action
  def update(
      game: StratGame, // new sg game position
      action: Action,
      blur: Boolean = false
  ): Progress = {

    def copyPlayer(player: Player) =
      if (blur && action.player == player.playerIndex)
        player.copy(
          blurs = player.blurs.add(playerMoves(player.playerIndex))
        )
      else player

    // This must be computed eagerly
    // because it depends on the current time
    val newClockHistory = for {
      clk <- game.clock
      ch  <- clockHistory
    } yield ch.record(turnPlayerIndex, clk, stratGame.fullTurnCount)

    def deadStoneOfferStateAfterAction: Option[DeadStoneOfferState] = {
      game.situation match {
        case Situation.Go(s) if s.canSelectSquares => DeadStoneOfferState.ChooseFirstOffer.some
        case _                                     => None
      }
    }

    val updated = copy(
      p1Player = copyPlayer(p1Player),
      p2Player = copyPlayer(p2Player),
      stratGame = game,
      binaryPlyTimes = (!isPgnImport && stratGame.clock.isEmpty).option {
        BinaryFormat.plyTime.write {
          binaryPlyTimes.?? { t =>
            BinaryFormat.plyTime.read(t, playedPlies)
          } :+ Centis(nowCentis - updatedAt.getCentis).nonNeg
        }
      },
      loadClockHistory = action match {
        case _: Undo => (_ => clockHistory.map(_.update(turnPlayerIndex, _.dropRight(1))))
        case _       => (_ => newClockHistory)
      },
      status = game.situation.status | status,
      updatedAt = DateTime.now,
      turnAt = if (game.hasJustSwitchedTurns) DateTime.now else turnAt,
      metadata = metadata.copy(deadStoneOfferState = deadStoneOfferStateAfterAction)
    )

    val state = Event.State(
      playerIndex = game.situation.player,
      turnCount = game.turnCount,
      plies = game.plies,
      status = (status != updated.status) option updated.status,
      winner = game.situation.winner,
      p1OffersDraw = p1Player.isOfferingDraw,
      p2OffersDraw = p2Player.isOfferingDraw,
      p1OffersSelectSquares = p1Player.isOfferingSelectSquares,
      p2OffersSelectSquares = p2Player.isOfferingSelectSquares
    )

    val clockEvent = updated.stratGame.clock map Event.Clock.apply orElse {
      updated.playableCorrespondenceClock map Event.CorrespondenceClock.apply
    }

    val events = {
      action match {
        case m: Move =>
          Event.Move(m, game.situation, state, clockEvent, updated.board.pocketData)
        case d: Drop =>
          Event.Drop(d, game.situation, state, clockEvent, updated.board.pocketData)
        case l: Lift =>
          Event.Lift(l, game.situation, state, clockEvent, updated.board.pocketData)
        case p: Pass =>
          Event.Pass(p, game.situation, state, clockEvent, updated.board.pocketData)
        case r: DiceRoll =>
          Event.DiceRoll(r, game.situation, state, clockEvent, updated.board.pocketData)
        case ca: CubeAction =>
          Event.CubeAction(ca, game.situation, state, clockEvent, updated.board.pocketData)
        case et: EndTurn =>
          Event.EndTurn(et, game.situation, state, clockEvent, updated.board.pocketData)
        case u: Undo =>
          Event.Undo(u, game.situation, state, clockEvent, updated.board.pocketData)
        case ss: SelectSquares =>
          Event.SelectSquares(ss, game.situation, state, clockEvent, updated.board.pocketData)
      }
    } :: {
      // abstraction leak, I know.
      if (updated.board.variant.gameLogic == GameLogic.Draughts())
        (updated.board.variant.frisianVariant || updated.board.variant.draughts64Variant) ?? List(
          Event.KingMoves(
            p1 = updated.history.kingMoves.p1,
            p2 = updated.history.kingMoves.p2,
            p1King = updated.history.kingMoves.p1King.map(Pos.Draughts),
            p2King = updated.history.kingMoves.p2King.map(Pos.Draughts)
          )
        )
      else if (updated.board.variant.gameLogic == GameLogic.Togyzkumalak())
        //Is this even necessary as score is in the fen?
        (updated.board.variant.gameFamily == GameFamily.Togyzkumalak()) ?? List(
          Event.Score(p1 = updated.history.score.p1, p2 = updated.history.score.p2)
        )
      else if (updated.board.variant.gameLogic == GameLogic.Backgammon())
        //Is this even necessary as score is in the fen?
        (updated.board.variant.gameFamily == GameFamily.Backgammon()) ?? List(
          Event.Score(p1 = updated.history.score.p1, p2 = updated.history.score.p2)
        )
      // TODO Abalone is this how we want to represent score? Maybe look at Backgammon
      else if (updated.board.variant.gameLogic == GameLogic.Abalone())
        //Is this even necessary as score is in the fen?
        (updated.board.variant.key == "abalone") ?? List(
          Event.Score(p1 = updated.history.score.p1, p2 = updated.history.score.p2)
        )
      else //chess. Is this even necessary as checkCount is in the fen?
        ((updated.board.variant.key == "threeCheck" || updated.board.variant.key == "fiveCheck") && game.situation.check) ?? List(
          Event.CheckCount(
            p1 = updated.history.checkCount.p1,
            p2 = updated.history.checkCount.p2
          )
        )
    }

    Progress(this, updated, events)
  }

  def playerScores: List[String] =
    if (calculateScore(P1) == "" || calculateScore(P2) == "") List()
    else List(calculateScore(P1), calculateScore(P2))

  def calculateScore(playerIndex: PlayerIndex): String =
    variant.key match {
      case "flipello" | "flipello10" | "antiflipello" | "octagonflipello" =>
        board.pieces
          .map { case (_, (piece, _)) => piece.player.name }
          .filter(p => p == playerIndex.name)
          .size
          .toString()
      case "threeCheck" | "fiveCheck" =>
        history.checkCount(opponent(playerIndex).playerIndex).toString()
      case "oware" =>
        val fen   = Forsyth.>>(variant.gameLogic, situation)
        val score = if (playerIndex.name == "p1") fen.player1Score else fen.player2Score
        score.toString()
      case "togyzkumalak" | "bestemshe" => history.score(playerIndex).toString()
      case "abalone"                    => history.score(playerIndex).toString()
      case "go9x9" | "go13x13" | "go19x19" =>
        val fen   = Forsyth.>>(variant.gameLogic, situation)
        val score = (if (playerIndex.name == "p1") fen.player1Score else fen.player2Score) / 10.0
        score.toString().replace(".0", "")
      case "backgammon" | "hyper" | "nackgammon" => {
        multiPointState
          .fold(history.score(playerIndex)) { mps => playerIndex.fold(mps.p1Points, mps.p2Points) }
          .toString()
      }
      case _ => ""
    }

  def displayScore: Option[Score] =
    if (
      variant.gameLogic == GameLogic.Togyzkumalak() || variant.gameLogic == GameLogic
        .Backgammon() || variant.gameLogic == GameLogic.Abalone()
    )
      history.score.some
    else if (variant.gameLogic == GameLogic.Go()) {
      if (finished || selectSquaresPossible) history.score.some
      else history.captures.some
    } else none

  def lastActionKeys: Option[String] =
    history.lastAction map {
      case d: Uci.Drop          => s"@${d.pos}"
      case m: Uci.Move          => m.keys
      case l: Uci.Lift          => s"${l.pos}${l.pos}"
      case _: Uci.EndTurn       => "endturn"
      case _: Uci.Undo          => "undo"
      case _: Uci.Pass          => "pass"
      case _: Uci.DiceRoll      => "roll"
      case _: Uci.CubeAction    => "cube"
      case _: Uci.SelectSquares => "ss:"
      case _                    => sys.error("Type Error")
    }

  def updatePlayer(playerIndex: PlayerIndex, f: Player => Player) =
    playerIndex.fold(
      copy(p1Player = f(p1Player)),
      copy(p2Player = f(p2Player))
    )

  def updatePlayers(f: Player => Player) =
    copy(
      p1Player = f(p1Player),
      p2Player = f(p2Player)
    )

  def start =
    if (started) this
    else
      copy(
        status = Status.Started,
        mode = Mode(mode.rated && userIds.distinct.size == 2)
      )

  def startClock =
    clock map { c =>
      start.withClock(c.start)
    }

  def correspondenceClock: Option[CorrespondenceClock] =
    daysPerTurn map { days =>
      val increment   = days * 24 * 60 * 60
      val secondsLeft = (updatedAt.getSeconds + increment - nowSeconds).toInt max 0
      CorrespondenceClock(
        increment = increment,
        p1Time = activePlayerIndex.fold(secondsLeft, increment).toFloat,
        p2Time = activePlayerIndex.fold(increment, secondsLeft).toFloat
      )
    }

  def playableCorrespondenceClock: Option[CorrespondenceClock] =
    playable ?? correspondenceClock

  def speed = Speed(stratGame.clock.map(_.config))

  def perfKey  = PerfPicker.key(this)
  def perfType = PerfType(perfKey)

  def started = status >= Status.Started

  def notStarted = !started

  def aborted = status == Status.Aborted

  def playedThenAborted = aborted && bothPlayersHaveMoved

  def abort = copy(status = Status.Aborted)

  def playable = status < Status.Aborted && !imported

  def playableEvenImported = status < Status.Aborted

  def playableBy(p: Player): Boolean = playable && turnOf(p)

  def playableBy(c: PlayerIndex): Boolean = playableBy(player(c))

  def playableByAi: Boolean = playable && (player.isAi || player.isPSBot)

  def mobilePushable = isCorrespondence && playable && nonAi

  def alarmable = hasCorrespondenceClock && playable && nonAi

  def continuable =
    status != Status.Mate && status != Status.PerpetualCheck && status != Status.Stalemate

  def aiLevel: Option[Int] = players find (_.isAi) flatMap (_.aiLevel)

  def hasAi: Boolean = players.exists(_.isAi)
  def nonAi          = !hasAi

  def hasPSBot: Boolean = players.exists(_.isPSBot)

  def aiPov: Option[Pov] = players.find(_.isAi).map(_.playerIndex) map pov

  def mapPlayers(f: Player => Player) =
    copy(
      p1Player = f(p1Player),
      p2Player = f(p2Player)
    )

  def multiPointResult: Option[MultiPointState] =
    metadata.multiPointState.flatMap { mps =>
      if (finished) finalScoreMultiPointState else Some(mps)
    }

  // style "copy pasted" from a ts function
  def finalScoreMultiPointState: Option[MultiPointState] = {
    val points2Add: Array[Int] =
      if (pointValue.isDefined && winner.isDefined)
        if (winner.get.playerIndex == P1) Array(pointValue.get, 0)
        else Array(0, pointValue.get)
      else Array(0, 0);

    if (Status.flagged.contains(status) && winner.isDefined) {
      if (List(Status.RuleOfGin, Status.GinGammon, Status.GinBackgammon).contains(status)) {
        if (winner.get.playerIndex == P1) {
          if (multiPointState.get.p1Points + points2Add(0) < multiPointState.get.target) points2Add(1) += 64
        } else {
          if (multiPointState.get.p2Points + points2Add(1) < multiPointState.get.target) points2Add(0) += 64
        }
      } else {
        if (winner.get.playerIndex == P1) points2Add(0) += 64
        else points2Add(1) += 64
      }
    }

    return multiPointState match {
      case Some(m) =>
        Some(
          MultiPointState(
            m.target,
            Math.min(m.target, m.p1Points + points2Add(0)),
            Math.min(m.target, m.p2Points + points2Add(1))
          )
        )
      case _ => None
    }
  }

  def pointValue: Option[Int] = {
    if (status == Status.ResignMatch) Some(64)
    else situation.pointValue(winnerPlayerIndex.map(!_))
  }

  def selectSquaresPossible =
    started &&
      playable &&
      turnCount >= 2 &&
      (situation match {
        case Situation.Go(s) => s.canSelectSquares
        case _               => false
      }) &&
      !deadStoneOfferState.map(_.is(DeadStoneOfferState.RejectedOffer)).has(true)

  def neitherPlayerHasMadeAnOffer =
    !player(PlayerIndex.P1).isOfferingSelectSquares &&
      !player(PlayerIndex.P2).isOfferingSelectSquares

  //TODO should be able condense the next two functions into one, and only use the bottom one
  def playerCanOfferSelectSquares(playerIndex: PlayerIndex) =
    if (selectSquaresPossible)
      if (neitherPlayerHasMadeAnOffer) playerIndex == turnPlayerIndex
      else !player(playerIndex).isOfferingSelectSquares
    else false

  def playerToOfferSelectSquares: Option[PlayerIndex] =
    if (playerCanOfferSelectSquares(PlayerIndex.P1)) PlayerIndex.P1.some
    else if (playerCanOfferSelectSquares(PlayerIndex.P2)) PlayerIndex.P2.some
    else none

  def deadStoneOfferState = metadata.deadStoneOfferState

  def drawOffers = metadata.drawOffers

  def selectedSquaresOfferDoesNotMatchUci(uci: Uci): Boolean =
    selectedSquares.fold(false)(p =>
      p.map(_.toString).sorted.mkString != uci.uci.drop(3).split(",").sorted.mkString
    )

  def selectedSquares = metadata.selectedSquares

  def offerSelectSquares(playerIndex: PlayerIndex, squares: List[Pos]) =
    copy(
      updatedAt = DateTime.now,
      metadata = metadata.copy(
        selectedSquares = Some(squares),
        deadStoneOfferState =
          if (playerIndex == P1) Some(DeadStoneOfferState.P1Offering)
          else Some(DeadStoneOfferState.P2Offering)
      )
    )
      .updatePlayer(playerIndex, _.offerSelectSquares)
      .updatePlayer(!playerIndex, _.removeSelectSquaresOffer)

  def acceptSelectSquares(playerIndex: PlayerIndex) =
    copy(
      stratGame = stratGame.copy(clock = clock.map(_.start)),
      updatedAt = DateTime.now,
      metadata = metadata.copy(
        deadStoneOfferState = metadata.deadStoneOfferState match {
          // TODO: this is a mess, but the whole thing is a bit of a mess right now.
          //       What do we do in the case where this method is called when in another state?
          case Some(DeadStoneOfferState.P1Offering) => Some(DeadStoneOfferState.AcceptedP1Offer)
          case Some(DeadStoneOfferState.P2Offering) => Some(DeadStoneOfferState.AcceptedP2Offer)
          case _                                    => sys.error("Logic error, trying to accept a non-existant offer")
        }
      )
    )
      .updatePlayer(playerIndex, _.removeSelectSquaresOffer)
      .updatePlayer(!playerIndex, _.removeSelectSquaresOffer)

  def declineSelectSquares(playerIndex: PlayerIndex) =
    copy(
      stratGame = stratGame.copy(clock = clock.map(_.start)),
      updatedAt = DateTime.now,
      metadata = metadata.copy(
        selectedSquares = None,
        deadStoneOfferState = Some(DeadStoneOfferState.RejectedOffer)
      )
    )
      .updatePlayer(playerIndex, _.removeSelectSquaresOffer)
      .updatePlayer(!playerIndex, _.removeSelectSquaresOffer)

  def hasDeadStoneOfferState = deadStoneOfferState != None

  def resetDeadStoneOfferState =
    copy(
      updatedAt = DateTime.now,
      metadata = metadata.copy(deadStoneOfferState = None)
    )

  def setChooseFirstOffer =
    copy(
      updatedAt = DateTime.now,
      metadata = metadata.copy(
        deadStoneOfferState = DeadStoneOfferState.ChooseFirstOffer.some
      )
    )

  def playerCanOfferDraw(playerIndex: PlayerIndex) =
    started && playable &&
      turnCount >= 2 &&
      !player(playerIndex).isOfferingDraw &&
      !opponent(playerIndex).isAi &&
      !playerHasOfferedDrawRecently(playerIndex)

  def playerHasOfferedDrawRecently(playerIndex: PlayerIndex) =
    drawOffers.lastBy(playerIndex) ?? (_ >= turnCount - 20)

  def offerDraw(playerIndex: PlayerIndex) = copy(
    metadata = metadata.copy(drawOffers = drawOffers.add(playerIndex, turnCount))
  ).updatePlayer(playerIndex, _.offerDraw)

  def playerCouldRematch =
    finishedOrAborted &&
      nonMandatory &&
      !boosted && ! {
        hasAi && variant.fromPositionVariant && clock.exists(_.config.limitSeconds < 60)
      }

  def playerCanProposeTakeback(playerIndex: PlayerIndex) =
    started && playable && !isTournament && !isSimul &&
      bothPlayersHaveMoved &&
      !player(playerIndex).isProposingTakeback &&
      !opponent(playerIndex).isProposingTakeback

  def boosted = rated && finished && bothPlayersHaveMoved && playedTurns < 10

  def moretimeable(playerIndex: PlayerIndex) =
    playable && nonMandatory && {
      clock.??(_ moretimeable playerIndex) || correspondenceClock.??(_ moretimeable playerIndex)
    }

  def abortable =
    status == Status.Started && playedTurns < 2 && nonMandatory && nonMandatory &&
      metadata.multiMatchGameNr.fold(true)(x => x < 2)

  def berserkable =
    clock.??(_.config.berserkable) && status == Status.Started && playedTurns < 2

  def goBerserk(playerIndex: PlayerIndex): Option[Progress] =
    clock.ifTrue(berserkable && !player(playerIndex).berserk).map { c =>
      val newClock = c.goBerserk(playerIndex)
      Progress(
        this,
        copy(
          stratGame = stratGame.copy(clock = Some(newClock)),
          loadClockHistory = _ =>
            clockHistory.map(history => {
              if (history(playerIndex).isEmpty) history
              else history.reset(playerIndex).record(playerIndex, newClock, stratGame.fullTurnCount)
            })
        ).updatePlayer(playerIndex, _.goBerserk)
      ) ++
        List(
          Event.ClockInc(playerIndex, -c.config.berserkPenalty),
          Event.Clock(newClock), // BC
          Event.Berserk(playerIndex)
        )
    }

  def resignable         = playable && !abortable
  def drawable           = playable && !abortable
  def forceResignable    = resignable && nonAi && !fromFriend && hasClock && !isSwiss
  def forceResignableNow = forceResignable && bothPlayersHaveMoved

  def finish(status: Status, winner: Option[PlayerIndex]) = {
    val newClock = clock map { _.stop }
    Progress(
      this,
      copy(
        status = status,
        p1Player = p1Player.finish(winner contains P1),
        p2Player = p2Player.finish(winner contains P2),
        stratGame = stratGame.copy(clock = newClock),
        loadClockHistory = clk =>
          clockHistory map { history =>
            // If not already finished, we're ending due to an event
            // in the middle of a turn, such as resignation or draw
            // acceptance. In these cases, record a final clock time
            // for the active playerIndex. This ensures the end time in
            // clockHistory always matches the final clock time on
            // the board.
            if (!finished) history.record(turnPlayerIndex, clk, stratGame.fullTurnCount)
            else history
          }
      ),
      // Events here for BC.
      List(Event.End(winner)) ::: newClock.??(c => List(Event.Clock(c)))
    )
  }

  def rated  = mode.rated
  def casual = !rated

  def finished = status >= Status.Mate

  def finishedOrAborted = finished || aborted

  private def accountable = playedTurns >= 2 || isTournament

  def updateRatingsOnFinish =
    rated && accountable && !MultiPointState.requireMoreGamesInMultipoint(this)

  def replayable = isPgnImport || finished || (aborted && bothPlayersHaveMoved)

  def analysable = replayable && playedTurns > 4 && Game.analysableVariants(variant)

  def ratingVariant =
    if (isTournament && variant.fromPositionVariant) Variant.libStandard(variant.gameLogic)
    else variant

  def fromPosition = variant.fromPositionVariant || source.??(Source.Position ==)

  def imported = source contains Source.Import

  def fromPool   = source contains Source.Pool
  def fromLobby  = source contains Source.Lobby
  def fromFriend = source contains Source.Friend
  def fromApi    = source contains Source.Api

  def winner = players find (_.wins)

  def loser = winner map opponent

  def winnerPlayerIndex: Option[PlayerIndex] = winner map (_.playerIndex)

  def winnerUserId: Option[String] = winner flatMap (_.userId)

  def loserUserId: Option[String] = loser flatMap (_.userId)

  def wonBy(c: PlayerIndex): Option[Boolean] = winner map (_.playerIndex == c)

  def lostBy(c: PlayerIndex): Option[Boolean] = winner map (_.playerIndex != c)

  def drawn = finished && winner.isEmpty

  def outoftime(withGrace: Boolean): Boolean =
    if (isCorrespondence) outoftimeCorrespondence else outoftimeClock(withGrace)

  private def canBeOutOfTime(c: ClockBase): Boolean =
    if (metadata.multiPointState.map(_.maxPoints).getOrElse(0) > 0)
      bothPlayersHaveMoved && !c.isRunning && !c.isPaused
    else
      !c.isRunning && !c.isPaused

  private def outoftimeClock(withGrace: Boolean): Boolean =
    clock ?? { c =>
      started && playable && (bothPlayersHaveMoved || isSimul || isSwiss || fromFriend || fromApi) && {
        c.outOfTime(turnPlayerIndex, withGrace) || {
          canBeOutOfTime(c) && c.clockPlayerExists(_.elapsed.centis > 0)
        }
      }
    }

  private def outoftimeCorrespondence: Boolean =
    playableCorrespondenceClock ?? { _ outoftime activePlayerIndex }

  def isCorrespondence = speed == Speed.Correspondence

  def isSwitchable = nonAi && (isCorrespondence || isSimul)

  def hasClock = clock.isDefined

  def hasFischerClock = clock.fold(false)(c =>
    c.config match {
      case _: Clock.Config => true
      case _               => false
    }
  )

  def hasBronsteinClock = clock.fold(false)(c =>
    c.config match {
      case _: Clock.BronsteinConfig => true
      case _                        => false
    }
  )

  def hasSimpleDelayClock = clock.fold(false)(c =>
    c.config match {
      case _: Clock.SimpleDelayConfig => true
      case _                          => false
    }
  )

  def hasByoyomiClock = clock.fold(false)(c =>
    c match {
      case _: ByoyomiClock => true
      case _               => false
    }
  )

  def hasCorrespondenceClock = daysPerTurn.isDefined

  def isUnlimited = !hasClock && !hasCorrespondenceClock

  def isClockRunning = clock ?? (_.isRunning)

  def withClock(c: ClockBase) = Progress(this, copy(stratGame = stratGame.copy(clock = Some(c))))

  def correspondenceGiveTime = Progress(this, copy(updatedAt = DateTime.now))

  def estimateClockTotalTime = clock.map(_.estimateTotalSeconds)

  def estimateTotalTime =
    estimateClockTotalTime orElse
      correspondenceClock.map(_.estimateTotalTime) getOrElse 1200

  def timeForFirstTurn: Centis =
    Centis ofSeconds {
      import Speed._
      val base = if (isTournament) speed match {
        case UltraBullet => 11
        case Bullet      => 16
        case Blitz       => 21
        case Rapid       => 25
        case _           => 30
      }
      else
        speed match {
          case UltraBullet => 15
          case Bullet      => 20
          case Blitz       => 25
          case Rapid       => 30
          case _           => 35
        }
      if (variant.key == "chess960" || variant.key == "backgammon" || variant.key == "nackgammon") base * 2
      else if (isTournament && (variant.draughts64Variant) && metadata.simulPairing.isDefined) base + 10
      else base
    }

  def expirableAtStart =
    !bothPlayersHaveMoved && source.exists(Source.expirable.contains) && playable && nonAi && clock.exists(
      !_.isRunning
    )

  def timeBeforeExpirationAtStart: Option[Centis] =
    expirableAtStart option {
      Centis.ofMillis(turnAt.getMillis - nowMillis + timeForFirstTurn.millis).nonNeg
    }

  def timeWhenPaused: Centis =
    Centis ofSeconds {
      import Speed._
      speed match {
        case UltraBullet => 15
        case Bullet      => 20
        case Blitz       => if (isTournament) 30 else 60
        case _           => 60
      }
    }

  def expirableOnPaused = playable && nonAi && clock.exists(_.isPaused)

  def timeBeforeExpirationOnPaused: Option[Centis] =
    expirableOnPaused option {
      Centis.ofMillis(updatedAt.getMillis - nowMillis + timeWhenPaused.millis).nonNeg
    }

  def expirable = expirableAtStart || expirableOnPaused

  def playersWhoDidNotMove: List[Player] = players.filterNot { p => playerHasMoved(p.playerIndex) }

  def playerWhoDidNotMove: Option[Player] =
    if (!onePlayerHasMoved) player(startPlayerIndex).some
    else if (!bothPlayersHaveMoved) player(!startPlayerIndex).some
    else none

  def onePlayerHasMoved    = playedTurns >= 1
  def bothPlayersHaveMoved = playedTurns >= 2

  def startPlayerIndex                     = PlayerIndex.fromTurnCount(stratGame.startedAtTurn)
  def startIndex(playerIndex: PlayerIndex) = if (playerIndex == startPlayerIndex) 0 else 1

  //the number of ply a player has played
  def playerMoves(playerIndex: PlayerIndex): Int =
    actionStrs.zipWithIndex.filter(_._2 % 2 == startIndex(playerIndex)).map(_._1.size).sum

  // if a player has completed their first full turn
  def playerHasMoved(playerIndex: PlayerIndex) =
    //does this actually confirm the full turn is completed?
    if (startIndex(playerIndex) == 0) onePlayerHasMoved else bothPlayersHaveMoved

  def playerBlurPercent(playerIndex: PlayerIndex): Int =
    if (playedTurns > 5)
      (player(playerIndex).blurs.nb * 100) / playerMoves(playerIndex)
    else 0

  def isBeingPlayed = !isPgnImport && !finishedOrAborted

  def olderThan(seconds: Int) = updatedAt isBefore DateTime.now.minusSeconds(seconds)

  def justCreated = createdAt isAfter DateTime.now.minusSeconds(1)

  def unplayed = !bothPlayersHaveMoved && (createdAt isBefore Game.unplayedDate)

  def abandoned =
    (status <= Status.Started) && {
      updatedAt isBefore {
        if (hasAi && !hasCorrespondenceClock) Game.aiAbandonedDate
        else Game.abandonedDate
      }
    }

  def forecastable = started && playable && isCorrespondence && !hasAi

  def hasBookmarks = bookmarks > 0

  def showBookmarks = hasBookmarks ?? bookmarks.toString

  def userIds = playerMaps(_.userId)

  def twoUserIds: Option[(User.ID, User.ID)] =
    for {
      w <- p1Player.userId
      b <- p2Player.userId
      if w != b
    } yield w -> b

  def userRatings = playerMaps(_.rating)

  def averageUsersRating =
    userRatings match {
      case a :: b :: Nil => Some((a + b) / 2)
      case a :: Nil      => Some((a + 1500) / 2)
      case _             => None
    }

  def withHandicappedTournament(isHandicapped: Boolean) =
    copy(metadata = metadata.copy(fromHandicappedTournament = isHandicapped))

  //TODO Refactor MultiPointState!
  def withMultiPointState(multiPointState: Option[MultiPointState]) =
    copy(
      stratGame = stratGame match {
        case StratGame.Backgammon(g) =>
          StratGame.wrap(
            g.copy(
              situation = g.situation.copy(
                board = g.situation.board.copy(
                  history = g.situation.board.history.copy(
                    multiPointState =
                      multiPointState.map(mps => StratMultiPointState(mps.target, mps.p1Points, mps.p2Points))
                  )
                )
              )
            )
          )
        case _ => stratGame
      },
      metadata = metadata.copy(multiPointState = multiPointState)
    )

  def withTournamentId(id: String) = copy(metadata = metadata.copy(tournamentId = id.some))
  def withSwissId(id: String)      = copy(metadata = metadata.copy(swissId = id.some))

  def withSimulId(id: String) = copy(metadata = metadata.copy(simulId = id.some))

  def withId(newId: String) = copy(id = newId)

  def source = metadata.source

  def pgnImport   = metadata.pgnImport
  def isPgnImport = pgnImport.isDefined

  def resetTurns =
    copy(
      stratGame = stratGame.copy(
        turnCount = 0,
        plies = 0,
        startedAtPly = 0,
        startedAtTurn = 0
      )
    )

  lazy val opening: Option[FullOpening.AtPly] =
    if (fromPosition || !Variant.openingSensibleVariants(variant.gameLogic)(variant)) none
    else FullOpeningDB.search(variant.gameLogic, actionStrs)

  def synthetic = id == Game.syntheticId

  private def playerMaps[A](f: Player => Option[A]): List[A] = players flatMap f

  def pov(c: PlayerIndex)                           = Pov(this, c)
  def playerIdPov(playerId: Player.ID): Option[Pov] = player(playerId) map { Pov(this, _) }
  def p1Pov                                         = pov(P1)
  def p2Pov                                         = pov(P2)
  def playerPov(p: Player)                          = pov(p.playerIndex)
  def loserPov                                      = loser map playerPov

  //When updating, also edit modules/challenge, modules/puzzle and ui/@types/playstrategy/index.d.ts:declare type PlayerName
  def playerTrans(p: PlayerIndex)(implicit lang: Lang) =
    stratGame.board.variant.playerNames(p) match {
      case "White" => trans.white.txt()
      case "Black" => trans.black.txt()
      //Xiangqi add back in when adding red as a colour for Xiangqi
      //case "Red"   => trans.red.txt()
      case "Sente"   => trans.sente.txt()
      case "Gote"    => trans.gote.txt()
      case s: String => s
    }

  def setAnalysed = copy(metadata = metadata.copy(analysed = true))

  def secondsSinceCreation = (nowSeconds - createdAt.getSeconds).toInt

  override def toString = s"""Game($id)"""
}

object Game {

  type ID = String

  case class Id(value: String) extends AnyVal with StringValue {
    def full(playerId: PlayerId) = FullId(s"$value{$playerId.value}")
  }
  case class FullId(value: String) extends AnyVal with StringValue {
    def gameId   = Id(value take gameIdSize)
    def playerId = PlayerId(value drop gameIdSize)
  }
  case class PlayerId(value: String) extends AnyVal with StringValue

  case class WithInitialFen(game: Game, fen: Option[FEN])

  val syntheticId = "synthetic"

  val maxPlayingRealtime = 100

  val maxPlaying = 200 //including correspondence

  val maxPlies =
    1000 // also in SG gl/format/pgn/Binary.scala + study/node(unlimited can cause StackOverflowError)
  val maxTurns = maxPlies // used for correct terminology where appropriate

  val analysableVariants: Set[Variant] = Variant.all.filter(_.hasFishnet).toSet

  //not used anywhere
  //val unanalysableVariants: Set[Variant] =
  //  Variant.all.toSet -- analysableVariants

  val variantsWhereP1IsBetter: Set[Variant] =
    Variant.all.filter(_.p1IsBetterVariant).toSet

  val blindModeVariants: Set[Variant] =
    Variant.all.filter(_.blindModeVariant).toSet

  //lichess old format
  //val hordeP1PawnsSince = new DateTime(2015, 4, 11, 10, 0)
  //def isOldHorde(game: Game) =
  //  game.variant == strategygames.chess.variant.Horde &&
  //    game.createdAt.isBefore(Game.hordeP1PawnsSince)

  def allowRated(variant: Variant, clock: Option[ClockConfig]) =
    variant.key == "standard" || {
      clock ?? { c =>
        c.estimateTotalTime >= Centis(3000) &&
        c.limitSeconds > 0 || c.graceSeconds > 1
      }
    }

  def combinePlyTimes(
      a: List[Centis],
      b: List[Centis],
      who: Vector[String],
      output: Vector[Centis]
  ): Vector[Centis] = {
    if (who.size == 0 || (who(0) == "a" & a.size == 0) || (who(0) == "b" & b.size == 0)) output
    else if (who(0) == "a") combinePlyTimes(a.drop(1), b, who.drop(1), output ++ a.take(1))
    else if (who(0) == "b") combinePlyTimes(a, b.drop(1), who.drop(1), output ++ b.take(1))
    else output
  }

  val gameIdSize   = 8
  val playerIdSize = 4
  val fullIdSize   = 12
  val tokenSize    = 4

  val unplayedHours = 24
  def unplayedDate  = DateTime.now minusHours unplayedHours

  val abandonedDays = 21
  def abandonedDate = DateTime.now minusDays abandonedDays

  val aiAbandonedHours = 6
  def aiAbandonedDate  = DateTime.now minusHours aiAbandonedHours

  def takeGameId(fullId: String)   = fullId take gameIdSize
  def takePlayerId(fullId: String) = fullId drop gameIdSize

  val idRegex         = """[\w-]{8}""".r
  def validId(id: ID) = idRegex matches id

  def isBoardCompatible(game: Game): Boolean =
    game.clock.fold(true) { c =>
      isBoardCompatible(c.config)
    }

  def isBoardCompatible(clock: ClockConfig): Boolean =
    Speed(clock) >= Speed.Rapid

  def isBotCompatible(game: Game): Boolean = {
    game.hasAi || game.fromFriend || game.fromApi || game.hasPSBot
  } && isBotCompatible(game.speed)

  def isBotCompatible(speed: Speed): Boolean = speed >= Speed.Bullet

  def isBoardOrBotCompatible(game: Game) = isBoardCompatible(game) || isBotCompatible(game)

  private[game] val emptyCheckCount = CheckCount(0, 0)
  private[game] val emptyScore      = Score(0, 0)

  private[game] val someEmptyFischerClockHistory = Some(FischerClockHistory())
  private[game] val someEmptyDelayClockHistory   = Some(DelayClockHistory())
  private[game] def someEmptyByoyomiClockHistory(c: ClockBase) = c match {
    case bc: ByoyomiClock => Some(ByoyomiClockHistory(bc.config.byoyomi))
    case _                => Some(ByoyomiClockHistory(Centis(0)))
  }
  private[game] def someEmptyClockHistory(c: ClockBase) = c.config match {
    case _: Clock.Config            => someEmptyFischerClockHistory
    case _: Clock.BronsteinConfig   => someEmptyDelayClockHistory
    case _: Clock.SimpleDelayConfig => someEmptyDelayClockHistory
    case _: ByoyomiClock.Config     => someEmptyByoyomiClockHistory(c)
  }

  def make(
      stratGame: StratGame,
      p1Player: Player,
      p2Player: Player,
      mode: Mode,
      source: Source,
      pgnImport: Option[PgnImport],
      daysPerTurn: Option[Int] = None,
      drawLimit: Option[Int] = None,
      multiMatch: Option[String] = None,
      backgammonPoints: Option[Int] = None
  ): NewGame = {
    val createdAt = DateTime.now
    NewGame(
      Game(
        id = IdGenerator.uncheckedGame,
        p1Player = p1Player,
        p2Player = p2Player,
        stratGame = stratGame,
        status = Status.Created,
        daysPerTurn = daysPerTurn,
        mode = mode,
        metadata = metadata(source)
          .copy(
            pgnImport = pgnImport,
            drawLimit = drawLimit,
            multiMatch = multiMatch
          ),
        createdAt = createdAt,
        updatedAt = createdAt,
        turnAt = createdAt
      ).withMultiPointState(MultiPointState(backgammonPoints))
    )
  }

  def metadata(source: Source) =
    Metadata(
      source = source.some,
      pgnImport = none,
      tournamentId = none,
      swissId = none,
      simulId = none,
      analysed = false,
      drawOffers = GameDrawOffers.empty
    )

  object BSONFields {

    val id                        = "_id"
    val p1Player                  = "p0"
    val p2Player                  = "p1"
    val playerIds                 = "is"
    val playerUids                = "us"
    val playingUids               = "pl"
    val binaryPieces              = "ps"
    val oldPgn                    = "pg"
    val huffmanPgn                = "hp"
    val status                    = "s"
    val turns                     = "t"
    val plies                     = "p"
    val activePlayer              = "ap"
    val startedAtPly              = "sp"
    val startedAtTurn             = "st"
    val clock                     = "c"
    val clockType                 = "ct"
    val positionHashes            = "ph"
    val checkCount                = "cc"
    val score                     = "sc"
    val captures                  = "cp"
    val castleLastMove            = "cl"
    val kingMoves                 = "km"
    val historyLastTurn           = "hlm" // was called historyLastMove hence hlm
    val historyCurrentTurn        = "hct"
    val unmovedRooks              = "ur"
    val daysPerTurn               = "cd"
    val plyTimes                  = "mt"  // was called moveTimes hence mt
    val p1ClockHistory            = "cw"
    val p2ClockHistory            = "cb"
    val periodsP1                 = "pp0"
    val periodsP2                 = "pp1"
    val rated                     = "ra"
    val analysed                  = "an"
    val lib                       = "l"
    val variant                   = "v"
    val pocketData                = "chd"
    val bookmarks                 = "bm"
    val createdAt                 = "ca"
    val updatedAt                 = "ua"
    val turnAt                    = "ta"
    val source                    = "so"
    val pgnImport                 = "pgni"
    val tournamentId              = "tid"
    val swissId                   = "iid"
    val simulId                   = "sid"
    val fromHandicappedTournament = "hd"
    val tvAt                      = "tv"
    val winnerPlayerIndex         = "w"
    val winnerId                  = "wid"
    val initialFen                = "if"
    val checkAt                   = "ck"
    val perfType                  = "pt"  // only set on student games for aggregation
    val drawOffers                = "do"
    //backgammon
    val unusedDice      = "ud"
    val cubeData        = "bcd"
    val multiPointState = "mps"
    // go
    val selectedSquares     = "ss" // the dead stones selected in go
    val deadStoneOfferState = "os" //state of the dead stone offer
    //draughts
    val simulPairing = "sip"
    val timeOutUntil = "to"
    val multiMatch   = "mm"
    val drawLimit    = "dl"
  }
}

sealed abstract class DeadStoneOfferState(val id: Int) {
  def name                                                            = toString
  def is(s: DeadStoneOfferState): Boolean                             = this == s
  def is(f: DeadStoneOfferState.type => DeadStoneOfferState): Boolean = is(f(DeadStoneOfferState))
}

object DeadStoneOfferState {
  case object ChooseFirstOffer extends DeadStoneOfferState(0)
  case object P1Offering       extends DeadStoneOfferState(1)
  case object P2Offering       extends DeadStoneOfferState(2)
  case object RejectedOffer    extends DeadStoneOfferState(3)
  case object AcceptedP1Offer  extends DeadStoneOfferState(4)
  case object AcceptedP2Offer  extends DeadStoneOfferState(5)

  val all = List(ChooseFirstOffer, P1Offering, P2Offering, RejectedOffer, AcceptedP1Offer, AcceptedP2Offer)

  val byId = all map { v => (v.id, v) } toMap

  def apply(id: Int): Option[DeadStoneOfferState] = byId get id
}

case class MultiPointState(target: Int, p1Points: Int = 0, p2Points: Int = 0) {

  def maxPoints = Math.max(p1Points, p2Points)

  def isCrawfordState = maxPoints + 1 == target && p1Points != p2Points

  private def updatePoints(origPoints: Int, wonPoints: Int) =
    (origPoints + wonPoints).atMost(target)

  def updateMultiPointState(pointValue: Option[Int], winner: Option[PlayerIndex]): Option[MultiPointState] =
    winner.map(p =>
      p.fold(
        copy(p1Points = updatePoints(this.p1Points, pointValue.getOrElse(0))),
        copy(p2Points = updatePoints(this.p2Points, pointValue.getOrElse(0)))
      )
    )

  override def toString: String     = f"${target}%02d${p1Points}%02d${p2Points}%02d"
  def toString(p1: Boolean): String = f"${(if (p1) p1Points else p2Points)}%02d"
}

object MultiPointState {
  var noDataChar = "-"

  def apply(points: Option[Int]): Option[MultiPointState] = points.filter(_ != 1).map(p => MultiPointState(p))

  def nextGameIsCrawford(game: Game): Boolean =
    game.metadata.multiPointState.fold(false)(mps =>
      !mps.isCrawfordState && mps
        .updateMultiPointState(
          game.pointValue,
          game.winnerPlayerIndex
        )
        .map(_.isCrawfordState)
        .getOrElse(false)
    )

  def requireMoreGamesInMultipoint(game: Game): Boolean =
    !((Status.flagged ++ List(Status.ResignMatch)).contains(game.status)) &&
      game.metadata.multiPointState.fold(false)(mps =>
        game.winnerPlayerIndex
          .map { p =>
            p.fold(mps.p1Points, mps.p2Points) + game.pointValue.getOrElse(0) < mps.target
          }
          .getOrElse(false)
      )

}

case class CastleLastMove(castles: Castles, lastMove: Option[ChessUci])

object CastleLastMove {

  def init = CastleLastMove(Castles.all, None)

  import reactivemongo.api.bson._
  import lila.db.ByteArray.ByteArrayBSONHandler

  implicit private[game] val castleLastMoveBSONHandler: BSONHandler[CastleLastMove] =
    new BSONHandler[CastleLastMove] {
      def readTry(bson: BSONValue) =
        bson match {
          case bin: BSONBinary => ByteArrayBSONHandler readTry bin map BinaryFormat.castleLastMove.read
          case b               => lila.db.BSON.handlerBadType(b)
        }
      def writeTry(clmt: CastleLastMove) =
        ByteArrayBSONHandler writeTry {
          BinaryFormat.castleLastMove write clmt
        }
    }
}

// At what turns we entered a new period
case class PeriodEntries(
    p1: Vector[Int],
    p2: Vector[Int]
) {

  def apply(player: PlayerIndex) =
    player.fold(p1, p2)

  def update(player: PlayerIndex, f: Vector[Int] => Vector[Int]) =
    player.fold(copy(p1 = f(p1)), copy(p2 = f(p2)))

}

object PeriodEntries {
  val default    = PeriodEntries(Vector(), Vector())
  val maxPeriods = 5
}

sealed trait ClockHistory {
  val p1: Vector[Centis]
  val p2: Vector[Centis]
  def update(playerIndex: PlayerIndex, f: Vector[Centis] => Vector[Centis]): ClockHistory
  def record(playerIndex: PlayerIndex, clock: ClockBase, fullTurnCount: Int): ClockHistory
  def reset(playerIndex: PlayerIndex): ClockHistory
  def apply(playerIndex: PlayerIndex): Vector[Centis]
  def dbTimes(playerIndex: PlayerIndex): Vector[Centis]
  def plyTimes(
      playerIndex: PlayerIndex,
      turnPlayerIndex: PlayerIndex,
      playedPlies: Int,
      startedAtTurn: Int,
      finished: Boolean,
      status: Status,
      grace: Centis = Centis(0),
      byo: Centis = Centis(0)
  ): List[Centis]
  def lastX(playerIndex: PlayerIndex, plies: Int): Option[Centis]
  def size: Int
  def bothClockStates(firstMoveBy: PlayerIndex, who: Vector[String]): Vector[Centis]
}

case class FischerClockHistory(
    p1: Vector[Centis] = Vector.empty,
    p2: Vector[Centis] = Vector.empty
) extends ClockHistory {

  def update(playerIndex: PlayerIndex, f: Vector[Centis] => Vector[Centis]): ClockHistory =
    playerIndex.fold(copy(p1 = f(p1)), copy(p2 = f(p2)))

  override def record(playerIndex: PlayerIndex, clock: ClockBase, fullTurnCount: Int): ClockHistory =
    update(playerIndex, _ :+ clock.remainingTime(playerIndex))
  def reset(playerIndex: PlayerIndex)                   = update(playerIndex, _ => Vector.empty)
  def apply(playerIndex: PlayerIndex): Vector[Centis]   = playerIndex.fold(p1, p2)
  def dbTimes(playerIndex: PlayerIndex): Vector[Centis] = apply(playerIndex)
  override def lastX(playerIndex: PlayerIndex, plies: Int): Option[Centis] =
    if (apply(playerIndex).size < plies) None
    else apply(playerIndex).takeRight(plies).headOption
  def size = p1.size + p2.size

  // TODO: the parameters of this function may need a rethinking later on
  override def plyTimes(
      playerIndex: PlayerIndex,
      turnPlayerIndex: PlayerIndex,
      playedPlies: Int,
      startedAtTurn: Int,
      finished: Boolean,
      status: Status,
      grace: Centis = Centis(0),
      byo: Centis = Centis(0)
  ): List[Centis] = {
    val clocks = dbTimes(playerIndex)
    Centis(0) :: {
      val pairs = clocks.iterator zip clocks.iterator.drop(1)

      // We need to determine if this playerIndex's last clock had grace applied.
      // if finished and history.size == playedPlies then game was ended
      // by a players ply, such as with mate or autodraw. In this case,
      // the last ply of the game, and the only one without grace, is the
      // last entry of the clock history for !turnPlayerIndex.
      //
      // On the other hand, if history.size is more than playedPlies,
      // then the game ended during a players turn by async event, and
      // the last recorded time is in the history for turnPlayerIndex.
      val noLastInc =
        finished && (size <= playedPlies) == (playerIndex != turnPlayerIndex)

      (pairs.map { case (first, second) =>
        ({
          val mt     = first - second
          val cGrace = (pairs.hasNext || !noLastInc) ?? grace

          (mt + cGrace)
        } nonNeg)
      } toList)
    }
  }

  // first state is of the playerIndex that moved first.
  override def bothClockStates(firstMoveBy: PlayerIndex, who: Vector[String]): Vector[Centis] = {
    val a = firstMoveBy.fold(p1, p2)
    val b = firstMoveBy.fold(p2, p1)
    Game.combinePlyTimes(a.toList, b.toList, who, Vector.empty)
  }
}

case class DelayClockHistory(
    p1ActionTimes: Vector[Centis] = Vector.empty,
    p2ActionTimes: Vector[Centis] = Vector.empty,
    p1RemainingTime: Option[Centis] = None,
    p2RemainingTime: Option[Centis] = None
) extends ClockHistory {
  // In this case, our case class stores the time moves took as the primary
  // attribue but, we need to produce the time remaining after each move.
  // We do this by working backwards from the prevsRemainingTime and adding in the move times
  // and then reversing it.
  //TODO this doesn't work as remaingtime is always None, would need clock details to work out remaining time?
  // Issues seen in analysis clock times (not correct for delay)
  private def timeRemaining(moveTimes: Vector[Centis], remainingTime: Option[Centis]): Vector[Centis] =
    moveTimes.reverse.scanLeft(remainingTime.getOrElse(Centis(0)))(_ + _).reverse
  lazy val p1: Vector[Centis] = timeRemaining(p1ActionTimes, p1RemainingTime)
  lazy val p2: Vector[Centis] = timeRemaining(p2ActionTimes, p2RemainingTime)

  def update(playerIndex: PlayerIndex, f: Vector[Centis] => Vector[Centis]): ClockHistory =
    playerIndex.fold(copy(p1ActionTimes = f(p1ActionTimes)), copy(p2ActionTimes = f(p2ActionTimes)))

  def updateRemainingTime(playerIndex: PlayerIndex, f: Option[Centis] => Option[Centis]): DelayClockHistory =
    playerIndex.fold(copy(p1RemainingTime = f(p1RemainingTime)), copy(p2RemainingTime = f(p2RemainingTime)))

  override def record(playerIndex: PlayerIndex, clock: ClockBase, fullTurnCount: Int): ClockHistory = {
    val remainingTime = clock.remainingTime(playerIndex)
    updateRemainingTime(playerIndex, _ => Some(remainingTime)).update(
      playerIndex,
      prev => prev :+ clock.lastMoveTime(playerIndex)
    )
  }
  def reset(playerIndex: PlayerIndex)                 = update(playerIndex, _ => Vector.empty)
  def apply(playerIndex: PlayerIndex): Vector[Centis] = playerIndex.fold(p1, p2)
  def dbTimes(playerIndex: PlayerIndex): Vector[Centis] =
    playerIndex.fold(p1ActionTimes, p2ActionTimes)

  // TODO: the parameters of this function may need a rethinking later on
  // NOTE: This implementation doesn't need any of the extra params, illustrating the benefit
  //       of storing the ply times directly. :heart:
  override def plyTimes(
      playerIndex: PlayerIndex,
      @nowarn turnPlayerIndex: PlayerIndex,
      @nowarn playedPlies: Int,
      @nowarn startedAtTurn: Int,
      @nowarn finished: Boolean,
      @nowarn status: Status,
      @nowarn grace: Centis = Centis(0),
      @nowarn byo: Centis = Centis(0)
  ): List[Centis] = dbTimes(playerIndex).toList

  override def lastX(playerIndex: PlayerIndex, plies: Int): Option[Centis] =
    if (apply(playerIndex).size < plies) None
    else apply(playerIndex).takeRight(plies).headOption
  def size = p1.size + p2.size

  // first state is of the playerIndex that moved first.
  override def bothClockStates(firstMoveBy: PlayerIndex, who: Vector[String]): Vector[Centis] = {
    val a = firstMoveBy.fold(p1, p2)
    val b = firstMoveBy.fold(p2, p1)
    Game.combinePlyTimes(a.toList, b.toList, who, Vector.empty)
  }
}

case class ByoyomiClockHistory(
    byoyomi: Centis,
    p1: Vector[Centis] = Vector.empty,
    p2: Vector[Centis] = Vector.empty,
    periodEntries: PeriodEntries = PeriodEntries.default
) extends ClockHistory {

  def apply(playerIndex: PlayerIndex): Vector[Centis]   = playerIndex.fold(p1, p2)
  def dbTimes(playerIndex: PlayerIndex): Vector[Centis] = playerIndex.fold(p1, p2)

  // TODO: the parameters of this function may need a rethinking later on
  override def plyTimes(
      playerIndex: PlayerIndex,
      turnPlayerIndex: PlayerIndex,
      playedPlies: Int,
      startedAtTurn: Int,
      finished: Boolean,
      status: Status,
      grace: Centis = Centis(0),
      byo: Centis = Centis(0)
  ): List[Centis] = {
    val clocks = dbTimes(playerIndex)
    Centis(0) :: {
      val pairs = clocks.iterator zip clocks.iterator.drop(1)

      // We need to determine if this playerIndex's last clock had grace applied.
      // if finished and history.size == playedPlies then game was ended
      // by a players ply, such as with mate or autodraw. In this case,
      // the last ply of the game, and the only one without grace, is the
      // last entry of the clock history for !turnPlayerIndex.
      //
      // On the other hand, if history.size is more than playedPlies,
      // then the game ended during a players turn by async event, and
      // the last recorded time is in the history for turnPlayerIndex.
      val noLastInc =
        finished && (size <= playedPlies) == (playerIndex != turnPlayerIndex)

      // Also if we timed out over a period or periods, we need to
      // multiply byoyomi by number of periods entered that turn and add
      // previous remaining time, which could either be byoyomi or
      // remaining time
      val byoyomiStart = firstEnteredPeriod(playerIndex)
      val byoyomiTimeout =
        byoyomiStart.isDefined && (Status.flagged.contains(status)) && (playerIndex == turnPlayerIndex)

      (pairs.zipWithIndex.map { case ((first, second), index) =>
        ({
          //TODO multiaction need to calculate fullTurncount (expand on pairs to get an actual full turn of times)
          val fullTurnCount = index + 2 + startedAtTurn / 2
          val afterByoyomi  = byoyomiStart ?? (_ <= fullTurnCount)
          // after byoyomi we store movetimes directly, not remaining time
          val mt     = if (afterByoyomi) second else first - second
          val cGrace = (!afterByoyomi && (pairs.hasNext || !noLastInc)) ?? grace

          (if (!pairs.hasNext && byoyomiTimeout) {
             val prevTurnByoyomi = byoyomiStart ?? (_ < fullTurnCount)
             (if (prevTurnByoyomi) byo else first) + byo * countSpentPeriods(playerIndex, fullTurnCount)
           } else mt + cGrace)
        } nonNeg)
      } toList)
    }
  }

  def update(playerIndex: PlayerIndex, f: Vector[Centis] => Vector[Centis]): ClockHistory =
    updateInternal(playerIndex, f)

  def updateInternal(playerIndex: PlayerIndex, f: Vector[Centis] => Vector[Centis]): ByoyomiClockHistory =
    playerIndex.fold(copy(p1 = f(p1)), copy(p2 = f(p2)))

  def updatePeriods(playerIndex: PlayerIndex, f: Vector[Int] => Vector[Int]): ClockHistory =
    copy(periodEntries = periodEntries.update(playerIndex, f))

  override def record(playerIndex: PlayerIndex, clock: ClockBase, fullTurnCount: Int): ClockHistory = {
    val curClock        = clock currentClockFor playerIndex
    val initiatePeriods = clock.config.startsAtZero && periodEntries(playerIndex).isEmpty
    val isUsingByoyomi  = curClock.periods > 0 && !initiatePeriods

    val timeToStore = if (isUsingByoyomi) clock.lastMoveTimeOf(playerIndex) else curClock.time

    updateInternal(playerIndex, _ :+ timeToStore)
      .updatePeriods(
        playerIndex,
        _.padTo(initiatePeriods ?? 1, 0)
          .padTo(curClock.periods atMost PeriodEntries.maxPeriods, fullTurnCount)
      )
  }

  override def reset(playerIndex: PlayerIndex) =
    updateInternal(playerIndex, _ => Vector.empty).updatePeriods(playerIndex, _ => Vector.empty)

  def lastX(playerIndex: PlayerIndex, plies: Int): Option[Centis] =
    if (apply(playerIndex).size < plies) None
    else apply(playerIndex).takeRight(plies).headOption

  def size = p1.size + p2.size

  def firstEnteredPeriod(playerIndex: PlayerIndex): Option[Int] =
    periodEntries(playerIndex).headOption

  def countSpentPeriods(playerIndex: PlayerIndex, fullTurnCount: Int) =
    periodEntries(playerIndex).count(_ == fullTurnCount)

  def refundSpentPeriods(playerIndex: PlayerIndex, turn: Int) =
    updatePeriods(playerIndex, _.filterNot(_ == turn))

  private def padWithByo(playerIndex: PlayerIndex, byo: Centis): Vector[Centis] = {
    val times = apply(playerIndex)
    times.take(firstEnteredPeriod(playerIndex).fold(times.size)(_ - 1)).padTo(times.size, byo)
  }

  // first state is of the playerIndex that moved first.
  override def bothClockStates(firstMoveBy: PlayerIndex, who: Vector[String]): Vector[Centis] = {
    val p1Times = padWithByo(PlayerIndex.P1, byoyomi)
    val p2Times = padWithByo(PlayerIndex.P2, byoyomi)
    val a       = firstMoveBy.fold(p1Times, p2Times)
    val b       = firstMoveBy.fold(p2Times, p1Times)
    Game.combinePlyTimes(a.toList, b.toList, who, Vector.empty)
  }
}

case class MonthlyGameData(
    yearMonth: String,
    libVar: String,
    count: Long
)

case class WinRate(
    libVar: String,
    p1: Int,
    p2: Int,
    draws: Int,
    total: Int
)

case class WinRatePercentages(
    libVar: String,
    p1: Int,
    p2: Int,
    draw: Int
)
