package lila.game

import strategygames.format.{ FEN, Uci }
import strategygames.opening.{ FullOpening, FullOpeningDB }
import strategygames.chess.{ Castles, CheckCount }
import strategygames.chess.format.{ Uci => ChessUci }
import strategygames.{
  ActionStrs,
  Centis,
  ByoyomiClock,
  Clock,
  ClockConfig,
  FischerClock,
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
    loadClockHistory: Clock => Option[ClockHistory] = Game.someEmptyClockHistory,
    status: Status,
    daysPerTurn: Option[Int],
    binaryPlyTimes: Option[ByteArray] = None,
    mode: Mode = Mode.default,
    bookmarks: Int = 0,
    createdAt: DateTime = DateTime.now,
    updatedAt: DateTime = DateTime.now,
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

  def turnPlayerIndex = stratGame.player

  //For the front end - whose 'turn' is it? (SG + Go select squares status)
  def activePlayerIndex = playerToOfferSelectSquares.getOrElse(turnPlayerIndex)

  def turnOf(p: Player): Boolean      = p == player
  def turnOf(c: PlayerIndex): Boolean = c == turnPlayerIndex
  def turnOf(u: User): Boolean        = player(u) ?? turnOf

  def playedTurns = turnCount - stratGame.startedAtTurn
  //this is a bit messy.
  //the check on plies == turnCount ensures the logic for non multiaction games is unchanged
  //once draughts is converted we should be able to use actionStrs.flatten.size everywhere
  def playedPlies = if (plies == turnCount) playedTurns else actionStrs.flatten.size

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
      inc = clk.incrementOf(playerIndex)
      byo = clk match {
        case bc: ByoyomiClock => bc.byoyomiOf(playerIndex)
        case _                => Centis(0)
      }
      history <- clockHistory
      clocks = history(playerIndex)
    } yield Centis(0) :: {
      val pairs = clocks.iterator zip clocks.iterator.drop(1)

      // We need to determine if this playerIndex's last clock had inc applied.
      // if finished and history.size == playedPlies then game was ended
      // by a players ply, such as with mate or autodraw. In this case,
      // the last ply of the game, and the only one without inc, is the
      // last entry of the clock history for !turnPlayerIndex.
      //
      // On the other hand, if history.size is more than playedPlies,
      // then the game ended during a players turn by async event, and
      // the last recorded time is in the history for turnPlayerIndex.
      val noLastInc = finished && (history.size <= playedPlies) == (playerIndex != turnPlayerIndex)

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
          //TODO review 'turn' for multiaction
          val turn         = index + 2 + stratGame.startedAtTurn / 2
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
  } orElse binaryPlyTimes.map { binary =>
    // Thibault TODO: make plyTime.read return List after writes are disabled.
    val base = BinaryFormat.plyTime.read(binary, playedPlies)
    val mts  = if (playerIndex == startPlayerIndex) base else base.drop(1)
    everyOther(mts.toList)
  }

  def plyTimes: Option[Vector[Centis]] =
    for {
      a <- plyTimes(startPlayerIndex)
      b <- plyTimes(!startPlayerIndex)
    } yield Sequence.interleave(a, b)

  def bothClockStates: Option[Vector[Centis]] =
    clockHistory.map(ch =>
      ch match {
        case fch: FischerClockHistory => fch.bothClockStates(startPlayerIndex)
        case bch: ByoyomiClockHistory =>
          bch.bothClockStates(
            startPlayerIndex,
            clock ?? (c =>
              c match {
                case bc: ByoyomiClock => bc.byoyomi
                case _                => Centis(0)
              }
            )
          )
      }
    )

  def draughtsActionStrsConcat(fullCaptures: Boolean = false, dropGhosts: Boolean = false): ActionStrs =
    stratGame match {
      case StratGame.Draughts(game) => game.actionStrsConcat(fullCaptures, dropGhosts)
      case _                        => sys.error("Cant call actionStrsConcat for a gamelogic other than draughts")
    }

  def actionStrs(playerIndex: PlayerIndex): ActionStrs = {
    val pivot = if (playerIndex == startPlayerIndex) 0 else 1
    val plys = variant.gameLogic match {
      case GameLogic.Draughts() => draughtsActionStrsConcat()
      case _                    => actionStrs
    }
    plys.zipWithIndex.collect {
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
      loadClockHistory = _ => newClockHistory,
      status = game.situation.status | status,
      updatedAt = DateTime.now,
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
      turnCount >= 2 &&
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
      val newClock = c goBerserk playerIndex
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

  def accountable = playedTurns >= 2 || isTournament

  def replayable = isPgnImport || finished || (aborted && bothPlayersHaveMoved)

  def analysable = replayable && playedTurns > 4 && Game.analysableVariants(variant)

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
    c match {
      case _: FischerClock => true
      case _               => false
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

  def withClock(c: Clock) = Progress(this, copy(stratGame = stratGame.copy(clock = Some(c))))

  def correspondenceGiveTime = Progress(this, copy(updatedAt = DateTime.now))

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
      Centis.ofMillis(updatedAt.getMillis - nowMillis + timeForFirstMove.millis).nonNeg
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

  def playerWhoDidNotMove: Option[Player] =
    if (!onePlayerHasMoved) player(startPlayerIndex).some
    else if (!bothPlayersHaveMoved) player(!startPlayerIndex).some
    else none

  //old
  //def playerWhoDidNotMove: Option[Player] =
  //  (playedTurns, variant.plysPerTurn) match {
  //    case (0, 1) => player(startPlayerIndex).some
  //    case (1, 1) => player(!startPlayerIndex).some
  //    case (0, 2) => player(startPlayerIndex).some
  //    case (1, 2) => player(startPlayerIndex).some
  //    case (2, 2) => player(!startPlayerIndex).some
  //    case (3, 2) => player(!startPlayerIndex).some
  //    case (_, _) => none
  //  }

  def onePlayerHasMoved    = playedTurns >= 1
  def bothPlayersHaveMoved = playedTurns >= 2

  def startPlayerIndex                     = PlayerIndex.fromTurnCount(stratGame.startedAtTurn)
  def startIndex(playerIndex: PlayerIndex) = if (playerIndex == startPlayerIndex) 0 else 1

  //the number of ply a player has played
  def playerMoves(playerIndex: PlayerIndex): Int =
    actionStrs.zipWithIndex.filter(_._2 % 2 == startIndex(playerIndex)).map(_._1.size).sum

  //old when using plysPerTurn
  //def playerMoves(playerIndex: PlayerIndex): Int =
  //  variant.plysPerTurn * (playedTurns / (variant.plysPerTurn * 2)) + (if (playerIndex == startPlayerIndex)
  //                                                                       Math.min(
  //                                                                         variant.plysPerTurn,
  //                                                                         playedTurns % (variant.plysPerTurn * 2)
  //                                                                       )
  //                                                                     else
  //                                                                       Math.max(
  //                                                                         0,
  //                                                                         (playedTurns % (variant.plysPerTurn * 2)) - variant.plysPerTurn
  //                                                                       ))

  //old pre-multimove
  // def playerMoves(playerIndex: PlayerIndex): Int =
  //   if (playerIndex == startPlayerIndex) (playedTurns + 1) / 2 else playedTurns / 2

  // if a player has completed their first full turn
  def playerHasMoved(playerIndex: PlayerIndex) =
    //does this actually confirm the full turn is completed?
    if (startIndex(playerIndex) == 0) onePlayerHasMoved else bothPlayersHaveMoved

  //old
  //def playerHasMoved(playerIndex: PlayerIndex) = playerMoves(playerIndex) > (variant.plysPerTurn - 1) // 0

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
        startedAtPlies = 0,
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

  //When updating, also edit modules/challenge and ui/@types/playstrategy/index.d.ts:declare type PlayerName
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

  val maxPlayingRealtime = 100 // plus 200 correspondence games

  val maxPlies = 600      // unlimited can cause StackOverflowError
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
    variant.standard || {
      clock ?? { c =>
        c.estimateTotalTime >= Centis(3000) &&
        c.limitSeconds > 0 || c.incrementSeconds > 1
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
  private[game] val someEmptyByoyomiClockHistory = Some(ByoyomiClockHistory())
  private[game] def someEmptyClockHistory(c: Clock) = c match {
    case _: FischerClock => someEmptyFischerClockHistory
    case _: ByoyomiClock => someEmptyByoyomiClockHistory
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
      multiMatch: Option[String] = None
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
        updatedAt = createdAt
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
    val plies             = "p"
    val activePlayer      = "ap"
    val startedAtPlies    = "sp"
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
    val plyTimes          = "mt" // was called moveTimes hence mt
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
    val updatedAt         = "ua"
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
  def record(playerIndex: PlayerIndex, clock: Clock, fullTurnCount: Int): ClockHistory
  def reset(playerIndex: PlayerIndex): ClockHistory
  def apply(playerIndex: PlayerIndex): Vector[Centis]
  //def last(playerIndex: PlayerIndex): Option[Centis]
  def lastX(playerIndex: PlayerIndex, plys: Int): Option[Centis]
  def size: Int
}

case class FischerClockHistory(
    p1: Vector[Centis] = Vector.empty,
    p2: Vector[Centis] = Vector.empty
) extends ClockHistory {

  def update(playerIndex: PlayerIndex, f: Vector[Centis] => Vector[Centis]): ClockHistory =
    playerIndex.fold(copy(p1 = f(p1)), copy(p2 = f(p2)))

  def record(playerIndex: PlayerIndex, clock: Clock, fullTurnCount: Int): ClockHistory =
    update(playerIndex, _ :+ clock.remainingTime(playerIndex))

  def reset(playerIndex: PlayerIndex) = update(playerIndex, _ => Vector.empty)

  def apply(playerIndex: PlayerIndex): Vector[Centis] = playerIndex.fold(p1, p2)

  //def last(playerIndex: PlayerIndex) = apply(playerIndex).lastOption

  def lastX(playerIndex: PlayerIndex, plys: Int): Option[Centis] =
    if (apply(playerIndex).size < plys) None
    else apply(playerIndex).takeRight(plys).headOption

  def size = p1.size + p2.size

  // first state is of the playerIndex that moved first.
  def bothClockStates(firstMoveBy: PlayerIndex): Vector[Centis] =
    Sequence.interleave(
      firstMoveBy.fold(p1, p2),
      firstMoveBy.fold(p2, p1)
    )
}

case class ByoyomiClockHistory(
    p1: Vector[Centis] = Vector.empty,
    p2: Vector[Centis] = Vector.empty,
    periodEntries: PeriodEntries = PeriodEntries.default
) extends ClockHistory {

  def apply(playerIndex: PlayerIndex): Vector[Centis] = playerIndex.fold(p1, p2)

  def update(playerIndex: PlayerIndex, f: Vector[Centis] => Vector[Centis]): ClockHistory =
    updateInternal(playerIndex, f)

  def updateInternal(playerIndex: PlayerIndex, f: Vector[Centis] => Vector[Centis]): ByoyomiClockHistory =
    playerIndex.fold(copy(p1 = f(p1)), copy(p2 = f(p2)))

  def updatePeriods(playerIndex: PlayerIndex, f: Vector[Int] => Vector[Int]): ClockHistory =
    copy(periodEntries = periodEntries.update(playerIndex, f))

  def record(playerIndex: PlayerIndex, clock: Clock, fullTurnCount: Int): ClockHistory = {
    val curClock        = clock currentClockFor playerIndex
    val initiatePeriods = clock.config.startsAtZero && periodEntries(playerIndex).isEmpty
    val isUsingByoyomi  = curClock.periods > 0 && !initiatePeriods

    val timeToStore = if (isUsingByoyomi) clock.lastMoveTimeOf(playerIndex) else curClock.time

    updateInternal(playerIndex, _ :+ timeToStore)
      .updatePeriods(
        playerIndex,
        _.padTo(initiatePeriods ?? 1, 0)
        //TODO discuss whether using fullTurnCount is correct now
        //most clock stuff deals in plies but we dont necessarily have a fixed number of plies each
        //what are we padding out here?
          .padTo(curClock.periods atMost PeriodEntries.maxPeriods, fullTurnCount)
      )
  }

  def reset(playerIndex: PlayerIndex) =
    updateInternal(playerIndex, _ => Vector.empty).updatePeriods(playerIndex, _ => Vector.empty)

  //def last(playerIndex: PlayerIndex) = apply(playerIndex).lastOption

  def lastX(playerIndex: PlayerIndex, plys: Int): Option[Centis] =
    if (apply(playerIndex).size < plys) None
    else apply(playerIndex).takeRight(plys).headOption

  def size = p1.size + p2.size

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
  def bothClockStates(firstMoveBy: PlayerIndex, byo: Centis): Vector[Centis] = {
    val p1Times = padWithByo(PlayerIndex.P1, byo)
    val p2Times = padWithByo(PlayerIndex.P2, byo)
    Sequence.interleave(
      firstMoveBy.fold(p1Times, p2Times),
      firstMoveBy.fold(p2Times, p1Times)
    )
  }
}
