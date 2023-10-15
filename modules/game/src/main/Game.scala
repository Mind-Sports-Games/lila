package lila.game

import strategygames.format.{ FEN, Uci }
import strategygames.opening.{ FullOpening, FullOpeningDB }
import strategygames.chess.{ Castles, CheckCount }
import strategygames.chess.format.{ Uci => ChessUci }
import strategygames.{
  P2,
  Centis,
  ByoyomiClock,
  ClockBase,
  ClockConfig,
  Clock,
  Player => PlayerIndex,
  Game => StratGame,
  GameLogic,
  Mode,
  Move,
  Drop,
  Pass,
  SelectSquares,
  Action,
  Pos,
  Speed,
  Status,
  P1,
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
    chess: StratGame,
    loadClockHistory: ClockBase => Option[ClockHistory] = Game.someEmptyClockHistory,
    status: Status,
    daysPerTurn: Option[Int],
    binaryMoveTimes: Option[ByteArray] = None,
    mode: Mode = Mode.default,
    bookmarks: Int = 0,
    createdAt: DateTime = DateTime.now,
    movedAt: DateTime = DateTime.now,
    metadata: Metadata,
    pdnStorage: Option[PdnStorage] = None
) {

  lazy val clockHistory = chess.clock.flatMap(loadClockHistory)

  def situation = chess.situation
  def board     = chess.situation.board
  def history   = chess.situation.board.history
  def variant   = chess.situation.board.variant
  def turns     = chess.turns
  def clock     = chess.clock
  // TODO: we really should rename pgnMoves to something more general,
  //       because only our chess games ae using PGN.
  def pgnMoves = chess.pgnMoves

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

  lazy val naturalOrientation = variant match {
    case Variant.Chess(v) if v.racingKings => P1
    case _                                 => PlayerIndex.fromP1(p1Player before p2Player)
  }

  def turnPlayerIndex = chess.player

  //For the front end - whose 'turn' is it? (SG + Go select squares status)
  def activePlayerIndex = playerToOfferSelectSquares.getOrElse(turnPlayerIndex)

  def turnOf(p: Player): Boolean      = p == player
  def turnOf(c: PlayerIndex): Boolean = c == turnPlayerIndex
  def turnOf(u: User): Boolean        = player(u) ?? turnOf

  def playedTurns = turns - chess.startedAtTurn

  def flagged = (status == Status.Outoftime).option(turnPlayerIndex)

  def fullIdOf(player: Player): Option[String] =
    (players contains player) option s"$id${player.id}"

  def fullIdOf(playerIndex: PlayerIndex): String = s"$id${player(playerIndex).id}"

  def tournamentId = metadata.tournamentId
  def simulId      = metadata.simulId
  def swissId      = metadata.swissId

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

  // we can't rely on the clock,
  // because if moretime was given,
  // elapsed time is no longer representing the game duration
  def durationSeconds: Option[Int] =
    movedAt.getSeconds - createdAt.getSeconds match {
      case seconds if seconds > 60 * 60 * 12 => none // no way it lasted more than 12 hours, come on.
      case seconds                           => seconds.toInt.some
    }

  private def everyOther[A](l: List[A]): List[A] =
    l match {
      case a :: _ :: tail => a :: everyOther(tail)
      case _              => l
    }

  def moveTimes(playerIndex: PlayerIndex): Option[List[Centis]] = {
    for {
      clk <- clock
      inc = Centis(0) // TODO: FIXME clk.incrementOf(playerIndex)
      byo = clk match {
        case bc: ByoyomiClock => bc.byoyomiOf(playerIndex)
        case _                => Centis(0)
      }
      history <- clockHistory
      clocks = history(playerIndex)
    } yield Centis(0) :: {
      val pairs = clocks.iterator zip clocks.iterator.drop(1)

      // We need to determine if this playerIndex's last clock had inc applied.
      // if finished and history.size == playedTurns then game was ended
      // by a players move, such as with mate or autodraw. In this case,
      // the last move of the game, and the only one without inc, is the
      // last entry of the clock history for !turnPlayerIndex.
      //
      // On the other hand, if history.size is more than playedTurns,
      // then the game ended during a players turn by async event, and
      // the last recorded time is in the history for turnPlayerIndex.
      // todo check this works for amazons?
      val noLastInc = finished && (history.size <= playedTurns) == (playerIndex != turnPlayerIndex)

      // Also if we timed out over a period or periods, we need to
      // multiply byoyomi by number of periods entered that turn and add
      // previous remaining time, which could either be byoyomi or
      // remaining time
      val byoyomiStart = history match {
        case bch: ByoyomiClockHistory => bch.firstEnteredPeriod(playerIndex)
        case _                        => None
      }
      val byoyomiTimeout =
        byoyomiStart.isDefined && (status == Status.Outoftime) && (playerIndex == turnPlayerIndex)

      val byoyomiHistory: Option[ByoyomiClockHistory] = history match {
        case bch: ByoyomiClockHistory => Some(bch)
        case _                        => None
      }

      pairs.zipWithIndex.map { case ((first, second), index) =>
        {
          val turn         = index + 2 + chess.startedAtTurn / 2
          val afterByoyomi = byoyomiStart ?? (_ <= turn)
          // after byoyomi we store movetimes directly, not remaining time
          val mt   = if (afterByoyomi) second else first - second
          val cInc = (!afterByoyomi && (pairs.hasNext || !noLastInc)) ?? inc

          if (!pairs.hasNext && byoyomiTimeout) {
            val prevTurnByoyomi = byoyomiStart ?? (_ < turn)
            (if (prevTurnByoyomi) byo else first) + byo * byoyomiHistory.fold(0)(
              _.countSpentPeriods(playerIndex, turn)
            )
          } else mt + cInc
        } nonNeg
      } toList
    }
  } orElse binaryMoveTimes.map { binary =>
    // TODO: make movetime.read return List after writes are disabled.
    val base = BinaryFormat.moveTime.read(binary, playedTurns)
    val mts  = if (playerIndex == startPlayerIndex) base else base.drop(1)
    everyOther(mts.toList)
  }

  def moveTimes: Option[Vector[Centis]] =
    for {
      a <- moveTimes(startPlayerIndex)
      b <- moveTimes(!startPlayerIndex)
    } yield Sequence.interleave(a, b)

  def bothClockStates: Option[Vector[Centis]] =
    clockHistory.map(_.bothClockStates(startPlayerIndex))

  def pdnMovesConcat(fullCaptures: Boolean = false, dropGhosts: Boolean = false): PgnMoves =
    chess match {
      case StratGame.Draughts(game) => game.pdnMovesConcat(fullCaptures, dropGhosts)
      case _                        => sys.error("Cant call pdnMovesConcat for a gamelogic other than draughts")
    }

  def pgnMoves(playerIndex: PlayerIndex): PgnMoves = {
    val pivot = if (playerIndex == startPlayerIndex) 0 else 1
    val pgnMoves = variant.gameLogic match {
      case GameLogic.Draughts() => pdnMovesConcat()
      case _                    => chess.pgnMoves
    }
    pgnMoves.zipWithIndex.collect {
      case (e, i) if (i % 2) == pivot => e
    }
  }

  // apply a move (now called action)
  def update(
      game: StratGame, // new chess position
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
    } yield ch.record(turnPlayerIndex, clk, chess.fullMoveNumber)

    def deadStoneOfferStateAfterAction: Option[DeadStoneOfferState] = {
      game.situation match {
        case Situation.Go(s) if s.canSelectSquares => DeadStoneOfferState.ChooseFirstOffer.some
        case _                                     => None
      }
    }

    val updated = copy(
      p1Player = copyPlayer(p1Player),
      p2Player = copyPlayer(p2Player),
      chess = game,
      binaryMoveTimes = (!isPgnImport && chess.clock.isEmpty).option {
        BinaryFormat.moveTime.write {
          binaryMoveTimes.?? { t =>
            BinaryFormat.moveTime.read(t, playedTurns)
          } :+ Centis(nowCentis - movedAt.getCentis).nonNeg
        }
      },
      loadClockHistory = _ => newClockHistory,
      status = game.situation.status | status,
      movedAt = DateTime.now,
      metadata = metadata.copy(deadStoneOfferState = deadStoneOfferStateAfterAction)
    )

    val state = Event.State(
      playerIndex = game.situation.player,
      turns = game.turns,
      status = (status != updated.status) option updated.status,
      winner = game.situation.winner,
      p1OffersDraw = p1Player.isOfferingDraw,
      p2OffersDraw = p2Player.isOfferingDraw,
      p1OffersSelectSquares = p1Player.isOfferingSelectSquares,
      p2OffersSelectSquares = p2Player.isOfferingSelectSquares
    )

    val clockEvent = updated.chess.clock map Event.Clock.apply orElse {
      updated.playableCorrespondenceClock map Event.CorrespondenceClock.apply
    }

    val events = {
      action match {
        case m: Move => Event.Move(m, game.situation, state, clockEvent, updated.board.pocketData)
        case d: Drop => Event.Drop(d, game.situation, state, clockEvent, updated.board.pocketData)
        case p: Pass => Event.Pass(p, game.situation, state, clockEvent, updated.board.pocketData)
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
        (updated.board.variant.togyzkumalak) ?? List(
          Event.Score(p1 = updated.history.score.p1, p2 = updated.history.score.p2)
        )
      //TODO: Review these extra pieces of info. This was determined unncecessary for Go
      //after we put the captures info into the FEN
      //else if (updated.board.variant.gameLogic == GameLogic.Go())
      //  //(updated.board.variant.go9x9 | updated.board.variant.go13x13 | updated.board.variant.go19x19) ?? List(
      //  //  Event.Score(p1 = updated.history.score.p1, p2 = updated.history.score.p2)
      //  //)
      //  (updated.board.variant.go9x9 | updated.board.variant.go13x13 | updated.board.variant.go19x19) ?? updated.displayScore
      //    .map(s => Event.Score(p1 = s.p1, p2 = s.p2))
      //    .toList
      else //chess. Is this even necessary as checkCount is in the fen?
        ((updated.board.variant.threeCheck || updated.board.variant.fiveCheck) && game.situation.check) ?? List(
          Event.CheckCount(
            p1 = updated.history.checkCount.p1,
            p2 = updated.history.checkCount.p2
          )
        )
    }

    Progress(this, updated, events)
  }

  def displayScore: Option[Score] =
    if (variant.gameLogic == GameLogic.Togyzkumalak()) history.score.some
    else if (variant.gameLogic == GameLogic.Go()) {
      if (finished || selectSquaresPossible) history.score.some
      else history.captures.some
    } else none

  def lastMoveKeys: Option[String] =
    history.lastMove map {
      case d: Uci.Drop          => s"${d.pos}${d.pos}"
      case m: Uci.Move          => m.keys
      case _: Uci.Pass          => "pass"
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
      val secondsLeft = (movedAt.getSeconds + increment - nowSeconds).toInt max 0
      CorrespondenceClock(
        increment = increment,
        p1Time = activePlayerIndex.fold(secondsLeft, increment).toFloat,
        p2Time = activePlayerIndex.fold(increment, secondsLeft).toFloat
      )
    }

  def playableCorrespondenceClock: Option[CorrespondenceClock] =
    playable ?? correspondenceClock

  def speed = Speed(chess.clock.map(_.config))

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

  def playableByAi: Boolean = playable && player.isAi

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

  private def selectSquaresPossible =
    started &&
      playable &&
      turns >= 2 &&
      (situation match {
        case Situation.Go(s) => s.canSelectSquares
        case _               => false
      }) &&
      !deadStoneOfferState.map(_.is(DeadStoneOfferState.RejectedOffer)).has(true)

  private def neitherPlayerHasMadeAnOffer =
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
    selectedSquares.fold(false)(p => ("ss:" + p.mkString(",")) != uci.uci)

  def selectedSquares = metadata.selectedSquares

  def offerSelectSquares(playerIndex: PlayerIndex, squares: List[Pos]) =
    copy(
      movedAt = DateTime.now,
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
      chess = chess.copy(clock = clock.map(_.start)),
      movedAt = DateTime.now,
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
      chess = chess.copy(clock = clock.map(_.start)),
      movedAt = DateTime.now,
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
      movedAt = DateTime.now,
      metadata = metadata.copy(deadStoneOfferState = None)
    )

  def setChooseFirstOffer =
    copy(
      movedAt = DateTime.now,
      metadata = metadata.copy(
        deadStoneOfferState = DeadStoneOfferState.ChooseFirstOffer.some
      )
    )

  def playerCanOfferDraw(playerIndex: PlayerIndex) =
    started && playable &&
      turns >= 2 &&
      !player(playerIndex).isOfferingDraw &&
      !opponent(playerIndex).isAi &&
      !playerHasOfferedDrawRecently(playerIndex)

  def playerHasOfferedDrawRecently(playerIndex: PlayerIndex) =
    drawOffers.lastBy(playerIndex) ?? (_ >= turns - 20)

  def offerDraw(playerIndex: PlayerIndex) = copy(
    metadata = metadata.copy(drawOffers = drawOffers.add(playerIndex, turns))
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

  def boosted = rated && finished && bothPlayersHaveMoved && playedTurns < (10 * variant.plysPerTurn)

  def moretimeable(playerIndex: PlayerIndex) =
    playable && nonMandatory && {
      clock.??(_ moretimeable playerIndex) || correspondenceClock.??(_ moretimeable playerIndex)
    }

  def abortable =
    status == Status.Started && playedTurns < (2 * variant.plysPerTurn) && nonMandatory && nonMandatory &&
      metadata.multiMatchGameNr.fold(true)(x => x < 2)

  def berserkable =
    clock.??(_.config.berserkable) && status == Status.Started && playedTurns < (2 * variant.plysPerTurn)

  def goBerserk(playerIndex: PlayerIndex): Option[Progress] =
    clock.ifTrue(berserkable && !player(playerIndex).berserk).map { c =>
      val newClock = c goBerserk playerIndex
      Progress(
        this,
        copy(
          chess = chess.copy(clock = Some(newClock)),
          loadClockHistory = _ =>
            clockHistory.map(history => {
              if (history(playerIndex).isEmpty) history
              else history.reset(playerIndex).record(playerIndex, newClock, chess.fullMoveNumber)
            })
        ).updatePlayer(playerIndex, _.goBerserk)
      ) ++
        List(
          Event.ClockInc(playerIndex, -c.config.berserkPenalty),
          Event.Clock(newClock), // BC
          Event.Berserk(playerIndex)
        )
    }

  def resignable      = playable && !abortable
  def drawable        = playable && !abortable
  def forceResignable = resignable && nonAi && !fromFriend && hasClock && !isSwiss

  def finish(status: Status, winner: Option[PlayerIndex]) = {
    val newClock = clock map { _.stop }
    Progress(
      this,
      copy(
        status = status,
        p1Player = p1Player.finish(winner contains P1),
        p2Player = p2Player.finish(winner contains P2),
        chess = chess.copy(clock = newClock),
        loadClockHistory = clk =>
          clockHistory map { history =>
            // If not already finished, we're ending due to an event
            // in the middle of a turn, such as resignation or draw
            // acceptance. In these cases, record a final clock time
            // for the active playerIndex. This ensures the end time in
            // clockHistory always matches the final clock time on
            // the board.
            if (!finished) history.record(turnPlayerIndex, clk, chess.fullMoveNumber)
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

  def accountable = playedTurns >= (2 * variant.plysPerTurn) || isTournament

  def replayable = isPgnImport || finished || (aborted && bothPlayersHaveMoved)

  def analysable =
    replayable && playedTurns > (4 * variant.plysPerTurn) && Game.analysableVariants(variant)

  def ratingVariant =
    if (isTournament && variant.fromPosition) Variant.libStandard(variant.gameLogic)
    else variant

  def fromPosition = variant.fromPosition || source.??(Source.Position ==)

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

  private def outoftimeClock(withGrace: Boolean): Boolean =
    clock ?? { c =>
      started && playable && (bothPlayersHaveMoved || isSimul || isSwiss || fromFriend || fromApi) && {
        c.outOfTime(turnPlayerIndex, withGrace) || {
          !c.isRunning && !c.isPaused && c.clockPlayerExists(_.elapsed.centis > 0)
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

  def hasUsDelayClock = clock.fold(false)(c =>
    c.config match {
      case _: Clock.UsDelayConfig => true
      case _                      => false
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

  def withClock(c: ClockBase) = Progress(this, copy(chess = chess.copy(clock = Some(c))))

  def correspondenceGiveTime = Progress(this, copy(movedAt = DateTime.now))

  def estimateClockTotalTime = clock.map(_.estimateTotalSeconds)

  def estimateTotalTime =
    estimateClockTotalTime orElse
      correspondenceClock.map(_.estimateTotalTime) getOrElse 1200

  def timeForFirstMove: Centis =
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
      if (variant.chess960) base * 2
      else if (isTournament && (variant.draughts64Variant) && metadata.simulPairing.isDefined) base + 10
      else base
    }

  def expirableAtStart =
    !bothPlayersHaveMoved && source.exists(Source.expirable.contains) && playable && nonAi && clock.exists(
      !_.isRunning
    )

  def timeBeforeExpirationAtStart: Option[Centis] =
    expirableAtStart option {
      Centis.ofMillis(movedAt.getMillis - nowMillis + timeForFirstMove.millis).nonNeg
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
      Centis.ofMillis(movedAt.getMillis - nowMillis + timeWhenPaused.millis).nonNeg
    }

  def expirable = expirableAtStart || expirableOnPaused

  def playerWhoDidNotMove: Option[Player] =
    (playedTurns, variant.plysPerTurn) match {
      case (0, 1) => player(startPlayerIndex).some
      case (1, 1) => player(!startPlayerIndex).some
      case (0, 2) => player(startPlayerIndex).some
      case (1, 2) => player(startPlayerIndex).some
      case (2, 2) => player(!startPlayerIndex).some
      case (3, 2) => player(!startPlayerIndex).some
      case (_, _) => none
    }

  def onePlayerHasMoved    = playedTurns > variant.plysPerTurn - 1     // 0
  def bothPlayersHaveMoved = playedTurns > 2 * variant.plysPerTurn - 1 // 1

  def startPlayerIndex = PlayerIndex.fromPly(chess.startedAtTurn, variant.plysPerTurn)

  //the number of ply a player has played
  def playerMoves(playerIndex: PlayerIndex): Int =
    variant.plysPerTurn * (playedTurns / (variant.plysPerTurn * 2)) + (if (playerIndex == startPlayerIndex)
                                                                         Math.min(
                                                                           variant.plysPerTurn,
                                                                           playedTurns % (variant.plysPerTurn * 2)
                                                                         )
                                                                       else
                                                                         Math.max(
                                                                           0,
                                                                           (playedTurns % (variant.plysPerTurn * 2)) - variant.plysPerTurn
                                                                         ))

  // def playerMoves(playerIndex: PlayerIndex): Int =
  //   if (playerIndex == startPlayerIndex) (playedTurns + 1) / 2 else playedTurns / 2

  // if a player has completed their first full turn
  def playerHasMoved(playerIndex: PlayerIndex) = playerMoves(playerIndex) > (variant.plysPerTurn - 1) // 0

  def playerBlurPercent(playerIndex: PlayerIndex): Int =
    if (playedTurns > (5 * variant.plysPerTurn))
      (player(playerIndex).blurs.nb * 100) / playerMoves(playerIndex)
    else 0

  def isBeingPlayed = !isPgnImport && !finishedOrAborted

  def olderThan(seconds: Int) = movedAt isBefore DateTime.now.minusSeconds(seconds)

  def justCreated = createdAt isAfter DateTime.now.minusSeconds(1)

  def unplayed = !bothPlayersHaveMoved && (createdAt isBefore Game.unplayedDate)

  def abandoned =
    (status <= Status.Started) && {
      movedAt isBefore {
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

  def withTournamentId(id: String) = copy(metadata = metadata.copy(tournamentId = id.some))
  def withSwissId(id: String)      = copy(metadata = metadata.copy(swissId = id.some))

  def withSimulId(id: String) = copy(metadata = metadata.copy(simulId = id.some))

  def withId(newId: String) = copy(id = newId)

  def source = metadata.source

  def pgnImport   = metadata.pgnImport
  def isPgnImport = pgnImport.isDefined

  def resetTurns =
    copy(
      chess = chess.copy(turns = 0, startedAtTurn = 0)
    )

  lazy val opening: Option[FullOpening.AtPly] =
    if (fromPosition || !Variant.openingSensibleVariants(variant.gameLogic)(variant)) none
    else FullOpeningDB.search(variant.gameLogic, pgnMoves)

  def synthetic = id == Game.syntheticId

  private def playerMaps[A](f: Player => Option[A]): List[A] = players flatMap f

  def pov(c: PlayerIndex)                           = Pov(this, c)
  def playerIdPov(playerId: Player.ID): Option[Pov] = player(playerId) map { Pov(this, _) }
  def p1Pov                                         = pov(P1)
  def p2Pov                                         = pov(P2)
  def playerPov(p: Player)                          = pov(p.playerIndex)
  def loserPov                                      = loser map playerPov

  //When updating, also edit modules/challenge and ui/@types/playstrategy/index.d.ts:declare type PlayerName
  def playerTrans(p: PlayerIndex)(implicit lang: Lang) = chess.board.variant.playerNames(p) match {
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

  val maxPlayingRealtime = 100 // plus 200 correspondence games

  val maxPlies = 600 // unlimited can cause StackOverflowError

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
    variant.standard || {
      clock ?? { c =>
        c.estimateTotalTime >= Centis(3000) &&
        c.limitSeconds > 0 || c.graceSeconds > 1
      }
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

  private[game] def someEmptyByoyomiClockHistory(c: ClockBase) = c match {
    case bc: ByoyomiClock => Some(ByoyomiClockHistory(bc.config.byoyomi))
    case _                => Some(ByoyomiClockHistory(Centis(0)))
  }
  private[game] def someEmptyClockHistory(c: ClockBase) = c match {
    case _: Clock        => someEmptyFischerClockHistory
    case _: ByoyomiClock => someEmptyByoyomiClockHistory(c)
  }

  def make(
      chess: StratGame,
      p1Player: Player,
      p2Player: Player,
      mode: Mode,
      source: Source,
      pgnImport: Option[PgnImport],
      daysPerTurn: Option[Int] = None,
      drawLimit: Option[Int] = None,
      multiMatch: Option[String] = None
  ): NewGame = {
    val createdAt = DateTime.now
    NewGame(
      Game(
        id = IdGenerator.uncheckedGame,
        p1Player = p1Player,
        p2Player = p2Player,
        chess = chess,
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
        movedAt = createdAt,
        pdnStorage =
          if (chess.situation.board.variant.gameLogic == GameLogic.Draughts()) Some(PdnStorage.OldBin)
          else None
      )
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

    val id                = "_id"
    val p1Player          = "p0"
    val p2Player          = "p1"
    val playerIds         = "is"
    val playerUids        = "us"
    val playingUids       = "pl"
    val binaryPieces      = "ps"
    val oldPgn            = "pg"
    val huffmanPgn        = "hp"
    val status            = "s"
    val turns             = "t"
    val startedAtTurn     = "st"
    val clock             = "c"
    val clockType         = "ct"
    val positionHashes    = "ph"
    val checkCount        = "cc"
    val score             = "sc"
    val captures          = "cp"
    val castleLastMove    = "cl"
    val kingMoves         = "km"
    val historyLastMove   = "hlm"
    val unmovedRooks      = "ur"
    val daysPerTurn       = "cd"
    val moveTimes         = "mt"
    val p1ClockHistory    = "cw"
    val p2ClockHistory    = "cb"
    val periodsP1         = "pp0"
    val periodsP2         = "pp1"
    val rated             = "ra"
    val analysed          = "an"
    val lib               = "l"
    val variant           = "v"
    val pocketData        = "chd"
    val bookmarks         = "bm"
    val createdAt         = "ca"
    val movedAt           = "ua" // ua = updatedAt (bc)
    val source            = "so"
    val pgnImport         = "pgni"
    val tournamentId      = "tid"
    val swissId           = "iid"
    val simulId           = "sid"
    val tvAt              = "tv"
    val winnerPlayerIndex = "w"
    val winnerId          = "wid"
    val initialFen        = "if"
    val checkAt           = "ck"
    val perfType          = "pt" // only set on student games for aggregation
    val drawOffers        = "do"
    // go
    val selectedSquares     = "ss" // the dead stones selected in go
    val deadStoneOfferState = "os" //state of the dead stone offer
    //draughts
    val simulPairing = "sp"
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

case class CastleLastMove(castles: Castles, lastMove: Option[ChessUci])

object CastleLastMove {

  def init = CastleLastMove(Castles.all, None)

  import reactivemongo.api.bson._
  import lila.db.ByteArray.ByteArrayBSONHandler

  implicit private[game] val castleLastMoveBSONHandler = new BSONHandler[CastleLastMove] {
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
  def record(playerIndex: PlayerIndex, clock: ClockBase, turn: Int): ClockHistory
  def reset(playerIndex: PlayerIndex): ClockHistory
  def apply(playerIndex: PlayerIndex): Vector[Centis]
  def last(playerIndex: PlayerIndex) = apply(playerIndex).lastOption
  def size: Int
  def bothClockStates(firstMoveBy: PlayerIndex): Vector[Centis]
}

case class FischerClockHistory(
    p1: Vector[Centis] = Vector.empty,
    p2: Vector[Centis] = Vector.empty
) extends ClockHistory {

  def update(playerIndex: PlayerIndex, f: Vector[Centis] => Vector[Centis]): ClockHistory =
    playerIndex.fold(copy(p1 = f(p1)), copy(p2 = f(p2)))

  def record(playerIndex: PlayerIndex, clock: ClockBase, turn: Int): ClockHistory =
    update(playerIndex, _ :+ clock.remainingTime(playerIndex))
  def reset(playerIndex: PlayerIndex)                 = update(playerIndex, _ => Vector.empty)
  def apply(playerIndex: PlayerIndex): Vector[Centis] = playerIndex.fold(p1, p2)
  def size                                            = p1.size + p2.size

  // first state is of the playerIndex that moved first.
  override def bothClockStates(firstMoveBy: PlayerIndex): Vector[Centis] =
    Sequence.interleave(
      firstMoveBy.fold(p1, p2),
      firstMoveBy.fold(p2, p1)
    )
}

case class ByoyomiClockHistory(
    byoyomi: Centis,
    p1: Vector[Centis] = Vector.empty,
    p2: Vector[Centis] = Vector.empty,
    periodEntries: PeriodEntries = PeriodEntries.default
) extends ClockHistory {

  def update(playerIndex: PlayerIndex, f: Vector[Centis] => Vector[Centis]): ClockHistory =
    updateInternal(playerIndex, f)

  def updateInternal(playerIndex: PlayerIndex, f: Vector[Centis] => Vector[Centis]): ByoyomiClockHistory =
    playerIndex.fold(copy(p1 = f(p1)), copy(p2 = f(p2)))

  def updatePeriods(playerIndex: PlayerIndex, f: Vector[Int] => Vector[Int]): ClockHistory =
    copy(periodEntries = periodEntries.update(playerIndex, f))

  def apply(playerIndex: PlayerIndex): Vector[Centis] = playerIndex.fold(p1, p2)

  def size = p1.size + p2.size

  override def record(playerIndex: PlayerIndex, clock: ClockBase, turn: Int): ClockHistory = {
    val curClock        = clock currentClockFor playerIndex
    val initiatePeriods = clock.config.startsAtZero && periodEntries(playerIndex).isEmpty
    val isUsingByoyomi  = curClock.periods > 0 && !initiatePeriods

    val timeToStore = if (isUsingByoyomi) clock.lastMoveTimeOf(playerIndex) else curClock.time

    updateInternal(playerIndex, _ :+ timeToStore)
      .updatePeriods(
        playerIndex,
        _.padTo(initiatePeriods ?? 1, 0).padTo(curClock.periods atMost PeriodEntries.maxPeriods, turn)
      )
  }

  override def reset(playerIndex: PlayerIndex) =
    updateInternal(playerIndex, _ => Vector.empty).updatePeriods(playerIndex, _ => Vector.empty)

  def firstEnteredPeriod(playerIndex: PlayerIndex): Option[Int] =
    periodEntries(playerIndex).headOption

  def countSpentPeriods(playerIndex: PlayerIndex, turn: Int) =
    periodEntries(playerIndex).count(_ == turn)

  def refundSpentPeriods(playerIndex: PlayerIndex, turn: Int) =
    updatePeriods(playerIndex, _.filterNot(_ == turn))

  private def padWithByo(playerIndex: PlayerIndex, byo: Centis): Vector[Centis] = {
    val times = apply(playerIndex)
    times.take(firstEnteredPeriod(playerIndex).fold(times.size)(_ - 1)).padTo(times.size, byo)
  }

  // first state is of the playerIndex that moved first.
  override def bothClockStates(firstMoveBy: PlayerIndex): Vector[Centis] = {
    val p1Times = padWithByo(PlayerIndex.P1, byoyomi)
    val p2Times = padWithByo(PlayerIndex.P2, byoyomi)
    Sequence.interleave(
      firstMoveBy.fold(p1Times, p2Times),
      firstMoveBy.fold(p2Times, p1Times)
    )
  }
}
