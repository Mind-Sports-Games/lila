package lila.game

import strategygames.{
  Player => PlayerIndex,
  Clock,
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
  Situation,
  Board,
  Role
}
import strategygames.chess
import strategygames.draughts
import strategygames.fairysf
import strategygames.samurai
import strategygames.togyzkumalak
import strategygames.format.Uci
import strategygames.variant.Variant
import strategygames.chess.variant.{ Variant => ChessVariant, Standard => ChessStandard }
import strategygames.draughts.variant.{ Variant => DraughtsVariant, Standard => DraughtsStandard }
import strategygames.fairysf.variant.{ Variant => FairySFVariant, Shogi => FairySFStandard }
import strategygames.samurai.variant.{ Variant => SamuraiVariant, Oware => SamuraiStandard }
import strategygames.togyzkumalak.variant.{
  Variant => TogyzkumalakVariant,
  Togyzkumalak => TogyzkumalakStandard
}
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

  implicit private[game] val scoreWriter = new BSONWriter[togyzkumalak.Score] {
    def writeTry(sc: togyzkumalak.Score) = Success(BSONArray(sc.p1, sc.p2))
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
        case _ => sys.error(s"Pocket Data BSON reader not implemented for GameLogic: ${r.intD("l")}")
      }

    def writes(w: BSON.Writer, o: PocketData) =
      BSONDocument(
        "l" -> (o match {
          case PocketData.Chess(_)   => 0
          case PocketData.FairySF(_) => 2
          case _                     => sys.error("Pocket Data BSON Handler not implemented for GameLogic")
        }),
        "f" -> (o match {
          case PocketData.Chess(_)    => 0
          case PocketData.FairySF(pd) => pd.gameFamily.getOrElse(GameFamily.Shogi()).id
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

    def readChessGame(r: BSON.Reader): Game = {
      val light           = lightGameBSONHandler.readsWithPlayerIds(r, r str F.playerIds)
      val startedAtTurn   = r intD F.startedAtTurn
      val plies           = r int F.turns atMost Game.maxPlies // unlimited can cause StackOverflowError
      val turnPlayerIndex = PlayerIndex.fromPly(plies)
      val createdAt       = r date F.createdAt

      val playedPlies = plies - startedAtTurn
      val gameVariant = ChessVariant(r intD F.variant) | ChessStandard

      val decoded = r.bytesO(F.huffmanPgn).map { PgnStorage.Huffman.decode(_, playedPlies) } | {
        val clm      = r.get[CastleLastMove](F.castleLastMove)
        val pgnMoves = PgnStorage.OldBin.decode(r bytesD F.oldPgn, playedPlies)
        PgnStorage.Decoded(
          pgnMoves = pgnMoves,
          pieces = BinaryFormat.piece.readChess(r bytes F.binaryPieces, gameVariant),
          positionHashes = r.getO[PositionHash](F.positionHashes) | Array.empty,
          unmovedRooks = r.getO[chess.UnmovedRooks](F.unmovedRooks) | chess.UnmovedRooks.default,
          lastMove = clm.lastMove,
          castles = clm.castles,
          halfMoveClock = pgnMoves.reverse.indexWhere(san =>
            san.contains("x") || san.headOption.exists(_.isLower)
          ) atLeast 0
        )
      }

      val chessGame = chess.Game(
        situation = chess.Situation(
          chess.Board(
            pieces = decoded.pieces,
            history = chess.History(
              lastMove = decoded.lastMove,
              castles = decoded.castles,
              halfMoveClock = decoded.halfMoveClock,
              positionHashes = decoded.positionHashes,
              unmovedRooks = decoded.unmovedRooks,
              checkCount = if (gameVariant.threeCheck || gameVariant.fiveCheck) {
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
        pgnMoves = decoded.pgnMoves,
        clock = r.getO[PlayerIndex => Clock](F.clock) {
          clockBSONReader(createdAt, light.p1Player.berserk, light.p2Player.berserk)
        } map (_(turnPlayerIndex)),
        turns = plies,
        startedAtTurn = startedAtTurn
      )

      val p1ClockHistory = r bytesO F.p1ClockHistory
      val p2ClockHistory = r bytesO F.p2ClockHistory

      Game(
        id = light.id,
        p1Player = light.p1Player,
        p2Player = light.p2Player,
        chess = StratGame.Chess(chessGame),
        loadClockHistory = clk =>
          for {
            bw <- p1ClockHistory
            bb <- p2ClockHistory
            history <-
              BinaryFormat.clockHistory
                .read(clk.limit, bw, bb, (light.status == Status.Outoftime).option(turnPlayerIndex))
            _ = lila.mon.game.loadClockHistory.increment()
          } yield history,
        status = light.status,
        daysPerTurn = r intO F.daysPerTurn,
        binaryMoveTimes = r bytesO F.moveTimes,
        mode = Mode(r boolD F.rated),
        bookmarks = r intD F.bookmarks,
        createdAt = createdAt,
        movedAt = r.dateD(F.movedAt, createdAt),
        metadata = Metadata(
          source = r intO F.source flatMap Source.apply,
          pgnImport = r.getO[PgnImport](F.pgnImport)(PgnImport.pgnImportBSONHandler),
          tournamentId = r strO F.tournamentId,
          swissId = r strO F.swissId,
          simulId = r strO F.simulId,
          multiMatch = r strO F.multiMatch,
          analysed = r boolD F.analysed,
          drawOffers = r.getD(F.drawOffers, GameDrawOffers.empty)
        )
      )
    }

    def readDraughtsGame(r: BSON.Reader): Game = {

      //lila.mon.game.fetch()

      val light         = lightGameBSONHandler.readsWithPlayerIds(r, r str F.playerIds)
      val gameVariant   = DraughtsVariant(r intD F.variant) | DraughtsStandard
      val startedAtTurn = r intD F.startedAtTurn
      val plies         = r int F.turns atMost Game.maxPlies // unlimited can cause StackOverflowError
      val playedPlies   = plies - startedAtTurn

      val decoded = r.bytesO(F.huffmanPgn).map { PdnStorage.Huffman.decode(_, playedPlies) } | {
        PdnStorage.Decoded(
          pdnMoves = PdnStorage.OldBin.decode(r bytesD F.oldPgn, playedPlies),
          pieces = BinaryFormat.piece.readDraughts(r bytes F.binaryPieces, gameVariant),
          positionHashes = r.getO[PositionHash](F.positionHashes) | Array.empty,
          lastMove = r strO F.historyLastMove flatMap (draughts.format.Uci.apply),
          //lastMove = r strO F.historyLastMove flatMap(uci => Uci.wrap(draughts.format.Uci(uci))),
          format = PdnStorage.OldBin
        )
      }

      val decodedBoard = draughts.Board(
        pieces = decoded.pieces,
        history = draughts.DraughtsHistory(
          lastMove = decoded.lastMove,
          positionHashes = decoded.positionHashes,
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

      val midCapture =
        decoded.pdnMoves.lastOption.fold(false)(_.indexOf('x') != -1) && decodedBoard.ghosts != 0
      val currentPly      = if (midCapture) plies - 1 else plies
      val turnPlayerIndex = PlayerIndex.fromPly(currentPly)

      val decodedSituation = draughts.Situation(
        board = decodedBoard,
        player = turnPlayerIndex
      )

      val createdAt = r date F.createdAt

      val draughtsGame = draughts.DraughtsGame(
        situation = decodedSituation,
        pdnMoves = decoded.pdnMoves,
        clock = r.getO[PlayerIndex => Clock](F.clock) {
          clockBSONReader(createdAt, light.p1Player.berserk, light.p2Player.berserk)
        } map (_(decodedSituation.player)),
        turns = currentPly,
        startedAtTurn = startedAtTurn
      )

      val p1ClockHistory = r bytesO F.p1ClockHistory
      val p2ClockHistory = r bytesO F.p2ClockHistory

      Game(
        id = light.id,
        p1Player = light.p1Player,
        p2Player = light.p2Player,
        chess = StratGame.Draughts(draughtsGame),
        loadClockHistory = clk =>
          for {
            bw <- p1ClockHistory
            bb <- p2ClockHistory
            history <- BinaryFormat.clockHistory
              .read(clk.limit, bw, bb, (light.status == Status.Outoftime).option(decodedSituation.player))
            _ = lila.mon.game.loadClockHistory.increment()
          } yield history,
        pdnStorage = Some(decoded.format),
        status = light.status,
        daysPerTurn = r intO F.daysPerTurn,
        binaryMoveTimes = r bytesO F.moveTimes,
        mode = Mode(r boolD F.rated),
        bookmarks = r intD F.bookmarks,
        createdAt = createdAt,
        movedAt = r.dateD(F.movedAt, createdAt),
        metadata = Metadata(
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
      )
    }

    def readFairySFGame(r: BSON.Reader): Game = {
      val light           = lightGameBSONHandler.readsWithPlayerIds(r, r str F.playerIds)
      val startedAtTurn   = r intD F.startedAtTurn
      val gameVariant     = FairySFVariant(r intD F.variant) | FairySFStandard
      val plies           = r int F.turns atMost Game.maxPlies // unlimited can cause StackOverflowError
      val turnPlayerIndex = PlayerIndex.fromPly(plies, gameVariant.plysPerTurn)
      val createdAt       = r date F.createdAt

      val playedPlies = plies - startedAtTurn

      val decoded = r.bytesO(F.huffmanPgn).map { PfnStorage.Huffman.decode(_, playedPlies) } | {
        //val clm      = r.get[CastleLastMove](F.castleLastMove)
        val pgnMoves = PfnStorage.OldBin.decode(r bytesD F.oldPgn, playedPlies)
        PfnStorage.Decoded(
          pgnMoves = pgnMoves,
          pieces = BinaryFormat.piece.readFairySF(r bytes F.binaryPieces, gameVariant),
          positionHashes = r.getO[PositionHash](F.positionHashes) | Array.empty,
          //unmovedRooks = chess.UnmovedRooks.default,
          lastMove =
            (r strO F.historyLastMove) flatMap (uci => fairysf.format.Uci.apply(gameVariant.gameFamily, uci)),
          //castles = Castles.none,
          halfMoveClock = pgnMoves.reverse.indexWhere(san =>
            san.contains("x") || san.headOption.exists(_.isLower)
          ) atLeast 0
        )
      }

      val fairysfGame = fairysf.Game(
        situation = fairysf.Situation(
          fairysf.Board(
            pieces = decoded.pieces,
            history = fairysf.History(
              lastMove = decoded.lastMove,
              //castles = decoded.castles,
              halfMoveClock = decoded.halfMoveClock,
              positionHashes = decoded.positionHashes //,
              //unmovedRooks = decoded.unmovedRooks,
              //checkCount = if (gameVariant.threeCheck) {
              //  val counts = r.intsD(F.checkCount)
              //  chess.CheckCount(~counts.headOption, ~counts.lastOption)
              //} else Game.emptyCheckCount
            ),
            variant = gameVariant,
            pocketData = gameVariant.dropsVariant option (r.get[PocketData](F.pocketData)) match {
              case Some(PocketData.FairySF(pd)) => Some(pd)
              case None                         => None
              case _                            => sys.error("non fairysf pocket data")
            },
            uciMoves = strategygames.fairysf.format.pgn.Parser.pgnMovesToUciMoves(decoded.pgnMoves)
          ),
          player = turnPlayerIndex
        ),
        pgnMoves = decoded.pgnMoves,
        clock = r.getO[PlayerIndex => Clock](F.clock) {
          clockBSONReader(createdAt, light.p1Player.berserk, light.p2Player.berserk)
        } map (_(turnPlayerIndex)),
        turns = plies,
        startedAtTurn = startedAtTurn
      )

      val p1ClockHistory = r bytesO F.p1ClockHistory
      val p2ClockHistory = r bytesO F.p2ClockHistory

      Game(
        id = light.id,
        p1Player = light.p1Player,
        p2Player = light.p2Player,
        chess = StratGame.FairySF(fairysfGame),
        loadClockHistory = clk =>
          for {
            bw <- p1ClockHistory
            bb <- p2ClockHistory
            history <-
              BinaryFormat.clockHistory
                .read(clk.limit, bw, bb, (light.status == Status.Outoftime).option(turnPlayerIndex))
            _ = lila.mon.game.loadClockHistory.increment()
          } yield history,
        status = light.status,
        daysPerTurn = r intO F.daysPerTurn,
        binaryMoveTimes = r bytesO F.moveTimes,
        mode = Mode(r boolD F.rated),
        bookmarks = r intD F.bookmarks,
        createdAt = createdAt,
        movedAt = r.dateD(F.movedAt, createdAt),
        metadata = Metadata(
          source = r intO F.source flatMap Source.apply,
          pgnImport = r.getO[PgnImport](F.pgnImport)(PgnImport.pgnImportBSONHandler),
          tournamentId = r strO F.tournamentId,
          swissId = r strO F.swissId,
          simulId = r strO F.simulId,
          multiMatch = r strO F.multiMatch,
          analysed = r boolD F.analysed,
          drawOffers = r.getD(F.drawOffers, GameDrawOffers.empty)
        )
      )
    }

    def readSamuraiGame(r: BSON.Reader): Game = {
      val light           = lightGameBSONHandler.readsWithPlayerIds(r, r str F.playerIds)
      val startedAtTurn   = r intD F.startedAtTurn
      val plies           = r int F.turns atMost Game.maxPlies // unlimited can cause StackOverflowError
      val turnPlayerIndex = PlayerIndex.fromPly(plies)
      val createdAt       = r date F.createdAt

      val playedPlies = plies - startedAtTurn
      val gameVariant = SamuraiVariant(r intD F.variant) | SamuraiStandard

      val decoded = r.bytesO(F.huffmanPgn).map { PmnStorage.Huffman.decode(_, playedPlies) } | {
        val pgnMoves = PmnStorage.OldBin.decode(r bytesD F.oldPgn, playedPlies)
        PmnStorage.Decoded(
          pgnMoves = pgnMoves,
          pieces = BinaryFormat.piece.readSamurai(r bytes F.binaryPieces, gameVariant),
          positionHashes = r.getO[PositionHash](F.positionHashes) | Array.empty,
          lastMove = (r strO F.historyLastMove) flatMap (samurai.format.Uci.apply),
          halfMoveClock = pgnMoves.reverse.indexWhere(san =>
            san.contains("x") || san.headOption.exists(_.isLower)
          ) atLeast 0
        )
      }

      val samuraiGame = samurai.Game(
        situation = samurai.Situation(
          samurai.Board(
            pieces = decoded.pieces,
            history = samurai.History(
              lastMove = decoded.lastMove,
              halfMoveClock = decoded.halfMoveClock,
              positionHashes = decoded.positionHashes
            ),
            variant = gameVariant,
            uciMoves = strategygames.samurai.format.pgn.Parser.pgnMovesToUciMoves(decoded.pgnMoves)
          ),
          player = turnPlayerIndex
        ),
        pgnMoves = decoded.pgnMoves,
        clock = r.getO[PlayerIndex => Clock](F.clock) {
          clockBSONReader(createdAt, light.p1Player.berserk, light.p2Player.berserk)
        } map (_(turnPlayerIndex)),
        turns = plies,
        startedAtTurn = startedAtTurn
      )

      val p1ClockHistory = r bytesO F.p1ClockHistory
      val p2ClockHistory = r bytesO F.p2ClockHistory

      Game(
        id = light.id,
        p1Player = light.p1Player,
        p2Player = light.p2Player,
        chess = StratGame.Samurai(samuraiGame),
        loadClockHistory = clk =>
          for {
            bw <- p1ClockHistory
            bb <- p2ClockHistory
            history <-
              BinaryFormat.clockHistory
                .read(clk.limit, bw, bb, (light.status == Status.Outoftime).option(turnPlayerIndex))
            _ = lila.mon.game.loadClockHistory.increment()
          } yield history,
        status = light.status,
        daysPerTurn = r intO F.daysPerTurn,
        binaryMoveTimes = r bytesO F.moveTimes,
        mode = Mode(r boolD F.rated),
        bookmarks = r intD F.bookmarks,
        createdAt = createdAt,
        movedAt = r.dateD(F.movedAt, createdAt),
        metadata = Metadata(
          source = r intO F.source flatMap Source.apply,
          pgnImport = r.getO[PgnImport](F.pgnImport)(PgnImport.pgnImportBSONHandler),
          tournamentId = r strO F.tournamentId,
          swissId = r strO F.swissId,
          simulId = r strO F.simulId,
          multiMatch = r strO F.multiMatch,
          analysed = r boolD F.analysed,
          drawOffers = r.getD(F.drawOffers, GameDrawOffers.empty)
        )
      )
    }

    def readTogyzkumalakGame(r: BSON.Reader): Game = {
      val light           = lightGameBSONHandler.readsWithPlayerIds(r, r str F.playerIds)
      val startedAtTurn   = r intD F.startedAtTurn
      val plies           = r int F.turns atMost Game.maxPlies // unlimited can cause StackOverflowError
      val turnPlayerIndex = PlayerIndex.fromPly(plies)
      val createdAt       = r date F.createdAt

      val playedPlies = plies - startedAtTurn
      val gameVariant = TogyzkumalakVariant(r intD F.variant) | TogyzkumalakStandard

      val decoded = r.bytesO(F.huffmanPgn).map { PtnStorage.Huffman.decode(_, playedPlies) } | {
        val pgnMoves = PtnStorage.OldBin.decode(r bytesD F.oldPgn, playedPlies)
        PtnStorage.Decoded(
          pgnMoves = pgnMoves,
          pieces = BinaryFormat.piece.readTogyzkumalak(r bytes F.binaryPieces, gameVariant),
          positionHashes = r.getO[PositionHash](F.positionHashes) | Array.empty,
          lastMove = (r strO F.historyLastMove) flatMap (togyzkumalak.format.Uci.apply),
          halfMoveClock = pgnMoves.reverse.indexWhere(san =>
            san.contains("x") || san.headOption.exists(_.isLower)
          ) atLeast 0
        )
      }

      val togyzkumalakGame = togyzkumalak.Game(
        situation = togyzkumalak.Situation(
          togyzkumalak.Board(
            pieces = decoded.pieces,
            history = togyzkumalak.History(
              lastMove = decoded.lastMove,
              halfMoveClock = decoded.halfMoveClock,
              positionHashes = decoded.positionHashes,
              score = {
                val counts = r.intsD(F.score)
                togyzkumalak.Score(~counts.headOption, ~counts.lastOption)
              }
            ),
            variant = gameVariant
          ),
          player = turnPlayerIndex
        ),
        pgnMoves = decoded.pgnMoves,
        clock = r.getO[PlayerIndex => Clock](F.clock) {
          clockBSONReader(createdAt, light.p1Player.berserk, light.p2Player.berserk)
        } map (_(turnPlayerIndex)),
        turns = plies,
        startedAtTurn = startedAtTurn
      )

      val p1ClockHistory = r bytesO F.p1ClockHistory
      val p2ClockHistory = r bytesO F.p2ClockHistory

      Game(
        id = light.id,
        p1Player = light.p1Player,
        p2Player = light.p2Player,
        chess = StratGame.Togyzkumalak(togyzkumalakGame),
        loadClockHistory = clk =>
          for {
            bw <- p1ClockHistory
            bb <- p2ClockHistory
            history <-
              BinaryFormat.clockHistory
                .read(clk.limit, bw, bb, (light.status == Status.Outoftime).option(turnPlayerIndex))
            _ = lila.mon.game.loadClockHistory.increment()
          } yield history,
        status = light.status,
        daysPerTurn = r intO F.daysPerTurn,
        binaryMoveTimes = r bytesO F.moveTimes,
        mode = Mode(r boolD F.rated),
        bookmarks = r intD F.bookmarks,
        createdAt = createdAt,
        movedAt = r.dateD(F.movedAt, createdAt),
        metadata = Metadata(
          source = r intO F.source flatMap Source.apply,
          pgnImport = r.getO[PgnImport](F.pgnImport)(PgnImport.pgnImportBSONHandler),
          tournamentId = r strO F.tournamentId,
          swissId = r strO F.swissId,
          simulId = r strO F.simulId,
          multiMatch = r strO F.multiMatch,
          analysed = r boolD F.analysed,
          drawOffers = r.getD(F.drawOffers, GameDrawOffers.empty)
        )
      )
    }

    def reads(r: BSON.Reader): Game = {

      lila.mon.game.fetch.increment()

      val libId = r intD F.lib
      libId match {
        case 0 => readChessGame(r)
        case 1 => readDraughtsGame(r)
        case 2 => readFairySFGame(r)
        case 3 => readSamuraiGame(r)
        case 4 => readTogyzkumalakGame(r)
        case _ => sys.error("Invalid game in the database")
      }
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
        F.turns         -> o.chess.turns,
        F.startedAtTurn -> w.intO(o.chess.startedAtTurn),
        F.clock -> (o.chess.clock flatMap { c =>
          clockBSONWrite(o.createdAt, c).toOption
        }),
        F.daysPerTurn    -> o.daysPerTurn,
        F.moveTimes      -> o.binaryMoveTimes,
        F.p1ClockHistory -> clockHistory(P1, o.clockHistory, o.chess.clock, o.flagged),
        F.p2ClockHistory -> clockHistory(P2, o.clockHistory, o.chess.clock, o.flagged),
        F.rated          -> w.boolO(o.mode.rated),
        F.lib            -> o.board.variant.gameLogic.id,
        F.variant        -> o.board.variant.exotic.option(w int o.board.variant.id),
        F.bookmarks      -> w.intO(o.bookmarks),
        F.createdAt      -> w.date(o.createdAt),
        F.movedAt        -> w.date(o.movedAt),
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
            o.pdnStorage match {
              case Some(PdnStorage.OldBin) =>
                $doc(
                  F.oldPgn -> PdnStorage.OldBin.encode(o.pgnMoves take Game.maxPlies),
                  F.binaryPieces -> BinaryFormat.piece.writeDraughts(o.board match {
                    case Board.Draughts(board) => board
                    case _                     => sys.error("invalid draughts board")
                  }),
                  F.positionHashes  -> o.history.positionHashes,
                  F.historyLastMove -> o.history.lastMove.map(_.uci),
                  // since variants are always OldBin
                  F.kingMoves -> o.history.kingMoves.nonEmpty.option(o.history.kingMoves)
                )
              case Some(PdnStorage.Huffman) =>
                $doc(
                  F.huffmanPgn -> PdnStorage.Huffman.encode(o.pgnMoves take Game.maxPlies)
                )
              case _ => sys.error("invalid draughts storage")
            }
          case GameLogic.FairySF() =>
            $doc(
              F.oldPgn -> PfnStorage.OldBin
                .encode(o.variant.gameFamily, o.pgnMoves take Game.maxPlies),
              F.binaryPieces -> BinaryFormat.piece.writeFairySF(o.board match {
                case Board.FairySF(board) => board.pieces
                case _                    => sys.error("invalid fairysf board")
              }),
              F.positionHashes  -> o.history.positionHashes,
              F.historyLastMove -> o.history.lastMove.map(_.uci),
              F.pocketData      -> o.board.pocketData
            )
          case GameLogic.Samurai() =>
            $doc(
              F.oldPgn -> PmnStorage.OldBin.encode(o.variant.gameFamily, o.pgnMoves take Game.maxPlies),
              F.binaryPieces -> BinaryFormat.piece.writeSamurai(o.board match {
                case Board.Samurai(board) => board.pieces
                case _                    => sys.error("invalid samurai board")
              }),
              F.positionHashes  -> o.history.positionHashes,
              F.historyLastMove -> o.history.lastMove.map(_.uci)
            )
          case GameLogic.Togyzkumalak() =>
            $doc(
              F.oldPgn -> PtnStorage.OldBin.encode(o.variant.gameFamily, o.pgnMoves take Game.maxPlies),
              F.binaryPieces -> BinaryFormat.piece.writeTogyzkumalak(o.board match {
                case Board.Togyzkumalak(board) => board.pieces
                case _                         => sys.error("invalid togyzkumalak board")
              }),
              F.positionHashes  -> o.history.positionHashes,
              F.historyLastMove -> o.history.lastMove.map(_.uci),
              F.score           -> o.history.score.nonEmpty.option(o.history.score)
            )
          case _ => //chess or fail
            if (o.variant.standard)
              $doc(F.huffmanPgn -> PgnStorage.Huffman.encode(o.pgnMoves take Game.maxPlies))
            else {
              $doc(
                F.oldPgn -> PgnStorage.OldBin.encode(o.pgnMoves take Game.maxPlies),
                F.binaryPieces -> BinaryFormat.piece.writeChess(o.board match {
                  case Board.Chess(board) => board.pieces
                  case _                  => sys.error("invalid chess board")
                }),
                F.positionHashes -> o.history.positionHashes,
                F.unmovedRooks   -> o.history.unmovedRooks,
                F.castleLastMove -> CastleLastMove.castleLastMoveBSONHandler
                  .writeTry(
                    CastleLastMove(
                      castles = o.history.castles,
                      lastMove = o.history match {
                        case History.Chess(h) => h.lastMove
                        case _                => sys.error("Invalid history")
                      }
                    )
                  )
                  .toOption,
                F.checkCount -> o.history.checkCount.nonEmpty.option(o.history.checkCount),
                F.score      -> o.history.score.nonEmpty.option(o.history.score),
                F.pocketData -> o.board.pocketData
              )
            }
        }
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

  private def clockHistory(
      playerIndex: PlayerIndex,
      clockHistory: Option[ClockHistory],
      clock: Option[Clock],
      flagged: Option[PlayerIndex]
  ) =
    for {
      clk     <- clock
      history <- clockHistory
      times = history(playerIndex)
    } yield BinaryFormat.clockHistory.writeSide(clk.limit, times, flagged has playerIndex)

  private[game] def clockBSONReader(since: DateTime, p1Berserk: Boolean, p2Berserk: Boolean) =
    new BSONReader[PlayerIndex => Clock] {
      def readTry(bson: BSONValue): Try[PlayerIndex => Clock] =
        bson match {
          case bin: BSONBinary =>
            ByteArrayBSONHandler readTry bin map { cl =>
              BinaryFormat.clock(since).read(cl, p1Berserk, p2Berserk)
            }
          case b => lila.db.BSON.handlerBadType(b)
        }
    }

  private[game] def clockBSONWrite(since: DateTime, clock: Clock) =
    ByteArrayBSONHandler writeTry {
      BinaryFormat clock since write clock
    }
}
