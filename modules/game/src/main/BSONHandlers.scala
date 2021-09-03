package lila.game

import strategygames.{ Color, Clock, White, Black, Game => StratGame, GameLib, History, Status, Mode, Piece, Pos, PositionHash, Situation, Board }
import strategygames.chess
import strategygames.draughts
import strategygames.format.Uci
import strategygames.variant.Variant
import strategygames.chess.variant.{ Variant => ChessVariant, Standard => ChessStandard, Crazyhouse }
import strategygames.draughts.variant.{ Variant => DraughtsVariant, Standard => DraughtsStandard }
import org.joda.time.DateTime
import reactivemongo.api.bson._
import scala.util.{ Success, Try }

import lila.db.BSON
import lila.db.dsl._

object BSONHandlers {

  import lila.db.ByteArray.ByteArrayBSONHandler

  implicit private[game] val checkCountWriter = new BSONWriter[chess.CheckCount] {
    def writeTry(cc: chess.CheckCount) = Success(BSONArray(cc.white, cc.black))
  }

  implicit val StatusBSONHandler = tryHandler[Status](
    { case BSONInteger(v) => Status(v) toTry s"No such status: $v" },
    x => BSONInteger(x.id)
  )

  implicit private[game] val unmovedRooksHandler = tryHandler[chess.UnmovedRooks](
    { case bin: BSONBinary => ByteArrayBSONHandler.readTry(bin) map BinaryFormat.unmovedRooks.read },
    x => ByteArrayBSONHandler.writeTry(BinaryFormat.unmovedRooks write x).get
  )

  implicit private[game] val crazyhouseDataBSONHandler = new BSON[Crazyhouse.Data] {

    import Crazyhouse._

    def reads(r: BSON.Reader) =
      Crazyhouse.Data(
        pockets = {
          val (white, black) = {
            r.str("p").view.flatMap(c => chess.Piece.fromChar(c)).to(List)
          }.partition(_ is chess.White)
          Pockets(
            white = Pocket(white.map(_.role)),
            black = Pocket(black.map(_.role))
          )
        },
        promoted = r.str("t").view.flatMap(chess.Pos.piotr).to(Set)
      )

    def writes(w: BSON.Writer, o: Crazyhouse.Data) =
      BSONDocument(
        "p" -> {
          o.pockets.white.roles.map(_.forsythUpper).mkString +
            o.pockets.black.roles.map(_.forsyth).mkString
        },
        "t" -> o.promoted.map(_.piotr).mkString
      )
  }

  implicit private[game] val gameDrawOffersHandler = tryHandler[GameDrawOffers](
    { case arr: BSONArray =>
      Success(arr.values.foldLeft(GameDrawOffers.empty) {
        case (offers, BSONInteger(p)) =>
          if (p > 0) offers.copy(white = offers.white incl p)
          else offers.copy(black = offers.black incl -p)
        case (offers, _) => offers
      })
    },
    offers => BSONArray((offers.white ++ offers.black.map(-_)).view.map(BSONInteger.apply).toIndexedSeq)
  )

  import Player.playerBSONHandler
  private val emptyPlayerBuilder = playerBSONHandler.read($empty)

  private[game] implicit val kingMovesWriter = new BSONWriter[draughts.KingMoves] {
    def writeTry(km: draughts.KingMoves) = Try{BSONArray(km.white, km.black, km.whiteKing.fold(0)(_.fieldNumber), km.blackKing.fold(0)(_.fieldNumber))}
  }

  implicit val gameBSONHandler: BSON[Game] = new BSON[Game] {

    import Game.{ BSONFields => F }
    import PgnImport.pgnImportBSONHandler

    def readChessGame(r: BSON.Reader): Game = {
      val light         = lightGameBSONHandler.readsWithPlayerIds(r, r str F.playerIds)
      val startedAtTurn = r intD F.startedAtTurn
      val plies         = r int F.turns atMost Game.maxPlies // unlimited can cause StackOverflowError
      val turnColor     = Color.fromPly(plies)
      val createdAt     = r date F.createdAt

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
              checkCount = if (gameVariant.threeCheck) {
                val counts = r.intsD(F.checkCount)
                chess.CheckCount(~counts.headOption, ~counts.lastOption)
              } else Game.emptyCheckCount
              ),
            variant = gameVariant,
            crazyData = gameVariant.crazyhouse option r.get[Crazyhouse.Data](F.crazyData)
          ),
          color = turnColor
        ),
        pgnMoves = decoded.pgnMoves,
        clock = r.getO[Color => Clock](F.clock) {
          clockBSONReader(createdAt, light.whitePlayer.berserk, light.blackPlayer.berserk)
        } map (_(turnColor)),
        turns = plies,
        startedAtTurn = startedAtTurn
      )

      val whiteClockHistory = r bytesO F.whiteClockHistory
      val blackClockHistory = r bytesO F.blackClockHistory

      Game(
        id = light.id,
        whitePlayer = light.whitePlayer,
        blackPlayer = light.blackPlayer,
        chess = StratGame.Chess(chessGame),
        loadClockHistory = clk =>
          for {
            bw <- whiteClockHistory
            bb <- blackClockHistory
            history <-
              BinaryFormat.clockHistory
                .read(clk.limit, bw, bb, (light.status == Status.Outoftime).option(turnColor))
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
          analysed = r boolD F.analysed,
          drawOffers = r.getD(F.drawOffers, GameDrawOffers.empty)
        )
      )
    }

    def readDraughtsGame(r: BSON.Reader): Game = {

      //lila.mon.game.fetch()

      val light = lightGameBSONHandler.readsWithPlayerIds(r, r str F.playerIds)
      val gameVariant = DraughtsVariant(r intD F.variant) | DraughtsStandard
      val startedAtTurn = r intD F.startedAtTurn
      val plies = r int F.turns atMost Game.maxPlies // unlimited can cause StackOverflowError
      val playedPlies = plies - startedAtTurn

      val decoded = r.bytesO(F.huffmanPgn).map { PdnStorage.Huffman.decode(_, playedPlies) } | {
        PdnStorage.Decoded(
          pdnMoves = PdnStorage.OldBin.decode(r bytesD F.oldPgn, playedPlies),
          pieces = BinaryFormat.piece.readDraughts(r bytes F.binaryPieces, gameVariant),
          positionHashes = r.getO[PositionHash](F.positionHashes) | Array.empty,
          lastMove = r strO F.historyLastMove flatMap(draughts.format.Uci.apply),
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
            if (counts.length > 0){
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

      val midCapture = decoded.pdnMoves.lastOption.fold(false)(_.indexOf('x') != -1) && decodedBoard.ghosts != 0
      val currentPly = if (midCapture) plies - 1 else plies
      val turnColor = Color.fromPly(currentPly)

      val decodedSituation = draughts.Situation(
        board = decodedBoard,
        color = turnColor
      )

      val createdAt = r date F.createdAt

      val draughtsGame = draughts.DraughtsGame(
        situation = decodedSituation,
        pdnMoves = decoded.pdnMoves,
        clock = r.getO[Color => Clock](F.clock) {
          clockBSONReader(createdAt, light.whitePlayer.berserk, light.blackPlayer.berserk)
        } map (_(decodedSituation.color)),
        turns = currentPly,
        startedAtTurn = startedAtTurn
      )

      val whiteClockHistory = r bytesO F.whiteClockHistory
      val blackClockHistory = r bytesO F.blackClockHistory

      Game(
        id = light.id,
        whitePlayer = light.whitePlayer,
        blackPlayer = light.blackPlayer,
        chess = StratGame.Draughts(draughtsGame),
        loadClockHistory = clk => for {
          bw <- whiteClockHistory
          bb <- blackClockHistory
          history <- BinaryFormat.clockHistory.read(clk.limit, bw, bb, (light.status == Status.Outoftime).option(decodedSituation.color))
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
          microMatch = r strO F.microMatch,
          drawLimit = r intO F.drawLimit,
          analysed = r boolD F.analysed,
          drawOffers = r.getD(F.drawOffers, GameDrawOffers.empty)//should be empty for draughts
        )
      )
    }

    def reads(r: BSON.Reader): Game = {

      lila.mon.game.fetch.increment()

      val libId = r intD F.lib
      libId match {
        case 0 => readChessGame(r)
        case 1 => readDraughtsGame(r)
        case _ => sys.error("Invalid game in the database")
      }
    }

    def writes(w: BSON.Writer, o: Game) =
      BSONDocument(
        F.id         -> o.id,
        F.playerIds  -> (o.whitePlayer.id + o.blackPlayer.id),
        F.playerUids -> w.strListO(List(~o.whitePlayer.userId, ~o.blackPlayer.userId)),
        F.whitePlayer -> w.docO(
          playerBSONHandler write ((_: Color) =>
            (_: Player.ID) => (_: Player.UserId) => (_: Player.Win) => o.whitePlayer
          )
        ),
        F.blackPlayer -> w.docO(
          playerBSONHandler write ((_: Color) =>
            (_: Player.ID) => (_: Player.UserId) => (_: Player.Win) => o.blackPlayer
          )
        ),
        F.status        -> o.status,
        F.turns         -> o.chess.turns,
        F.startedAtTurn -> w.intO(o.chess.startedAtTurn),
        F.clock -> (o.chess.clock flatMap { c =>
          clockBSONWrite(o.createdAt, c).toOption
        }),
        F.daysPerTurn       -> o.daysPerTurn,
        F.moveTimes         -> o.binaryMoveTimes,
        F.whiteClockHistory -> clockHistory(White, o.clockHistory, o.chess.clock, o.flagged),
        F.blackClockHistory -> clockHistory(Black, o.clockHistory, o.chess.clock, o.flagged),
        F.rated             -> w.boolO(o.mode.rated),
        F.lib               -> o.board.variant.gameLib.id,
        F.variant           -> o.board.variant.exotic.option(w int o.board.variant.id),
        F.bookmarks         -> w.intO(o.bookmarks),
        F.createdAt         -> w.date(o.createdAt),
        F.movedAt           -> w.date(o.movedAt),
        F.source            -> o.metadata.source.map(_.id),
        F.pgnImport         -> o.metadata.pgnImport,
        F.tournamentId      -> o.metadata.tournamentId,
        F.swissId           -> o.metadata.swissId,
        F.simulId           -> o.metadata.simulId,
        F.microMatch        -> o.metadata.microMatch,
        F.drawLimit         -> o.metadata.drawLimit,
        F.analysed          -> w.boolO(o.metadata.analysed)
      ) ++ {
        if (o.board.variant.gameLib == GameLib.Draughts()){
          o.pdnStorage match {
            case Some(PdnStorage.OldBin) => $doc(
              F.oldPgn -> PdnStorage.OldBin.encode(o.pgnMoves take Game.maxPlies),
              F.binaryPieces -> BinaryFormat.piece.writeDraughts(o.board match {
                case Board.Draughts(board) => board
                case _ => sys.error("invalid draughts board")
              }),
              F.positionHashes -> o.history.positionHashes,
              F.historyLastMove -> o.history.lastMove.map(_.uci),
              // since variants are always OldBin
              F.kingMoves -> o.history.kingMoves.nonEmpty.option(o.history.kingMoves)
            )
            case Some(PdnStorage.Huffman) => $doc(
              F.huffmanPgn -> PdnStorage.Huffman.encode(o.pgnMoves take Game.maxPlies)
            )
            case _ => sys.error("invalid draughts storage")
          }
        } else {//chess or fail
          if (o.variant.standard)
            $doc(F.huffmanPgn -> PgnStorage.Huffman.encode(o.pgnMoves take Game.maxPlies))
          else {
            val f = PgnStorage.OldBin
            $doc(
              F.oldPgn         -> f.encode(o.pgnMoves take Game.maxPlies),
              F.binaryPieces   -> BinaryFormat.piece.writeChess(o.board match {
                case Board.Chess(board) => board.pieces
                case _ => sys.error("invalid chess board")
              }),
              F.positionHashes -> o.history.positionHashes,
              F.unmovedRooks   -> o.history.unmovedRooks,
              F.castleLastMove -> CastleLastMove.castleLastMoveBSONHandler
                .writeTry(
                  CastleLastMove(
                    castles = o.history.castles,
                    lastMove = o.history match {
                      case History.Chess(h) => h.lastMove
                      case _ => sys.error("Invalid history")
                    }
                  )
                )
                .toOption,
              F.checkCount -> o.history.checkCount.nonEmpty.option(o.history.checkCount),
              F.crazyData  -> o.board.crazyData
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
      val (whiteId, blackId)   = playerIds splitAt 4
      val winC                 = r boolO F.winnerColor map(Color.fromWhite)
      val uids                 = ~r.getO[List[lila.user.User.ID]](F.playerUids)
      val (whiteUid, blackUid) = (uids.headOption.filter(_.nonEmpty), uids.lift(1).filter(_.nonEmpty))
      def makePlayer(field: String, color: Color, id: Player.ID, uid: Player.UserId): Player = {
        val builder = r.getO[Player.Builder](field)(playerBSONHandler) | emptyPlayerBuilder
        builder(color)(id)(uid)(winC map (_ == color))
      }
      LightGame(
        id = r str F.id,
        whitePlayer = makePlayer(F.whitePlayer, White, whiteId, whiteUid),
        blackPlayer = makePlayer(F.blackPlayer, Black, blackId, blackUid),
        status = r.get[Status](F.status)
      )
    }
  }

  private def clockHistory(
      color: Color,
      clockHistory: Option[ClockHistory],
      clock: Option[Clock],
      flagged: Option[Color]
  ) =
    for {
      clk     <- clock
      history <- clockHistory
      times = history(color)
    } yield BinaryFormat.clockHistory.writeSide(clk.limit, times, flagged has color)

  private[game] def clockBSONReader(since: DateTime, whiteBerserk: Boolean, blackBerserk: Boolean) =
    new BSONReader[Color => Clock] {
      def readTry(bson: BSONValue): Try[Color => Clock] =
        bson match {
          case bin: BSONBinary =>
            ByteArrayBSONHandler readTry bin map { cl =>
              BinaryFormat.clock(since).read(cl, whiteBerserk, blackBerserk)
            }
          case b => lila.db.BSON.handlerBadType(b)
        }
    }

  private[game] def clockBSONWrite(since: DateTime, clock: Clock) =
    ByteArrayBSONHandler writeTry {
      BinaryFormat clock since write clock
    }
}
