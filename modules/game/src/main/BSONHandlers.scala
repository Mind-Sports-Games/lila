package lila.game

import strategygames.{
  Player => PlayerIndex,
  ClockConfig,
  Clock,
  ClockBase,
  ByoyomiClock,
  P1,
  P2,
  Game => StratGame,
  GameFamily,
  GameLogic,
  History,
  Status,
  Mode,
  Piece,
  Pocket,
  PocketData,
  Pockets,
  Pos,
  PositionHash,
  Score,
  Situation,
  Board,
  Role
}
import strategygames.chess
import strategygames.draughts
import strategygames.fairysf
import strategygames.samurai
import strategygames.togyzkumalak
import strategygames.go
import strategygames.backgammon
import strategygames.format.Uci
import strategygames.format.FEN
import strategygames.variant.Variant
import strategygames.chess.variant.{ Variant => ChessVariant, Standard => ChessStandard }
import strategygames.draughts.variant.{ Variant => DraughtsVariant, Standard => DraughtsStandard }
import strategygames.fairysf.variant.{ Variant => FairySFVariant, Shogi => FairySFStandard }
import strategygames.samurai.variant.{ Variant => SamuraiVariant, Oware => SamuraiStandard }
import strategygames.togyzkumalak.variant.{
  Variant => TogyzkumalakVariant,
  Togyzkumalak => TogyzkumalakStandard
}
import strategygames.go.variant.{ Variant => GoVariant, Go19x19 => GoStandard }
import strategygames.backgammon.variant.{ Variant => BackgammonVariant, Backgammon => BackgammonStandard }
import org.joda.time.DateTime
import reactivemongo.api.bson._
import scala.util.{ Success, Try }

import lila.db.BSON
import lila.db.dsl._

object BSONHandlers {

  import lila.db.ByteArray.ByteArrayBSONHandler

  implicit private[game] val checkCountWriter = new BSONWriter[chess.CheckCount] {
    def writeTry(cc: chess.CheckCount) = Success(BSONArray(cc.p1, cc.p2))
  }

  implicit private[game] val scoreWriter = new BSONWriter[Score] {
    def writeTry(sc: Score) = Success(BSONArray(sc.p1, sc.p2))
  }

  implicit val StatusBSONHandler = tryHandler[Status](
    { case BSONInteger(v) => Status(v) toTry s"No such status: $v" },
    x => BSONInteger(x.id)
  )

  implicit private[game] val unmovedRooksHandler = tryHandler[chess.UnmovedRooks](
    { case bin: BSONBinary => ByteArrayBSONHandler.readTry(bin) map BinaryFormat.unmovedRooks.read },
    x => ByteArrayBSONHandler.writeTry(BinaryFormat.unmovedRooks write x).get
  )

  implicit private[game] val pocketDataBSONHandler = new BSON[PocketData] {

    def reads(r: BSON.Reader) =
      GameLogic(r.intD("l")) match {
        case GameLogic.Chess() =>
          PocketData.Chess(
            chess.PocketData(
              pockets = {
                val (p1, p2) = {
                  r.str("p").view.flatMap(c => chess.Piece.fromChar(c)).to(List)
                }.partition(_ is P1)
                Pockets(
                  p1 = Pocket(p1.map(_.role).map(Role.ChessRole)),
                  p2 = Pocket(p2.map(_.role).map(Role.ChessRole))
                )
              },
              promoted = r.str("t").view.flatMap(chess.Pos.piotr).to(Set)
            )
          )
        case GameLogic.FairySF() =>
          PocketData.FairySF(
            fairysf.PocketData(
              pockets = {
                val (p1, p2) = {
                  r.str("p").view.flatMap(c => fairysf.Piece.fromChar(GameFamily(r.intD("f")), c)).to(List)
                }.partition(_ is P1)
                Pockets(
                  p1 = Pocket(p1.map(_.role).map(Role.FairySFRole)),
                  p2 = Pocket(p2.map(_.role).map(Role.FairySFRole))
                )
              },
              promoted = r.str("t").view.flatMap(fairysf.Pos.piotr).to(Set)
            )
          )
        case GameLogic.Go() =>
          PocketData.Go(
            go.PocketData(
              pockets = {
                val (p1, p2) = {
                  r.str("p").view.flatMap(c => go.Piece.fromChar(c)).to(List)
                }.partition(_ is P1)
                Pockets(
                  p1 = Pocket(p1.map(_.role).map(Role.GoRole)),
                  p2 = Pocket(p2.map(_.role).map(Role.GoRole))
                )
              }
            )
          )
        //TODO consider what we want to read out for backgammon pocket
        case GameLogic.Backgammon() =>
          PocketData.Backgammon(
            backgammon.PocketData(
              pockets = {
                val (p1, p2) = {
                  r.str("p")
                    .view
                    .flatMap(c => fromBackgammonChar(c))
                    .to(List)
                }.partition(_ is P1)
                Pockets(
                  p1 = Pocket(p1.map(_.role).map(Role.BackgammonRole)),
                  p2 = Pocket(p2.map(_.role).map(Role.BackgammonRole))
                )
              }
            )
          )
        case _ => sys.error(s"Pocket Data BSON reader not implemented for GameLogic: ${r.intD("l")}")
      }

    //todo put this in SG Piece.scala
    def fromBackgammonChar(c: Char): Option[backgammon.Piece] =
      backgammon.Role.allByPgn get c.toUpper map {
        backgammon.Piece(PlayerIndex.fromP1(c.isUpper), _)
      }

    def writes(w: BSON.Writer, o: PocketData) =
      BSONDocument(
        "l" -> (o match {
          case PocketData.Chess(_)      => 0
          case PocketData.FairySF(_)    => 2
          case PocketData.Go(_)         => 5
          case PocketData.Backgammon(_) => 6
          case _                        => sys.error("Pocket Data BSON Handler not implemented for GameLogic")
        }),
        "f" -> (o match {
          case PocketData.Chess(_)      => 0
          case PocketData.FairySF(pd)   => pd.gameFamily.getOrElse(GameFamily.Shogi()).id
          case PocketData.Go(_)         => 9
          case PocketData.Backgammon(_) => 10
        }),
        "p" -> {
          o.pockets.p1.roles.map(_.forsyth.toUpper).mkString +
            o.pockets.p2.roles.map(_.forsyth).mkString
        },
        "t" -> o.promoted.map(_.piotr).mkString
      )
  }

  implicit private[game] val gameDrawOffersHandler = tryHandler[GameDrawOffers](
    { case arr: BSONArray =>
      Success(arr.values.foldLeft(GameDrawOffers.empty) {
        case (offers, BSONInteger(p)) =>
          if (p > 0) offers.copy(p1 = offers.p1 incl p)
          else offers.copy(p2 = offers.p2 incl -p)
        case (offers, _) => offers
      })
    },
    offers => BSONArray((offers.p1 ++ offers.p2.map(-_)).view.map(BSONInteger.apply).toIndexedSeq)
  )

  import Player.playerBSONHandler
  private val emptyPlayerBuilder = playerBSONHandler.read($empty)

  implicit private[game] val kingMovesWriter = new BSONWriter[draughts.KingMoves] {
    def writeTry(km: draughts.KingMoves) = Try {
      BSONArray(km.p1, km.p2, km.p1King.fold(0)(_.fieldNumber), km.p2King.fold(0)(_.fieldNumber))
    }
  }

  implicit val gameBSONHandler: BSON[Game] = new BSON[Game] {

    import Game.{ BSONFields => F }
    import PgnImport.pgnImportBSONHandler

    def reads(r: BSON.Reader): Game = {

      lila.mon.game.fetch.increment()

      val light     = lightGameBSONHandler.readsWithPlayerIds(r, r str F.playerIds)
      val createdAt = r date F.createdAt

      val startedAtTurn = r intD F.startedAtTurn
      val startedAtPly  = r intD F.startedAtPly
      // do we need to cap turns on reading?
      val turns = r int F.turns atMost Game.maxTurns
      // capping because unlimited can cause StackOverflowError
      val plies = ((r intO F.plies) | turns) atMost Game.maxPlies

      val playedPlies = plies - startedAtPly

      val turnPlayerIndex = PlayerIndex(((r intO F.activePlayer) | (turns % 2 + 1)) == 1)

      val periodEntries = readPeriodEntries(r)

      val defaultMetaData =
        Metadata(
          source = r intO F.source flatMap Source.apply,
          pgnImport = r.getO[PgnImport](F.pgnImport)(PgnImport.pgnImportBSONHandler),
          tournamentId = r strO F.tournamentId,
          swissId = r strO F.swissId,
          simulId = r strO F.simulId,
          multiMatch = r strO F.multiMatch,
          analysed = r boolD F.analysed,
          drawOffers = r.getD(F.drawOffers, GameDrawOffers.empty)
        )

      val clock = r.getO[PlayerIndex => ClockBase](F.clock) {
        clockBSONReader(
          r.intO(F.clockType),
          createdAt,
          periodEntries,
          light.p1Player.berserk,
          light.p2Player.berserk
        )
      } map (_(turnPlayerIndex))

      def readChessGame(r: BSON.Reader): (StratGame, Metadata) = {

        val gameVariant = ChessVariant(r intD F.variant) | ChessStandard

        val decoded = r.bytesO(F.huffmanPgn).map { PgnStorage.Huffman.decode(_, playedPlies) } | {
          val clm        = r.get[CastleLastMove](F.castleLastMove)
          val actionStrs = PgnStorage.OldBin.decode(r bytesD F.oldPgn, playedPlies)
          PgnStorage.Decoded(
            actionStrs = actionStrs,
            pieces = BinaryFormat.piece.readChess(r bytes F.binaryPieces, gameVariant),
            positionHashes = r.getO[PositionHash](F.positionHashes) | Array.empty,
            unmovedRooks = r.getO[chess.UnmovedRooks](F.unmovedRooks) | chess.UnmovedRooks.default,
            lastMove = clm.lastMove,
            castles = clm.castles,
            //TODO monster chess. Currently we can flatten as chess does not have any multiaction games (yet)
            halfMoveClock = actionStrs.flatten.reverse.indexWhere(san =>
              san.contains("x") || san.headOption.exists(_.isLower)
            ) atLeast 0
          )
        }

        def turnUcis(turnStr: Option[String]) =
          turnStr.map(_.split(",").toList.flatMap(chess.format.Uci.apply))

        //Need to store lastTurn differently in case of multiaction
        val lastTurnUcis    = turnUcis(r strO F.historyLastTurn).getOrElse(decoded.lastMove.toList)
        val currentTurnUcis = turnUcis(r strO F.historyCurrentTurn).getOrElse(List.empty)

        val chessGame = StratGame.Chess(
          chess.Game(
            situation = chess.Situation(
              chess.Board(
                pieces = decoded.pieces,
                history = chess.History(
                  lastTurn = lastTurnUcis,
                  currentTurn = currentTurnUcis,
                  castles = decoded.castles,
                  halfMoveClock = decoded.halfMoveClock,
                  positionHashes = decoded.positionHashes,
                  unmovedRooks = decoded.unmovedRooks,
                  checkCount = if (gameVariant.key == "threeCheck" || gameVariant.key == "fiveCheck") {
                    val counts = r.intsD(F.checkCount)
                    chess.CheckCount(~counts.headOption, ~counts.lastOption)
                  } else Game.emptyCheckCount
                ),
                variant = gameVariant,
                pocketData = gameVariant.dropsVariant option (r.get[PocketData](F.pocketData)) match {
                  case Some(PocketData.Chess(pd)) => Some(pd)
                  case None                       => None
                  case _                          => sys.error("non chess pocket data")
                }
              ),
              player = turnPlayerIndex
            ),
            actionStrs = decoded.actionStrs,
            clock = clock,
            plies = plies,
            turnCount = turns,
            startedAtPly = startedAtPly,
            startedAtTurn = startedAtTurn
          )
        )

        (chessGame, defaultMetaData)
      }

      def readDraughtsGame(r: BSON.Reader): (StratGame, Metadata) = {

        val gameVariant = DraughtsVariant(r intD F.variant) | DraughtsStandard

        val actionStrs = NewLibStorage.OldBin.decode(GameLogic.Draughts(), r bytesD F.oldPgn, playedPlies)

        val decodedBoard = draughts.Board(
          pieces = BinaryFormat.piece.readDraughts(r bytes F.binaryPieces, gameVariant),
          history = draughts.DraughtsHistory(
            //whilst Draughts isnt upgraded to multiaction
            lastMove = r strO F.historyLastTurn flatMap (draughts.format.Uci.apply),
            positionHashes = r.getO[PositionHash](F.positionHashes) | Array.empty,
            kingMoves = if (gameVariant.frisianVariant || gameVariant.draughts64Variant) {
              val counts = r.intsD(F.kingMoves)
              if (counts.length > 0) {
                draughts.KingMoves(
                  ~counts.headOption,
                  ~counts.tail.headOption,
                  if (counts.length > 2) gameVariant.boardSize.pos.posAt(counts(2)) else none,
                  if (counts.length > 3) gameVariant.boardSize.pos.posAt(counts(3)) else none
                )
              } else draughts.KingMoves(0, 0)
            } else draughts.KingMoves(0, 0),
            variant = gameVariant
          ),
          variant = gameVariant
        )

        //we can flatten as draughts does not have any multiaction games (yet)
        val midCapture =
          actionStrs.flatten.lastOption.fold(false)(_.indexOf('x') != -1) && decodedBoard.ghosts != 0
        val currentPly = if (midCapture) plies - 1 else plies

        val decodedSituation = draughts.Situation(
          board = decodedBoard,
          player = turnPlayerIndex
        )

        val draughtsGame = StratGame.Draughts(
          draughts.DraughtsGame(
            situation = decodedSituation,
            actionStrs = actionStrs,
            clock = clock,
            //whilst Draughts isnt upgraded to multiaction
            plies = currentPly,
            turnCount = currentPly,
            startedAtPly = startedAtPly,
            startedAtTurn = startedAtTurn
          )
        )

        val metadata = Metadata(
          source = r intO F.source flatMap Source.apply,
          pgnImport = r.getO[PgnImport](F.pgnImport)(PgnImport.pgnImportBSONHandler),
          tournamentId = r strO F.tournamentId,
          swissId = r strO F.swissId,
          simulId = r strO F.simulId,
          simulPairing = r intO F.simulPairing,
          timeOutUntil = r dateO F.timeOutUntil,
          multiMatch = r strO F.multiMatch,
          drawLimit = r intO F.drawLimit,
          analysed = r boolD F.analysed,
          drawOffers = r.getD(F.drawOffers, GameDrawOffers.empty) //should be empty for draughts
        )

        (draughtsGame, metadata)
      }

      def readFairySFGame(r: BSON.Reader): (StratGame, Metadata) = {

        val gameVariant = FairySFVariant(r intD F.variant) | FairySFStandard

        val actionStrs = NewLibStorage.OldBin.decode(GameLogic.FairySF(), r bytesD F.oldPgn, playedPlies)

        def turnUcis(turnStr: Option[String]) =
          turnStr
            .map(_.split(",").toList.flatMap(uci => fairysf.format.Uci.apply(gameVariant.gameFamily, uci)))
            .getOrElse(List.empty)

        val fairysfGame = StratGame.FairySF(
          fairysf.Game(
            situation = fairysf.Situation(
              fairysf.Board(
                pieces = BinaryFormat.piece.readFairySF(r bytes F.binaryPieces, gameVariant),
                history = fairysf.History(
                  lastTurn = turnUcis(r strO F.historyLastTurn),
                  currentTurn = turnUcis(r strO F.historyCurrentTurn),
                  //we can flatten as fairysf does not have any true multiaction games (yet)
                  //TODO: Is halfMoveClock even doing anything for fairysf?
                  halfMoveClock = actionStrs.flatten.reverse.indexWhere(san =>
                    san.contains("x") || san.headOption.exists(_.isLower)
                  ) atLeast 0,
                  positionHashes = r.getO[PositionHash](F.positionHashes) | Array.empty
                ),
                variant = gameVariant,
                pocketData = gameVariant.dropsVariant option (r.get[PocketData](F.pocketData)) match {
                  case Some(PocketData.FairySF(pd)) => Some(pd)
                  case None                         => None
                  case _                            => sys.error("non fairysf pocket data")
                },
                uciMoves = strategygames.fairysf.format.pgn.Parser
                  .flatActionStrsToFairyUciMoves(actionStrs.flatten, !gameVariant.switchPlayerAfterMove)
              ),
              player = turnPlayerIndex
            ),
            actionStrs = actionStrs,
            clock = clock,
            plies = plies,
            turnCount = turns,
            startedAtPly = startedAtPly,
            startedAtTurn = startedAtTurn
          )
        )

        (fairysfGame, defaultMetaData)
      }

      def readSamuraiGame(r: BSON.Reader): (StratGame, Metadata) = {

        val gameVariant = SamuraiVariant(r intD F.variant) | SamuraiStandard

        val actionStrs = NewLibStorage.OldBin.decode(GameLogic.Samurai(), r bytesD F.oldPgn, playedPlies)

        def turnUcis(turnStr: Option[String]) =
          turnStr.map(_.split(",").toList.flatMap(samurai.format.Uci.apply)).getOrElse(List.empty)

        val samuraiGame = StratGame.Samurai(
          samurai.Game(
            situation = samurai.Situation(
              samurai.Board(
                pieces = BinaryFormat.piece.readSamurai(r bytes F.binaryPieces, gameVariant),
                history = samurai.History(
                  lastTurn = turnUcis(r strO F.historyLastTurn),
                  currentTurn = turnUcis(r strO F.historyCurrentTurn),
                  //we can flatten as samurai does not have any multiaction games
                  //TODO: Is halfMoveClock even doing anything for samurai?
                  halfMoveClock = actionStrs.flatten.reverse.indexWhere(san =>
                    san.contains("x") || san.headOption.exists(_.isLower)
                  ) atLeast 0,
                  positionHashes = r.getO[PositionHash](F.positionHashes) | Array.empty
                ),
                variant = gameVariant,
                uciMoves = actionStrs.flatten.toList
              ),
              player = turnPlayerIndex
            ),
            actionStrs = actionStrs,
            clock = clock,
            plies = plies,
            turnCount = turns,
            startedAtPly = startedAtPly,
            startedAtTurn = startedAtTurn
          )
        )

        (samuraiGame, defaultMetaData)
      }

      def readTogyzkumalakGame(r: BSON.Reader): (StratGame, Metadata) = {

        val gameVariant = TogyzkumalakVariant(r intD F.variant) | TogyzkumalakStandard

        val actionStrs = NewLibStorage.OldBin.decode(GameLogic.Togyzkumalak(), r bytesD F.oldPgn, playedPlies)

        def turnUcis(turnStr: Option[String]) =
          turnStr.map(_.split(",").toList.flatMap(togyzkumalak.format.Uci.apply)).getOrElse(List.empty)

        val togyzkumalakGame = StratGame.Togyzkumalak(
          togyzkumalak.Game(
            situation = togyzkumalak.Situation(
              togyzkumalak.Board(
                pieces = BinaryFormat.piece
                  .readTogyzkumalak(r bytes F.binaryPieces, gameVariant)
                  .filterNot { case (_, posInfo) => posInfo._2 == 0 },
                history = togyzkumalak.History(
                  lastTurn = turnUcis(r strO F.historyLastTurn),
                  currentTurn = turnUcis(r strO F.historyCurrentTurn),
                  //we can flatten as togyzkumalak does not have any multiaction games
                  //TODO: Is halfMoveClock even doing anything for togyzkumalak?
                  halfMoveClock = actionStrs.flatten.reverse.indexWhere(san =>
                    san.contains("x") || san.headOption.exists(_.isLower)
                  ) atLeast 0,
                  positionHashes = r.getO[PositionHash](F.positionHashes) | Array.empty,
                  score = {
                    val counts = r.intsD(F.score)
                    Score(~counts.headOption, ~counts.lastOption)
                  }
                ),
                variant = gameVariant
              ),
              player = turnPlayerIndex
            ),
            actionStrs = actionStrs,
            clock = clock,
            plies = plies,
            turnCount = turns,
            startedAtPly = startedAtPly,
            startedAtTurn = startedAtTurn
          )
        )

        (togyzkumalakGame, defaultMetaData)
      }

      def readGoGame(r: BSON.Reader): (StratGame, Metadata) = {

        val gameVariant = GoVariant(r intD F.variant) | GoStandard

        val actionStrs = NewLibStorage.OldBin.decode(GameLogic.Go(), r bytesD F.oldPgn, playedPlies)
        val uciMoves   = actionStrs.flatten.toList

        //This is different for Go because select squares uci uses commas
        //When we change ss to a proper multiaction sequence we will need to change this
        def turnUcis(turnStr: Option[String]) = turnStr.flatMap(go.format.Uci.apply).toList

        val initialFen: Option[FEN] = r.getO[FEN](F.initialFen) //for handicapped games

        val goGame = StratGame.Go(
          go.Game(
            situation = go.Situation(
              go.Board(
                pieces = BinaryFormat.piece.readGo(r bytes F.binaryPieces, gameVariant),
                history = go.History(
                  lastTurn = turnUcis(r strO F.historyLastTurn),
                  currentTurn = turnUcis(r strO F.historyCurrentTurn),
                  //we can flatten as go does not have any multiaction games
                  //TODO: Is halfMoveClock even doing anything for togyzkumalak?
                  halfMoveClock = actionStrs.flatten.reverse.indexWhere(san =>
                    san.contains("x") || san.headOption.exists(_.isLower)
                  ) atLeast 0,
                  positionHashes = r.getO[PositionHash](F.positionHashes) | Array.empty,
                  score = {
                    val counts = r.intsD(F.score)
                    Score(~counts.headOption, ~counts.lastOption)
                  },
                  captures = {
                    val counts = r.intsD(F.captures)
                    Score(~counts.headOption, ~counts.lastOption)
                  }
                ),
                variant = gameVariant,
                pocketData = gameVariant.dropsVariant option (r.get[PocketData](F.pocketData)) match {
                  case Some(PocketData.Go(pd)) => Some(pd)
                  case None                    => None
                  case _                       => sys.error("non go pocket data")
                },
                uciMoves = uciMoves,
                position =
                  initialFen.map(f => strategygames.go.Api.positionFromStartingFenAndMoves(f.toGo, uciMoves))
              ),
              player = turnPlayerIndex
            ),
            actionStrs = actionStrs,
            clock = clock,
            plies = plies,
            turnCount = turns,
            startedAtPly = startedAtPly,
            startedAtTurn = startedAtTurn
          )
        )
        val metadata = Metadata(
          source = r intO F.source flatMap Source.apply,
          pgnImport = r.getO[PgnImport](F.pgnImport)(PgnImport.pgnImportBSONHandler),
          tournamentId = r strO F.tournamentId,
          swissId = r strO F.swissId,
          simulId = r strO F.simulId,
          multiMatch = r strO F.multiMatch,
          analysed = r boolD F.analysed,
          drawOffers = r.getD(F.drawOffers, GameDrawOffers.empty),
          selectedSquares = (r bytesO F.selectedSquares).map(BinaryFormat.pos.readGo(_)),
          deadStoneOfferState = (r intO F.deadStoneOfferState) flatMap DeadStoneOfferState.apply
        )
        (goGame, metadata)

      }

      def readBackgammonGame(r: BSON.Reader): (StratGame, Metadata) = {

        val gameVariant = BackgammonVariant(r intD F.variant) | BackgammonStandard

        val actionStrs = NewLibStorage.OldBin.decode(GameLogic.Backgammon(), r bytesD F.oldPgn, playedPlies)

        def turnUcis(turnStr: Option[String]) =
          turnStr.map(_.split(",").toList.flatMap(backgammon.format.Uci.apply)).getOrElse(List.empty)

        val backgammonGame = StratGame.Backgammon(
          backgammon.Game(
            situation = backgammon.Situation(
              backgammon.Board(
                pieces = BinaryFormat.piece
                  .readBackgammon(r bytes F.binaryPieces, gameVariant)
                  .filterNot { case (_, posInfo) => posInfo._2 == 0 },
                history = backgammon.History(
                  lastTurn = turnUcis(r strO F.historyLastTurn),
                  currentTurn = turnUcis(r strO F.historyCurrentTurn),
                  //we can flatten as backgammon does not have any multiaction games
                  //TODO: Is halfMoveClock even doing anything for backgammon?
                  halfMoveClock = actionStrs.flatten.reverse.indexWhere(san =>
                    san.contains("x") || san.headOption.exists(_.isLower)
                  ) atLeast 0,
                  positionHashes = r.getO[PositionHash](F.positionHashes) | Array.empty,
                  score = {
                    val counts = r.intsD(F.score)
                    Score(~counts.headOption, ~counts.lastOption)
                  }
                ),
                variant = gameVariant,
                pocketData = gameVariant.dropsVariant option (r.get[PocketData](F.pocketData)) match {
                  case Some(PocketData.Backgammon(pd)) => Some(pd)
                  case None                            => None
                  case _                               => sys.error("non backgammon pocket data")
                },
                unusedDice = r.getO[List[Int]](F.unusedDice).getOrElse(List.empty)
              ),
              player = turnPlayerIndex
            ),
            actionStrs = actionStrs,
            clock = clock,
            plies = plies,
            turnCount = turns,
            startedAtPly = startedAtPly,
            startedAtTurn = startedAtTurn
          )
        )

        (backgammonGame, defaultMetaData)
      }

      val libId = r intD F.lib
      val (stratGame, metadata) = libId match {
        case 0 => readChessGame(r)
        case 1 => readDraughtsGame(r)
        case 2 => readFairySFGame(r)
        case 3 => readSamuraiGame(r)
        case 4 => readTogyzkumalakGame(r)
        case 5 => readGoGame(r)
        case 6 => readBackgammonGame(r)
        case _ => sys.error("Invalid game in the database")
      }

      Game(
        id = light.id,
        p1Player = light.p1Player,
        p2Player = light.p2Player,
        stratGame = stratGame,
        readClockHistory(r, light, turnPlayerIndex, periodEntries),
        status = light.status,
        daysPerTurn = r intO F.daysPerTurn,
        binaryPlyTimes = r bytesO F.plyTimes,
        mode = Mode(r boolD F.rated),
        bookmarks = r intD F.bookmarks,
        createdAt = createdAt,
        updatedAt = r.dateD(F.updatedAt, createdAt),
        metadata = metadata
      )
    }

    def writes(w: BSON.Writer, o: Game) =
      BSONDocument(
        F.id         -> o.id,
        F.playerIds  -> (o.p1Player.id + o.p2Player.id),
        F.playerUids -> w.strListO(List(~o.p1Player.userId, ~o.p2Player.userId)),
        F.p1Player -> w.docO(
          playerBSONHandler write ((_: PlayerIndex) =>
            (_: Player.ID) => (_: Player.UserId) => (_: Player.Win) => o.p1Player
          )
        ),
        F.p2Player -> w.docO(
          playerBSONHandler write ((_: PlayerIndex) =>
            (_: Player.ID) => (_: Player.UserId) => (_: Player.Win) => o.p2Player
          )
        ),
        F.status        -> o.status,
        F.turns         -> o.stratGame.turnCount,
        F.plies         -> w.intO(if (o.stratGame.plies == o.stratGame.turnCount) 0 else o.stratGame.plies),
        F.activePlayer  -> o.stratGame.situation.player.hashCode,
        F.startedAtPly  -> w.intO(o.stratGame.startedAtPly),
        F.startedAtTurn -> w.intO(o.stratGame.startedAtTurn),
        F.clockType     -> o.stratGame.clock.map(clockTypeBSONWrite),
        F.clock -> (o.stratGame.clock flatMap { c =>
          clockBSONWrite(o.createdAt, c).toOption
        }),
        F.daysPerTurn    -> o.daysPerTurn,
        F.plyTimes       -> o.binaryPlyTimes,
        F.p1ClockHistory -> clockHistory(P1, o.clockHistory, o.stratGame.clock, o.flagged),
        F.p2ClockHistory -> clockHistory(P2, o.clockHistory, o.stratGame.clock, o.flagged),
        F.rated          -> w.boolO(o.mode.rated),
        F.lib            -> o.board.variant.gameLogic.id,
        F.variant        -> o.board.variant.exotic.option(w int o.board.variant.id),
        F.bookmarks      -> w.intO(o.bookmarks),
        F.createdAt      -> w.date(o.createdAt),
        F.updatedAt      -> w.date(o.updatedAt),
        F.source         -> o.metadata.source.map(_.id),
        F.pgnImport      -> o.metadata.pgnImport,
        F.tournamentId   -> o.metadata.tournamentId,
        F.swissId        -> o.metadata.swissId,
        F.simulId        -> o.metadata.simulId,
        F.multiMatch     -> o.metadata.multiMatch,
        F.drawLimit      -> o.metadata.drawLimit,
        F.analysed       -> w.boolO(o.metadata.analysed)
      ) ++ {
        o.board.variant.gameLogic match {
          case GameLogic.Draughts() =>
            $doc(
              F.oldPgn -> NewLibStorage.OldBin
                .encodeActionStrs(o.variant.gameFamily, o.actionStrs take Game.maxTurns),
              F.binaryPieces -> BinaryFormat.piece.writeDraughts(o.board match {
                case Board.Draughts(board) => board
                case _                     => sys.error("invalid draughts board")
              }),
              F.positionHashes  -> o.history.positionHashes,
              F.historyLastTurn -> o.history.lastAction.map(_.uci),
              F.kingMoves       -> o.history.kingMoves.nonEmpty.option(o.history.kingMoves)
            )
          case GameLogic.FairySF() =>
            $doc(
              F.oldPgn -> NewLibStorage.OldBin
                .encodeActionStrs(o.variant.gameFamily, o.actionStrs take Game.maxTurns),
              F.binaryPieces -> BinaryFormat.piece.writeFairySF(o.board match {
                case Board.FairySF(board) => board.pieces
                case _                    => sys.error("invalid fairysf board")
              }),
              F.positionHashes     -> o.history.positionHashes,
              F.historyLastTurn    -> o.history.lastTurnUciString,
              F.historyCurrentTurn -> o.history.currentTurnUciString,
              F.pocketData         -> o.board.pocketData
            )
          case GameLogic.Samurai() =>
            $doc(
              F.oldPgn -> NewLibStorage.OldBin
                .encodeActionStrs(o.variant.gameFamily, o.actionStrs take Game.maxTurns),
              F.binaryPieces -> BinaryFormat.piece.writeSamurai(o.board match {
                case Board.Samurai(board) => board.pieces
                case _                    => sys.error("invalid samurai board")
              }),
              F.positionHashes     -> o.history.positionHashes,
              F.historyLastTurn    -> o.history.lastTurnUciString,
              F.historyCurrentTurn -> o.history.currentTurnUciString
            )
          case GameLogic.Togyzkumalak() =>
            $doc(
              F.oldPgn -> NewLibStorage.OldBin
                .encodeActionStrs(o.variant.gameFamily, o.actionStrs take Game.maxTurns),
              F.binaryPieces -> BinaryFormat.piece.writeTogyzkumalak(o.board match {
                case Board.Togyzkumalak(board) => board.pieces
                case _                         => sys.error("invalid togyzkumalak board")
              }),
              F.positionHashes     -> o.history.positionHashes,
              F.historyLastTurn    -> o.history.lastTurnUciString,
              F.historyCurrentTurn -> o.history.currentTurnUciString,
              F.score              -> o.history.score.nonEmpty.option(o.history.score)
            )
          case GameLogic.Go() =>
            $doc(
              F.oldPgn -> NewLibStorage.OldBin
                .encodeActionStrs(o.variant.gameFamily, o.actionStrs take Game.maxTurns),
              F.binaryPieces -> BinaryFormat.piece.writeGo(o.board match {
                case Board.Go(board) => board.pieces
                case _               => sys.error("invalid go board")
              }),
              F.positionHashes      -> o.history.positionHashes,
              F.historyLastTurn     -> o.history.lastTurnUciString,
              F.historyCurrentTurn  -> o.history.currentTurnUciString,
              F.score               -> o.history.score.nonEmpty.option(o.history.score),
              F.captures            -> o.history.captures.nonEmpty.option(o.history.captures),
              F.pocketData          -> o.board.pocketData,
              F.selectedSquares     -> o.metadata.selectedSquares.map(BinaryFormat.pos.writeGo),
              F.deadStoneOfferState -> o.metadata.deadStoneOfferState.map(_.id)
            )
          case GameLogic.Backgammon() =>
            $doc(
              F.oldPgn -> NewLibStorage.OldBin
                .encodeActionStrs(o.variant.gameFamily, o.actionStrs take Game.maxTurns),
              F.binaryPieces -> BinaryFormat.piece.writeBackgammon(o.board match {
                case Board.Backgammon(board) => board.pieces
                case _                       => sys.error("invalid backgammon board")
              }),
              F.positionHashes     -> o.history.positionHashes,
              F.historyLastTurn    -> o.history.lastTurnUciString,
              F.historyCurrentTurn -> o.history.currentTurnUciString,
              F.pocketData         -> o.board.pocketData,
              F.unusedDice         -> o.board.unusedDice.nonEmpty.option(o.board.unusedDice),
              F.score              -> o.history.score.nonEmpty.option(o.history.score)
            )
          case _ => //chess or fail
            if (o.variant.key == "standard")
              $doc(F.huffmanPgn -> PgnStorage.Huffman.encode(o.actionStrs.flatten take Game.maxPlies))
            else {
              $doc(
                F.oldPgn -> PgnStorage.OldBin.encodeActionStrs(o.actionStrs take Game.maxTurns),
                F.binaryPieces -> BinaryFormat.piece.writeChess(o.board match {
                  case Board.Chess(board) => board.pieces
                  case _                  => sys.error("invalid chess board")
                }),
                F.positionHashes -> o.history.positionHashes,
                F.unmovedRooks   -> o.history.unmovedRooks,
                //need to store this for multiaction variants. Going to be essentially
                //storing 'lastMove' twice, once in lastTurn and once in castleLastMove
                //but want to retain old functionality, and this is only for chess variants
                F.historyLastTurn    -> o.history.lastTurnUciString,
                F.historyCurrentTurn -> o.history.currentTurnUciString,
                F.castleLastMove -> CastleLastMove.castleLastMoveBSONHandler
                  .writeTry(
                    CastleLastMove(
                      castles = o.history.castles,
                      lastMove = o.history match {
                        case History.Chess(h) => h.lastAction
                        case _                => sys.error("Invalid history")
                      }
                    )
                  )
                  .toOption,
                F.checkCount -> o.history.checkCount.nonEmpty.option(o.history.checkCount),
                //F.score      -> o.history.score.nonEmpty.option(o.history.score),
                F.pocketData -> o.board.pocketData
              )
            }
        }
      } ++ {
        o.clockHistory.fold($doc())(ch =>
          ch match {
            case ch: ByoyomiClockHistory =>
              $doc(
                F.periodsP1 -> writePeriodEntriesForPlayer(P1, Some(ch)),
                F.periodsP2 -> writePeriodEntriesForPlayer(P2, Some(ch))
              )
            case _ => $doc()
          }
        )
      }
  }

  implicit object lightGameBSONHandler extends lila.db.BSONReadOnly[LightGame] {

    import Game.{ BSONFields => F }
    import Player.playerBSONHandler

    def reads(r: BSON.Reader): LightGame = {
      lila.mon.game.fetchLight.increment()
      readsWithPlayerIds(r, "")
    }

    def readsWithPlayerIds(r: BSON.Reader, playerIds: String): LightGame = {
      val (p1Id, p2Id)   = playerIds splitAt 4
      val winC           = r boolO F.winnerPlayerIndex map (PlayerIndex.fromP1)
      val uids           = ~r.getO[List[lila.user.User.ID]](F.playerUids)
      val (p1Uid, p2Uid) = (uids.headOption.filter(_.nonEmpty), uids.lift(1).filter(_.nonEmpty))
      def makePlayer(field: String, playerIndex: PlayerIndex, id: Player.ID, uid: Player.UserId): Player = {
        val builder = r.getO[Player.Builder](field)(playerBSONHandler) | emptyPlayerBuilder
        builder(playerIndex)(id)(uid)(winC map (_ == playerIndex))
      }
      LightGame(
        id = r str F.id,
        p1Player = makePlayer(F.p1Player, P1, p1Id, p1Uid),
        p2Player = makePlayer(F.p2Player, P2, p2Id, p2Uid),
        status = r.get[Status](F.status),
        lib = r intD F.lib,
        variant_id = r intD F.variant
      )
    }
  }

  //------------------------------------------------------------------------------
  // General API
  //------------------------------------------------------------------------------
  private[game] def clockTypeBSONWrite(clock: ClockBase) =
    // NOTE: If you're changing this, the read below also needs to be changed.
    clock.config match {
      case Clock.Config(_, _)              => 1
      case ByoyomiClock.Config(_, _, _, _) => 2
      case Clock.BronsteinConfig(_, _)     => 3
      case Clock.SimpleDelayConfig(_, _)   => 4
    }

  private[game] def clockBSONWrite(since: DateTime, clock: ClockBase) =
    clock match {
      case f: Clock        => otherClockBSONWrite(since, f)
      case b: ByoyomiClock => byoyomiClockBSONWrite(since, b)
    }

  private def clockHistory(
      playerIndex: PlayerIndex,
      clockHistory: Option[ClockHistory],
      clock: Option[ClockBase],
      flagged: Option[PlayerIndex]
  ) =
    for {
      clk     <- clock
      history <- clockHistory
      times = history.dbTimes(playerIndex)
    } yield clk.config match {
      case fc: Clock.Config =>
        BinaryFormat.fischerClockHistory.writeSide(fc.limit, times, flagged has playerIndex)
      case bdc: Clock.BronsteinConfig =>
        BinaryFormat.delayClockHistory.writeSide(bdc.limit, times, flagged has playerIndex)
      case sdc: Clock.SimpleDelayConfig =>
        BinaryFormat.delayClockHistory.writeSide(sdc.limit, times, flagged has playerIndex)
      case bc: ByoyomiClock.Config =>
        BinaryFormat.byoyomiClockHistory.writeSide(bc.limit, times, flagged has playerIndex)
    }

  private[game] def clockBSONReader(
      clockType: Option[Int],
      since: DateTime,
      periodEntries: Option[PeriodEntries],
      p1Berserk: Boolean,
      p2Berserk: Boolean
  ) =
    clockType match {
      case Some(2) =>
        byoyomiClockBSONReader(since, periodEntries.getOrElse(PeriodEntries.default), p1Berserk, p2Berserk)
      case Some(3) => otherClockBSONReader(Clock.BronsteinConfig, since, p1Berserk, p2Berserk)
      case Some(4) => otherClockBSONReader(Clock.SimpleDelayConfig, since, p1Berserk, p2Berserk)
      case _       => otherClockBSONReader(Clock.Config, since, p1Berserk, p2Berserk)
    }

  def readClockHistory(
      r: BSON.Reader,
      light: LightGame,
      turnPlayerIndex: PlayerIndex,
      periodEntries: Option[PeriodEntries]
  ) = {
    import Game.{ BSONFields => F }
    val p1ClockHistory = r bytesO F.p1ClockHistory
    val p2ClockHistory = r bytesO F.p2ClockHistory
    (clk: ClockBase) =>
      for {
        bw <- p1ClockHistory
        bb <- p2ClockHistory
        history <-
          clk.config match {
            case fc: Clock.Config =>
              BinaryFormat.fischerClockHistory
                .read(fc.limit, bw, bb, (light.status == Status.Outoftime).option(turnPlayerIndex))
            case bdc: Clock.BronsteinConfig =>
              BinaryFormat.delayClockHistory
                .read(bdc.limit, bw, bb, (light.status == Status.Outoftime).option(turnPlayerIndex))
            case sdc: Clock.SimpleDelayConfig =>
              BinaryFormat.delayClockHistory
                .read(sdc.limit, bw, bb, (light.status == Status.Outoftime).option(turnPlayerIndex))
            case bc: ByoyomiClock.Config =>
              BinaryFormat.byoyomiClockHistory
                .read(
                  bc.byoyomi,
                  bc.limit,
                  bw,
                  bb,
                  periodEntries.getOrElse(PeriodEntries.default),
                  (light.status == Status.Outoftime).option(turnPlayerIndex)
                )
          }
        _ = lila.mon.game.loadClockHistory.increment()
      } yield history
    // TODO: does draughts really need this version?
    // (light.status == Status.Outoftime).option(decodedSituation.player)
    //                                           ^^^^^^^^^^^^^^^^^^^^^^^
    //                                           rather than turnPlayerIndex?
  }

  //------------------------------------------------------------------------------
  // FischerClock stuff
  //------------------------------------------------------------------------------
  private[game] def otherClockBSONReader(
      configConstructor: (Int, Int) => ClockConfig,
      since: DateTime,
      p1Berserk: Boolean,
      p2Berserk: Boolean
  ) =
    new BSONReader[PlayerIndex => ClockBase] {
      def readTry(bson: BSONValue): Try[PlayerIndex => Clock] =
        bson match {
          case bin: BSONBinary =>
            ByteArrayBSONHandler.readTry(bin).map { cl =>
              BinaryFormat
                .fischerClock(since)
                .read(configConstructor, cl, p1Berserk, p2Berserk)
            }
          case b => lila.db.BSON.handlerBadType(b)
        }
    }

  private[game] def otherClockBSONWrite(since: DateTime, clock: Clock) =
    ByteArrayBSONHandler writeTry {
      BinaryFormat.fischerClock(since).write(clock)
    }

  //------------------------------------------------------------------------------
  // ByoyomiClock  stuff
  //------------------------------------------------------------------------------
  def readPeriodEntries(r: BSON.Reader) = {
    import Game.{ BSONFields => F }
    BinaryFormat.periodEntries
      .read(
        r bytesD F.periodsP1,
        r bytesD F.periodsP2
      )
  }

  private def writePeriodEntriesForPlayer(
      playerIndex: PlayerIndex,
      clockHistory: Option[ByoyomiClockHistory]
  ) =
    for {
      history <- clockHistory
    } yield BinaryFormat.periodEntries.writeSide(history.periodEntries(playerIndex))

  private[game] def byoyomiClockBSONReader(
      since: DateTime,
      periodEntries: PeriodEntries,
      p1Berserk: Boolean,
      p2Berserk: Boolean
  ) =
    new BSONReader[PlayerIndex => ClockBase] {
      def readTry(bson: BSONValue): Try[PlayerIndex => ByoyomiClock] =
        bson match {
          case bin: BSONBinary =>
            ByteArrayBSONHandler readTry bin map { cl =>
              BinaryFormat.byoyomiClock(since).read(cl, periodEntries, p1Berserk, p2Berserk)
            }
          case b => lila.db.BSON.handlerBadType(b)
        }
    }

  private[game] def byoyomiClockBSONWrite(since: DateTime, clock: ByoyomiClock) =
    ByteArrayBSONHandler writeTry {
      BinaryFormat.byoyomiClock(since).write(clock)
    }
}
