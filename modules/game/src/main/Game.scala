package lila.game

import strategygames.format.{ FEN, Uci }
import strategygames.opening.{ FullOpening, FullOpeningDB }
import strategygames.chess.{ Castles, CheckCount }
import strategygames.chess.format.{ Uci => ChessUci }
import strategygames.{
  P2,
  Centis,
  Clock,
  Player => SGPlayer,
  Game => StratGame,
  GameLogic,
  Mode,
  Move,
  MoveOrDrop,
  Pos,
  Speed,
  Status,
  P1
}
import strategygames.variant.Variant
import org.joda.time.DateTime

import lila.common.Sequence
import lila.db.ByteArray
import lila.rating.PerfType
import lila.user.User

case class Game(
    id: Game.ID,
    p1Player: Player,
    p2Player: Player,
    chess: StratGame,
    loadClockHistory: Clock => Option[ClockHistory] = _ => Game.someEmptyClockHistory,
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

  lazy val clockHistory = chess.clock flatMap loadClockHistory

  def situation = chess.situation
  def board     = chess.situation.board
  def history   = chess.situation.board.history
  def variant   = chess.situation.board.variant
  def turns     = chess.turns
  def clock     = chess.clock
  def pgnMoves  = chess.pgnMoves

  val players = List(p1Player, p2Player)

  def player(sgPlayer: SGPlayer): Player = sgPlayer.fold(p1Player, p2Player)

  def player(playerId: Player.ID): Option[Player] =
    players find (_.id == playerId)

  def player(user: User): Option[Player] =
    players find (_ isUser user)

  def player(c: SGPlayer.type => SGPlayer): Player = player(c(SGPlayer))

  def isPlayerFullId(player: Player, fullId: String): Boolean =
    (fullId.lengthIs == Game.fullIdSize) && player.id == (fullId drop Game.gameIdSize)

  def player: Player = player(turnSGPlayer)

  def playerByUserId(userId: User.ID): Option[Player]   = players.find(_.userId contains userId)
  def opponentByUserId(userId: User.ID): Option[Player] = playerByUserId(userId) map opponent

  def hasUserIds(userId1: User.ID, userId2: User.ID) =
    playerByUserId(userId1).isDefined && playerByUserId(userId2).isDefined

  def opponent(p: Player): Player = opponent(p.sgPlayer)

  def opponent(c: SGPlayer): Player = player(!c)

  lazy val naturalOrientation = variant match {
    case Variant.Chess(v) if v.racingKings => P1
    case _                                 => SGPlayer.fromP1(p1Player before p2Player)
  }

  def turnSGPlayer = chess.player

  def turnOf(p: Player): Boolean = p == player
  def turnOf(c: SGPlayer): Boolean  = c == turnSGPlayer
  def turnOf(u: User): Boolean   = player(u) ?? turnOf

  def playedTurns = turns - chess.startedAtTurn

  def flagged = (status == Status.Outoftime).option(turnSGPlayer)

  def fullIdOf(player: Player): Option[String] =
    (players contains player) option s"$id${player.id}"

  def fullIdOf(sgPlayer: SGPlayer): String = s"$id${player(sgPlayer).id}"

  def tournamentId = metadata.tournamentId
  def simulId      = metadata.simulId
  def swissId      = metadata.swissId

  def isTournament = tournamentId.isDefined
  def isSimul      = simulId.isDefined
  def isSwiss      = swissId.isDefined
  def isMandatory  = isTournament || isSimul || isSwiss
  def isClassical  = perfType match {
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

  def moveTimes(sgPlayer: SGPlayer): Option[List[Centis]] = {
    for {
      clk <- clock
      inc = clk.incrementOf(sgPlayer)
      history <- clockHistory
      clocks = history(sgPlayer)
    } yield Centis(0) :: {
      val pairs = clocks.iterator zip clocks.iterator.drop(1)

      // We need to determine if this sgPlayer's last clock had inc applied.
      // if finished and history.size == playedTurns then game was ended
      // by a players move, such as with mate or autodraw. In this case,
      // the last move of the game, and the only one without inc, is the
      // last entry of the clock history for !turnSGPlayer.
      //
      // On the other hand, if history.size is more than playedTurns,
      // then the game ended during a players turn by async event, and
      // the last recorded time is in the history for turnSGPlayer.
      val noLastInc = finished && (history.size <= playedTurns) == (sgPlayer != turnSGPlayer)

      pairs map { case (first, second) =>
        {
          val d = first - second
          if (pairs.hasNext || !noLastInc) d + inc else d
        } nonNeg
      } toList
    }
  } orElse binaryMoveTimes.map { binary =>
    // TODO: make movetime.read return List after writes are disabled.
    val base = BinaryFormat.moveTime.read(binary, playedTurns)
    val mts  = if (sgPlayer == startSGPlayer) base else base.drop(1)
    everyOther(mts.toList)
  }

  def moveTimes: Option[Vector[Centis]] =
    for {
      a <- moveTimes(startSGPlayer)
      b <- moveTimes(!startSGPlayer)
    } yield Sequence.interleave(a, b)

  def bothClockStates: Option[Vector[Centis]] = clockHistory.map(_ bothClockStates startSGPlayer)

  def pdnMovesConcat(fullCaptures: Boolean = false, dropGhosts: Boolean = false): PgnMoves =
    chess match {
      case StratGame.Draughts(game) => game.pdnMovesConcat(fullCaptures, dropGhosts)
      case _ => sys.error("Cant call pdnMovesConcat for a gamelogic other than draughts")
    }

  def pgnMoves(sgPlayer: SGPlayer): PgnMoves = {
    val pivot = if (sgPlayer == startSGPlayer) 0 else 1
    val pgnMoves = variant.gameLogic match {
      case GameLogic.Draughts() => pdnMovesConcat()
      case _ => chess.pgnMoves
    }
    pgnMoves.zipWithIndex.collect {
      case (e, i) if (i % 2) == pivot => e
    }
  }

  // apply a move
  def update(
      game: StratGame, // new chess position
      moveOrDrop: MoveOrDrop,
      blur: Boolean = false
  ): Progress = {

    def copyPlayer(player: Player) =
      if (blur && moveOrDrop.fold(_.player, _.player) == player.sgPlayer)
        player.copy(
          blurs = player.blurs.add(playerMoves(player.sgPlayer))
        )
      else player

    // This must be computed eagerly
    // because it depends on the current time
    val newClockHistory = for {
      clk <- game.clock
      ch  <- clockHistory
    } yield ch.record(turnSGPlayer, clk)

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
      movedAt = DateTime.now
    )

    val state = Event.State(
      sgPlayer = game.situation.player,
      turns = game.turns,
      status = (status != updated.status) option updated.status,
      winner = game.situation.winner,
      p1OffersDraw = p1Player.isOfferingDraw,
      p2OffersDraw = p2Player.isOfferingDraw
    )

    val clockEvent = updated.chess.clock map Event.Clock.apply orElse {
      updated.playableCorrespondenceClock map Event.CorrespondenceClock.apply
    }

    val events = moveOrDrop.fold(
      Event.Move(_, game.situation, state, clockEvent, updated.board.pocketData),
      Event.Drop(_, game.situation, state, clockEvent, updated.board.pocketData)
    ) :: {
      // abstraction leak, I know.
      if (updated.board.variant.gameLogic == GameLogic.Draughts())
        (updated.board.variant.frisianVariant || updated.board.variant.draughts64Variant) ?? List(Event.KingMoves(
          p1 = updated.history.kingMoves.p1,
          p2 = updated.history.kingMoves.p2,
          p1King = updated.history.kingMoves.p1King.map(Pos.Draughts),
          p2King = updated.history.kingMoves.p2King.map(Pos.Draughts)
        ))
      else//chess
        (updated.board.variant.threeCheck && game.situation.check) ?? List(
          Event.CheckCount(
            p1 = updated.history.checkCount.p1,
            p2 = updated.history.checkCount.p2
          )
        )
    }

    Progress(this, updated, events)
  }

  def lastMoveKeys: Option[String] =
    history.lastMove map {
      case d: Uci.Drop => s"${d.role}${d.role}"
      case m: Uci.Move => m.keys
      case _ => sys.error("Type Error")
    }

  def updatePlayer(sgPlayer: SGPlayer, f: Player => Player) =
    sgPlayer.fold(
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
        p1Time = turnSGPlayer.fold(secondsLeft, increment).toFloat,
        p2Time = turnSGPlayer.fold(increment, secondsLeft).toFloat
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

  def playableBy(c: SGPlayer): Boolean = playableBy(player(c))

  def playableByAi: Boolean = playable && player.isAi

  def mobilePushable = isCorrespondence && playable && nonAi

  def alarmable = hasCorrespondenceClock && playable && nonAi

  def continuable =
    status != Status.Mate && status != Status.PerpetualCheck && status != Status.Stalemate

  def aiLevel: Option[Int] = players find (_.isAi) flatMap (_.aiLevel)

  def hasAi: Boolean = players.exists(_.isAi)
  def nonAi          = !hasAi

  def aiPov: Option[Pov] = players.find(_.isAi).map(_.sgPlayer) map pov

  def mapPlayers(f: Player => Player) =
    copy(
      p1Player = f(p1Player),
      p2Player = f(p2Player)
    )

  def drawOffers = metadata.drawOffers

  def playerCanOfferDraw(sgPlayer: SGPlayer) =
    started && playable &&
      turns >= 2 &&
      !player(sgPlayer).isOfferingDraw &&
      !opponent(sgPlayer).isAi &&
      !playerHasOfferedDrawRecently(sgPlayer)

  def playerHasOfferedDrawRecently(sgPlayer: SGPlayer) =
    drawOffers.lastBy(sgPlayer) ?? (_ >= turns - 20)

  def offerDraw(sgPlayer: SGPlayer) = copy(
    metadata = metadata.copy(drawOffers = drawOffers.add(sgPlayer, turns))
  ).updatePlayer(sgPlayer, _.offerDraw)

  def playerCouldRematch =
    finishedOrAborted &&
      nonMandatory &&
      !boosted && ! {
        hasAi && variant.fromPositionVariant && clock.exists(_.config.limitSeconds < 60)
      }

  def playerCanProposeTakeback(sgPlayer: SGPlayer) =
    started && playable && !isTournament && !isSimul &&
      bothPlayersHaveMoved &&
      !player(sgPlayer).isProposingTakeback &&
      !opponent(sgPlayer).isProposingTakeback

  def boosted = rated && finished && bothPlayersHaveMoved && playedTurns < 10

  def moretimeable(sgPlayer: SGPlayer) =
    playable && nonMandatory && {
      clock.??(_ moretimeable sgPlayer) || correspondenceClock.??(_ moretimeable sgPlayer)
    }

  def abortable = status == Status.Started && playedTurns < 2 && nonMandatory && nonMandatory && !metadata.microMatchGameNr.contains(2)

  def berserkable = clock.??(_.config.berserkable) && status == Status.Started && playedTurns < 2

  def goBerserk(sgPlayer: SGPlayer): Option[Progress] =
    clock.ifTrue(berserkable && !player(sgPlayer).berserk).map { c =>
      val newClock = c goBerserk sgPlayer
      Progress(
        this,
        copy(
          chess = chess.copy(clock = Some(newClock)),
          loadClockHistory = _ =>
            clockHistory.map(history => {
              if (history(sgPlayer).isEmpty) history
              else history.reset(sgPlayer).record(sgPlayer, newClock)
            })
        ).updatePlayer(sgPlayer, _.goBerserk)
      ) ++
        List(
          Event.ClockInc(sgPlayer, -c.config.berserkPenalty),
          Event.Clock(newClock), // BC
          Event.Berserk(sgPlayer)
        )
    }

  def resignable      = playable && !abortable
  def drawable        = playable && !abortable
  def forceResignable = resignable && nonAi && !fromFriend && hasClock && !isSwiss

  def finish(status: Status, winner: Option[SGPlayer]) = {
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
            // for the active sgPlayer. This ensures the end time in
            // clockHistory always matches the final clock time on
            // the board.
            if (!finished) history.record(turnSGPlayer, clk)
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

  def analysable =
    replayable && playedTurns > 4 && Game.analysableVariants(variant)

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

  def winnerSGPlayer: Option[SGPlayer] = winner map (_.sgPlayer)

  def winnerUserId: Option[String] = winner flatMap (_.userId)

  def loserUserId: Option[String] = loser flatMap (_.userId)

  def wonBy(c: SGPlayer): Option[Boolean] = winner map (_.sgPlayer == c)

  def lostBy(c: SGPlayer): Option[Boolean] = winner map (_.sgPlayer != c)

  def drawn = finished && winner.isEmpty

  def outoftime(withGrace: Boolean): Boolean =
    if (isCorrespondence) outoftimeCorrespondence else outoftimeClock(withGrace)

  private def outoftimeClock(withGrace: Boolean): Boolean =
    clock ?? { c =>
      started && playable && (bothPlayersHaveMoved || isSimul || isSwiss || fromFriend || fromApi) && {
        c.outOfTime(turnSGPlayer, withGrace) || {
          !c.isRunning && c.players.exists(_.elapsed.centis > 0)
        }
      }
    }

  private def outoftimeCorrespondence: Boolean =
    playableCorrespondenceClock ?? { _ outoftime turnSGPlayer }

  def isCorrespondence = speed == Speed.Correspondence

  def isSwitchable = nonAi && (isCorrespondence || isSimul)

  def hasClock = clock.isDefined

  def hasCorrespondenceClock = daysPerTurn.isDefined

  def isUnlimited = !hasClock && !hasCorrespondenceClock

  def isClockRunning = clock ?? (_.isRunning)

  def withClock(c: Clock) = Progress(this, copy(chess = chess.copy(clock = Some(c))))

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
      if (variant.chess960) base * 5 / 4
      if (isTournament && (variant.draughts64Variant) && metadata.simulPairing.isDefined) base + 10
      else base
    }

  def expirable =
    !bothPlayersHaveMoved && source.exists(Source.expirable.contains) && playable && nonAi && clock.exists(
      !_.isRunning
    )

  def timeBeforeExpiration: Option[Centis] =
    expirable option {
      Centis.ofMillis(movedAt.getMillis - nowMillis + timeForFirstMove.millis).nonNeg
    }

  def playerWhoDidNotMove: Option[Player] =
    playedTurns match {
      case 0 => player(startSGPlayer).some
      case 1 => player(!startSGPlayer).some
      case _ => none
    }

  def onePlayerHasMoved    = playedTurns > 0
  def bothPlayersHaveMoved = playedTurns > 1

  def startSGPlayer = SGPlayer.fromPly(chess.startedAtTurn)

  def playerMoves(sgPlayer: SGPlayer): Int =
    if (sgPlayer == startSGPlayer) (playedTurns + 1) / 2
    else playedTurns / 2

  def playerHasMoved(sgPlayer: SGPlayer) = playerMoves(sgPlayer) > 0

  def playerBlurPercent(sgPlayer: SGPlayer): Int =
    if (playedTurns > 5) (player(sgPlayer).blurs.nb * 100) / playerMoves(sgPlayer)
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

  def pov(c: SGPlayer)                                 = Pov(this, c)
  def playerIdPov(playerId: Player.ID): Option[Pov] = player(playerId) map { Pov(this, _) }
  def p1Pov                                      = pov(P1)
  def p2Pov                                      = pov(P2)
  def playerPov(p: Player)                          = pov(p.sgPlayer)
  def loserPov                                      = loser map playerPov

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

  val analysableVariants: Set[Variant] = Variant.all.filter(_.aiVariant).toSet

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

  def allowRated(variant: Variant, clock: Option[Clock.Config]) =
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

  def isBoardCompatible(clock: Clock.Config): Boolean =
    Speed(clock) >= Speed.Rapid

  def isBotCompatible(game: Game): Boolean = {
    game.hasAi || game.fromFriend || game.fromApi
  } && isBotCompatible(game.speed)

  def isBotCompatible(speed: Speed): Boolean = speed >= Speed.Bullet

  private[game] val emptyCheckCount = CheckCount(0, 0)

  private[game] val someEmptyClockHistory = Some(ClockHistory())

  def make(
      chess: StratGame,
      p1Player: Player,
      p2Player: Player,
      mode: Mode,
      source: Source,
      pgnImport: Option[PgnImport],
      daysPerTurn: Option[Int] = None,
      drawLimit: Option[Int] = None,
      microMatch: Option[String] = None
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
        metadata = metadata(source).copy(pgnImport = pgnImport, drawLimit = drawLimit, microMatch = microMatch),
        createdAt = createdAt,
        movedAt = createdAt,
        pdnStorage = if (chess.situation.board.variant.gameLogic == GameLogic.Draughts()) Some(PdnStorage.OldBin) else None
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
    val p1Player       = "p0"
    val p2Player       = "p1"
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
    val positionHashes    = "ph"
    val checkCount        = "cc"
    val castleLastMove    = "cl"
    val kingMoves         = "km"
    val historyLastMove   = "hlm"
    val unmovedRooks      = "ur"
    val daysPerTurn       = "cd"
    val moveTimes         = "mt"
    val p1ClockHistory = "cw"
    val p2ClockHistory = "cb"
    val rated             = "ra"
    val analysed          = "an"
    val lib               = "l"
    val variant           = "v"
    val pocketData         = "chd"
    val bookmarks         = "bm"
    val createdAt         = "ca"
    val movedAt           = "ua" // ua = updatedAt (bc)
    val source            = "so"
    val pgnImport         = "pgni"
    val tournamentId      = "tid"
    val swissId           = "iid"
    val simulId           = "sid"
    val tvAt              = "tv"
    val winnerSGPlayer       = "w"
    val winnerId          = "wid"
    val initialFen        = "if"
    val checkAt           = "ck"
    val perfType          = "pt" // only set on student games for aggregation
    val drawOffers        = "do"
    //draughts
    val simulPairing      = "sp"
    val timeOutUntil      = "to"
    val microMatch        = "mm"
    val drawLimit         = "dl"
  }
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

case class ClockHistory(
    p1: Vector[Centis] = Vector.empty,
    p2: Vector[Centis] = Vector.empty
) {

  def update(sgPlayer: SGPlayer, f: Vector[Centis] => Vector[Centis]): ClockHistory =
    sgPlayer.fold(copy(p1 = f(p1)), copy(p2 = f(p2)))

  def record(sgPlayer: SGPlayer, clock: Clock): ClockHistory =
    update(sgPlayer, _ :+ clock.remainingTime(sgPlayer))

  def reset(sgPlayer: SGPlayer) = update(sgPlayer, _ => Vector.empty)

  def apply(sgPlayer: SGPlayer): Vector[Centis] = sgPlayer.fold(p1, p2)

  def last(sgPlayer: SGPlayer) = apply(sgPlayer).lastOption

  def size = p1.size + p2.size

  // first state is of the sgPlayer that moved first.
  def bothClockStates(firstMoveBy: SGPlayer): Vector[Centis] =
    Sequence.interleave(
      firstMoveBy.fold(p1, p2),
      firstMoveBy.fold(p2, p1)
    )
}
