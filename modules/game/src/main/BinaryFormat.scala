package lila.game

import strategygames.{
  Board,
  ByoyomiClock,
  ByoyomiClockPlayer,
  Centis,
  Clock,
  ClockBase,
  ClockConfig,
  ClockPlayer,
  GameLogic,
  P1,
  P2,
  Piece,
  PieceMap,
  Player => PlayerIndex,
  Pos,
  Role,
  Timestamp
}
import strategygames.chess.{ Castles, Rank, UnmovedRooks }
import strategygames.chess
import strategygames.draughts
import strategygames.fairysf
import strategygames.samurai
import strategygames.togyzkumalak
import strategygames.go
import strategygames.format
import strategygames.variant.Variant
import org.joda.time.DateTime
import org.lichess.compression.clock.{ Encoder => ClockEncoder }
import scala.util.Try

import lila.db.ByteArray

object BinaryFormat {

  object fischerClockHistory {
    private val logger = lila.log("clockHistory")

    def writeSide(start: Centis, times: Vector[Centis], flagged: Boolean) = {
      val timesToWrite = if (flagged) times.dropRight(1) else times
      ByteArray(ClockEncoder.encode(timesToWrite.view.map(_.centis).to(Array), start.centis))
    }

    def readSide(start: Centis, ba: ByteArray, flagged: Boolean) = {
      val decoded: Vector[Centis] =
        ClockEncoder.decode(ba.value, start.centis).view.map(Centis.apply).to(Vector)
      if (flagged) decoded :+ Centis(0) else decoded
    }

    def read(start: Centis, bw: ByteArray, bb: ByteArray, flagged: Option[PlayerIndex]) =
      Try {
        FischerClockHistory(
          readSide(start, bw, flagged has P1),
          readSide(start, bb, flagged has P2)
        )
      }.fold(
        e => { logger.warn(s"Exception decoding history", e); none },
        some
      )
  }

  object delayClockHistory {
    private val logger = lila.log("clockHistory")

    def writeSide(start: Centis, times: Vector[Centis], flagged: Boolean) =
      fischerClockHistory.writeSide(start, times, flagged)

    def readSide(start: Centis, ba: ByteArray, flagged: Boolean) =
      fischerClockHistory.readSide(start, ba, flagged)

    def read(start: Centis, bw: ByteArray, bb: ByteArray, flagged: Option[PlayerIndex]) =
      Try {
        DelayClockHistory( // NOTE: this is the only difference from the above fischerClockHistory
          readSide(start, bw, flagged has P1),
          readSide(start, bb, flagged has P2)
        )
      }.fold(
        e => { logger.warn(s"Exception decoding history", e); none },
        some
      )
  }

  object byoyomiClockHistory {
    private val logger = lila.log("clockHistory")

    def writeSide(start: Centis, times: Vector[Centis], flagged: Boolean) = {
      val timesToWrite = if (flagged) times.dropRight(1) else times
      ByteArray(ClockEncoder.encode(timesToWrite.view.map(_.centis).to(Array), start.centis))
    }

    def readSide(start: Centis, ba: ByteArray, flagged: Boolean) = {
      val decoded: Vector[Centis] =
        ClockEncoder.decode(ba.value, start.centis).view.map(Centis.apply).to(Vector)
      if (flagged) decoded :+ Centis(0) else decoded
    }

    def read(
        byoyomi: Centis,
        start: Centis,
        bs: ByteArray,
        bg: ByteArray,
        pe: PeriodEntries,
        flagged: Option[PlayerIndex]
    ) =
      Try {
        ByoyomiClockHistory(
          byoyomi,
          readSide(start, bs, flagged has P1),
          readSide(start, bg, flagged has P2),
          pe
        )
      }.fold(
        e => { logger.warn(s"Exception decoding history", e); none },
        some
      )
  }

  object plyTime {

    private type MT = Int // centiseconds
    private val size          = 16
    private val buckets       =
      List(10, 50, 100, 150, 200, 300, 400, 500, 600, 800, 1000, 1500, 2000, 3000, 4000, 6000)
    private val encodeCutoffs = buckets zip buckets.tail map { case (i1, i2) =>
      (i1 + i2) / 2
    } toVector

    private val decodeMap: Map[Int, MT] = buckets.view.zipWithIndex.map(x => x._2 -> x._1).toMap

    def write(mts: Vector[Centis]): ByteArray = {
      def enc(mt: Centis) = encodeCutoffs.search(mt.centis).insertionPoint
      mts
        .grouped(2)
        .map {
          case Vector(a, b) => (enc(a) << 4) + enc(b)
          case Vector(a)    => enc(a) << 4
          case v            => sys error s"plyTime.write unexpected $v"
        }
        .map(_.toByte)
        .toArray
    }

    def read(ba: ByteArray, plies: Int): Vector[Centis] = {
      def dec(x: Int) = decodeMap.getOrElse(x, decodeMap(size - 1))
      ba.value map toInt flatMap { k =>
        Array(dec(k >> 4), dec(k & 15))
      }
    }.view.take(plies).map(Centis.apply).toVector
  }

  case class fischerClock(start: Timestamp) {

    def legacyElapsed(clock: ClockBase, playerIndex: PlayerIndex) =
      clock.limit - clock.clockPlayer(playerIndex).remaining

    def computeRemaining(config: ClockConfig, legacyElapsed: Centis) =
      config.limit - legacyElapsed

    // TODO: (Bronstein / SimpleDelay): We're reusing the same spot in the binary format for both increment and delay.
    //       This works, but should be reconsidered if we add more clock types, or when we refactor to the more general
    //       system.
    def write(clock: Clock): ByteArray = {
      Array(writeClockLimit(clock.limitSeconds), clock.graceSeconds.toByte) ++
        writeSignedInt24(legacyElapsed(clock, P1).centis) ++
        writeSignedInt24(legacyElapsed(clock, P2).centis) ++
        clock.timestamp.fold(Array.empty[Byte])(writeTimestamp)
    }

    // TODO: if we ever need to do things _besides_ the ones we have, this API
    //       will need to change.
    def read(
        configConstructor: (Int, Int) => ClockConfig,
        ba: ByteArray,
        p1Berserk: Boolean,
        p2Berserk: Boolean
    ): PlayerIndex => Clock =
      playerIndex => {
        val ia = ba.value map toInt

        // ba.size might be greater than 12 with 5 bytes timers
        // ba.size might be 8 if there was no timer.
        // #TODO remove 5 byte timer case! But fix the DB first!
        val timestamp = {
          if (ia.lengthIs == 12) readTimer(readInt(ia(8), ia(9), ia(10), ia(11)))
          else None
        }

        ia match {
          case Array(b1, b2, b3, b4, b5, b6, b7, b8, _*) =>
            val config   = configConstructor(readClockLimit(b1), b2)
            val legacyP1 = Centis(readSignedInt24(b3, b4, b5))
            val legacyP2 = Centis(readSignedInt24(b6, b7, b8))
            Clock(
              config = config,
              player = playerIndex,
              players = PlayerIndex.Map(
                ClockPlayer
                  .withConfig(config)
                  .withBerserk(p1Berserk)
                  .setRemaining(computeRemaining(config, legacyP1)),
                ClockPlayer
                  .withConfig(config)
                  .withBerserk(p2Berserk)
                  .setRemaining(computeRemaining(config, legacyP2))
              ),
              timestamp = timestamp
            )
          case _                                         => sys error s"BinaryFormat.clock.read invalid bytes: ${ba.showBytes}"
        }
      }

    private def writeTimestamp(timestamp: Timestamp) = {
      val centis  = (timestamp - start).centis
      /*
       * A zero timer is resolved by `readTimer` as the absence of a timer.
       * As a result, a clock that is started with a timer = 0
       * resolves as a clock that is not started.
       * This can happen when the clock was started at the same time as the game
       * For instance in simuls
       */
      val nonZero = centis atLeast 1
      writeInt(nonZero)
    }

    private def readTimer(l: Int) =
      if (l != 0) Some(start + Centis(l)) else None

    private def writeClockLimit(limit: Int): Byte = {
      // The database expects a byte for a limit, and this is limit / 60.
      // For 0.5+0, this does not give a round number, so there needs to be
      // an alternative way to describe 0.5.
      // The max limit where limit % 60 == 0, returns 180 for limit / 60
      // So, for the limits where limit % 30 == 0, we can use the space
      // from 181-255, where 181 represents 0.25 and 182 represents 0.50...
      (if (limit % 60 == 0) limit / 60 else limit / 15 + 180).toByte
    }

    private def readClockLimit(i: Int) = {
      if (i < 181) i * 60 else (i - 180) * 15
    }
  }

  object fischerClock {
    def apply(start: DateTime) = new fischerClock(Timestamp(start.getMillis))
  }

  case class byoyomiClock(start: Timestamp) {

    def legacyElapsed(clock: ByoyomiClock, playerIndex: PlayerIndex) =
      clock.limit - clock.players(playerIndex).remaining

    def computeRemaining(config: ByoyomiClock.Config, legacyElapsed: Centis) =
      config.limit - legacyElapsed

    def write(clock: ByoyomiClock): ByteArray = {
      Array(writeClockLimit(clock.limitSeconds), clock.config.incrementSeconds.toByte) ++
        writeSignedInt24(legacyElapsed(clock, P1).centis) ++
        writeSignedInt24(legacyElapsed(clock, P2).centis) ++
        clock.timestamp.fold(Array.empty[Byte])(writeTimestamp) ++ Array(
          clock.byoyomiSeconds.toByte,
          clock.periodsTotal.toByte
        )
    }

    def read(
        ba: ByteArray,
        periodEntries: PeriodEntries,
        p1Berserk: Boolean,
        p2Berserk: Boolean
    ): PlayerIndex => ByoyomiClock =
      playerIndex => {
        val ia = ba.value map toInt

        // ba.size might be greater than 12 with 5 bytes timers
        // ba.size might be 8 if there was no timer.
        // #TODO remove 5 byte timer case! But fix the DB first!
        val timestamp = {
          if (ia.size >= 12) readTimer(readInt(ia(8), ia(9), ia(10), ia(11)))
          else None
        }

        val byo = {
          if (ia.size == 14) ia(12)
          else if (ia.size == 10) ia(8)
          else 0
        }

        val per = {
          if (ia.size == 14) ia(13)
          else if (ia.size == 10) ia(9)
          else 1
        }

        ia match {
          case Array(b1, b2, b3, b4, b5, b6, b7, b8, _*) => {
            val config   = ByoyomiClock.Config(readClockLimit(b1), b2, byo, per)
            val legacyP1 = Centis(readSignedInt24(b3, b4, b5))
            val legacyP2 = Centis(readSignedInt24(b6, b7, b8))
            ByoyomiClock(
              config = config,
              player = playerIndex,
              players = PlayerIndex.Map(
                ByoyomiClockPlayer
                  .withConfig(config)
                  .copy(berserk = p1Berserk)
                  .setRemaining(computeRemaining(config, legacyP1))
                  .setPeriods(periodEntries(P1).size atLeast config.initPeriod),
                ByoyomiClockPlayer
                  .withConfig(config)
                  .copy(berserk = p2Berserk)
                  .setRemaining(computeRemaining(config, legacyP2))
                  .setPeriods(periodEntries(P2).size atLeast config.initPeriod)
              ),
              timestamp = timestamp
            )
          }
          case _                                         => sys error s"BinaryFormat.clock.read invalid bytes: ${ba.showBytes}"
        }
      }

    private def writeTimestamp(timestamp: Timestamp) = {
      val centis  = (timestamp - start).centis
      /*
       * A zero timer is resolved by `readTimer` as the absence of a timer.
       * As a result, a clock that is started with a timer = 0
       * resolves as a clock that is not started.
       * This can happen when the clock was started at the same time as the game
       * For instance in simuls
       */
      val nonZero = centis atLeast 1
      writeInt(nonZero)
    }

    private def readTimer(l: Int) =
      if (l != 0) Some(start + Centis(l)) else None

    private def writeClockLimit(limit: Int): Byte = {
      // The database expects a byte for a limit, and this is limit / 60.
      // For 0.5+0, this does not give a round number, so there needs to be
      // an alternative way to describe 0.5.
      // The max limit where limit % 60 == 0, returns 180 for limit / 60
      // So, for the limits where limit % 30 == 0, we can use the space
      // from 181-255, where 181 represents 0.25 and 182 represents 0.50...
      (if (limit % 60 == 0) limit / 60 else limit / 15 + 180).toByte
    }

    private def readClockLimit(i: Int) = {
      if (i < 181) i * 60 else (i - 180) * 15
    }
  }

  object byoyomiClock {
    def apply(start: DateTime) = new byoyomiClock(Timestamp(start.getMillis))
  }

  object periodEntries {
    private val logger = lila.log("periodEntries")

    def writeSide(v: Vector[Int]): ByteArray                      = {
      def intToShort(i: Int): Array[Byte] = Array((i >> 8).toByte, i.toByte)
      (v.flatMap(intToShort _)).toArray
    }
    def readSide(ba: ByteArray): Vector[Int]                      = {
      def backToInt(b: Array[Byte]): Int =
        b map toInt match {
          case Array(b1, b2) => (b1 << 8) + b2
          case _             => 0
        }
      val pairs                          = ba.value.grouped(2)
      (pairs map (backToInt _)).toVector
    }
    def read(bs: ByteArray, bg: ByteArray): Option[PeriodEntries] =
      Try {
        PeriodEntries(readSide(bs), readSide(bg))
      }.fold(
        e => { logger.warn(s"Exception decoding period entries", e); none },
        some
      )
  }

  // This class is chess only for the time being.
  object castleLastMove {

    def write(clmt: CastleLastMove): ByteArray = {

      val castleInt = clmt.castles.toSeq.zipWithIndex.foldLeft(0) {
        case (acc, (false, _)) => acc
        case (acc, (true, p))  => acc + (1 << (3 - p))
      }

      def posInt(pos: Pos): Int = pos.toInt
      val lastMoveInt           = clmt.lastMove.map(_.origDest).fold(0) { case (o, d) =>
        (posInt(Pos.Chess(o)) << 6) + posInt(Pos.Chess(d))
      }
      Array((castleInt << 4) + (lastMoveInt >> 8) toByte, lastMoveInt.toByte)
    }

    def read(ba: ByteArray): CastleLastMove = {
      val ints = ba.value map toInt
      doRead(ints(0), ints(1))
    }

    private def doRead(b1: Int, b2: Int) =
      CastleLastMove(
        castles = Castles(b1 > 127, (b1 & 64) != 0, (b1 & 32) != 0, (b1 & 16) != 0),
        lastMove = for {
          orig <- chess.Pos.at((b1 & 15) >> 1, ((b1 & 1) << 2) + (b2 >> 6))
          dest <- chess.Pos.at((b2 & 63) >> 3, b2 & 7)
          if orig != chess.Pos.A1 || dest != chess.Pos.A1
        } yield chess.format.Uci.Move(orig, dest)
      )
  }

  object pos {
    def writeGo(squares: List[Pos]): ByteArray = {
      ByteArray(
        squares
          .flatMap {
            case Pos.Go(g) => Some(writeSignedInt16(g.index))
            case _         => None
          }
          .toArray
          .flatten
      )
    }
    def readGo(ba: ByteArray): List[Pos]       = {
      val ia                                         = ba.value map toInt toList
      def intPos(i: List[Int]): List[Option[go.Pos]] = {
        i match {
          case Nil                => Nil
          case (b1 :: b2 :: rest) => go.Pos.apply(readSignedInt16(b1, b2)) :: intPos(rest)
          case x                  => throw new Exception(s"Binary go reader failed: ${x map showByte mkString ","}")
        }
      }
      intPos(ia).flatMap(p => p.map(Pos.Go(_)))
    }
  }

  object piece {

    def writeChess(pieces: chess.PieceMap): ByteArray = {
      def posInt(pos: chess.Pos): Int =
        (pieces get pos).fold(0) { piece =>
          piece.player.fold(0, 128) + piece.role.binaryInt
        }
      ByteArray(chess.Pos.all.map(posInt(_).toByte).toArray)
    }

    def readChess(ba: ByteArray, variant: chess.variant.Variant): chess.PieceMap = {
      def splitInts(b: Byte)                      = {
        val int = b.toInt
        Array(int >> 4, int & 0x0f)
      }
      def intPiece(int: Int): Option[chess.Piece] =
        chess.Role.binaryInt(int & 127) map { role =>
          chess.Piece(PlayerIndex.fromP1((int & 128) == 0), role)
        }
      (chess.Pos.all zip ba.value).view
        .flatMap { case (pos, int) =>
          intPiece(int) map (pos -> _)
        }
        .to(Map)
    }

    private val groupedPos: Map[draughts.Board.BoardSize, Array[(draughts.PosMotion, draughts.PosMotion)]] =
      draughts.Board.BoardSize.all
        .map { size =>
          size -> getGroupedPos(size)
        }
        .to(Map)

    private def getGroupedPos(size: draughts.Board.BoardSize) = size.pos.all grouped 2 collect {
      case List(p1, p2) => (p1, p2)
    } toArray

    def writeDraughts(pieces: draughts.PieceMap, variant: draughts.variant.Variant): ByteArray = {
      def posInt(pos: draughts.Pos): Int = (pieces get pos).fold(0) { piece =>
        piece.player.fold(0, 8) + piece.role.binaryInt
      }
      ByteArray(groupedPos(variant.boardSize) map { case (p1, p2) =>
        ((posInt(p1) << 4) + posInt(p2)).toByte
      })
    }

    def writeDraughts(board: draughts.Board): ByteArray = writeDraughts(board.pieces, board.variant)

    def readDraughts(ba: ByteArray, variant: draughts.variant.Variant): draughts.PieceMap = {
      def splitInts(b: Byte)                         = {
        val int = b.toInt
        Array(int >> 4, int & 0x0f)
      }
      def intPiece(int: Int): Option[draughts.Piece] =
        draughts.Role.binaryInt(int & 7) map { role =>
          draughts.Piece(PlayerIndex((int & 8) == 0), role)
        }
      val pieceInts                                  = ba.value flatMap splitInts
      (variant.boardSize.pos.all zip pieceInts)
        .flatMap { case (pos, int) =>
          intPiece(int) map (pos -> _)
        }
        .to(Map)
    }

    def writeFairySF(pieces: fairysf.PieceMap): ByteArray = {
      def posInt(pos: fairysf.Pos): Int =
        (pieces get pos).fold(0) { piece =>
          piece.player.fold(0, 128) + piece.role.binaryInt
        }
      ByteArray(fairysf.Pos.all.map(posInt(_).toByte).toArray)
    }

    def readFairySF(ba: ByteArray, variant: fairysf.variant.Variant): fairysf.PieceMap = {
      // def splitInts(b: Byte) = {
      //  val int = b.toInt
      //  Array(int >> 4, int & 0x0f)
      // }
      def intPiece(int: Int): Option[fairysf.Piece] =
        fairysf.Role.allByBinaryInt(variant.gameFamily).get(int & 127) map { role =>
          fairysf.Piece(PlayerIndex.fromP1((int & 128) == 0), role)
        }
      (fairysf.Pos.all zip ba.value).view
        .flatMap { case (pos, int) =>
          intPiece(int) map (pos -> _)
        }
        .to(Map)
    }

    def writeSamurai(pieces: samurai.PieceMap): ByteArray = {
      def posInt(pos: samurai.Pos): Int =
        (pieces get pos).fold(0) { case (piece, count) =>
          piece.player.fold(0, 128) + count
        }
      ByteArray(samurai.Pos.all.map(posInt(_).toByte).toArray)
    }

    def readSamurai(ba: ByteArray, variant: samurai.variant.Variant): samurai.PieceMap = {
      def intPiece(int: Int): Option[(samurai.Piece, Int)] =
        Some(
          (
            samurai.Piece(PlayerIndex.fromP1((int & 128) == 0), variant.defaultRole),
            int & 127
          )
        )
      (samurai.Pos.all zip ba.value).view
        .flatMap { case (pos, int) =>
          intPiece(int) map (pos -> _)
        }
        .to(Map)
    }

    def writeTogyzkumalak(pieces: togyzkumalak.PieceMap): ByteArray = {
      def posInt(pos: togyzkumalak.Pos): Int =
        (pieces get pos).fold(0) {
          case (piece, count) if piece.role == togyzkumalak.Role.defaultRole =>
            count
          case (piece, _)                                                    =>
            piece.role.binaryInt

        }
      ByteArray(togyzkumalak.Pos.all.map(posInt(_).toByte).toArray)
    }

    def readTogyzkumalak(ba: ByteArray, variant: togyzkumalak.variant.Variant): togyzkumalak.PieceMap = {
      def intPiece(player: PlayerIndex, int: Int): Option[(togyzkumalak.Piece, Int)] =
        if (int <= 162)
          Some(
            (togyzkumalak.Piece(player, variant.defaultRole), int)
          )
        else
          Some(
            (
              togyzkumalak.Piece(
                !player,
                togyzkumalak.Role.binaryInt(int).getOrElse(variant.defaultRole)
              ),
              1
            )
          )
      def unsignInt(int: Int)                                                        = if (int < 0) 256 + int else int
      (togyzkumalak.Pos.all zip ba.value).view
        .flatMap { case (pos, int) =>
          intPiece(pos.player, unsignInt(int)) map (pos -> _)
        }
        .to(Map)
    }

    def writeGo(pieces: go.PieceMap): ByteArray = {
      def posInt(pos: go.Pos): Int =
        (pieces get pos).fold(0) { piece =>
          piece.player.fold(0, 128) + piece.role.binaryInt
        }
      ByteArray(go.Pos.all.map(posInt(_).toByte).toArray)
    }

    def readGo(ba: ByteArray, variant: go.variant.Variant): go.PieceMap = {
      // def splitInts(b: Byte) = {
      //  val int = b.toInt
      //  Array(int >> 4, int & 0x0f)
      // }
      def intPiece(int: Int): Option[go.Piece] =
        go.Role.allByBinaryInt.get(int & 127) map { role =>
          go.Piece(PlayerIndex.fromP1((int & 128) == 0), role)
        }
      (go.Pos.all zip ba.value).view
        .flatMap { case (pos, int) =>
          intPiece(int) map (pos -> _)
        }
        .to(Map)
    }

    // cache standard start position
    def standard(lib: GameLogic) = lib match {
      case GameLogic.Chess()        => writeChess(chess.Board.init(chess.variant.Standard).pieces)
      case GameLogic.Draughts()     =>
        writeDraughts(
          draughts.Board.init(draughts.variant.Standard).pieces,
          draughts.variant.Standard
        )
      case GameLogic.Samurai()      => writeSamurai(samurai.Board.init(samurai.variant.Oware).pieces)
      case GameLogic.Togyzkumalak() =>
        writeTogyzkumalak(togyzkumalak.Board.init(togyzkumalak.variant.Togyzkumalak).pieces)
      case GameLogic.Go()           => writeGo(go.Board.init(go.variant.Go19x19).pieces)
      case _                        => sys.error("Cant write to binary for lib")
    }

  }

  object unmovedRooks {

    val emptyByteArray = ByteArray(Array(0, 0))

    def write(o: UnmovedRooks): ByteArray = {
      if (o.pos.isEmpty) emptyByteArray
      else {
        var p1 = 0
        var p2 = 0
        o.pos.foreach { pos =>
          if (pos.rank == Rank.First) p1 = p1 | (1 << (7 - pos.file.index))
          else p2 = p2 | (1 << (7 - pos.file.index))
        }
        Array(p1.toByte, p2.toByte)
      }
    }

    private def bitAt(n: Int, k: Int) = (n >> k) & 1

    private val arrIndexes = 0 to 1
    private val bitIndexes = 0 to 7
    private val p1Std      = Set(chess.Pos.A1, chess.Pos.H1)
    private val p2Std      = Set(chess.Pos.A8, chess.Pos.H8)

    def read(ba: ByteArray) =
      UnmovedRooks {
        var set = Set.empty[chess.Pos]
        arrIndexes.foreach { i =>
          val int = ba.value(i).toInt
          if (int != 0) {
            if (int == -127) set = if (i == 0) p1Std else set ++ p2Std
            else
              bitIndexes.foreach { j =>
                if (bitAt(int, j) == 1) set = set + chess.Pos.at(7 - j, 7 * i).get
              }
          }
        }
        set
      }
  }

  @inline private def toInt(b: Byte): Int = b & 0xff

  def writeInt16(int: Int) = {
    val i = if (int < (1 << 16)) int else 0
    Array((i >>> 8).toByte, i.toByte)
  }

  private val int15Max           = 1 << 15
  def writeSignedInt16(int: Int) = {
    val i = if (int < 0) int15Max - int else math.min(int, int15Max)
    writeInt16(i)
  }

  def readInt16(b1: Int, b2: Int) = (b1 << 8) | b2

  def readSignedInt16(b1: Int, b2: Int) = {
    val i = readInt16(b1, b2)
    if (i > int15Max) int15Max - i else i
  }

  def writeInt24(int: Int) = {
    val i = if (int < (1 << 24)) int else 0
    Array((i >>> 16).toByte, (i >>> 8).toByte, i.toByte)
  }

  private val int23Max           = 1 << 23
  def writeSignedInt24(int: Int) = {
    val i = if (int < 0) int23Max - int else math.min(int, int23Max)
    writeInt24(i)
  }

  def readInt24(b1: Int, b2: Int, b3: Int) = (b1 << 16) | (b2 << 8) | b3

  def readSignedInt24(b1: Int, b2: Int, b3: Int) = {
    val i = readInt24(b1, b2, b3)
    if (i > int23Max) int23Max - i else i
  }

  def writeInt(i: Int) =
    Array(
      (i >>> 24).toByte,
      (i >>> 16).toByte,
      (i >>> 8).toByte,
      i.toByte
    )

  def readInt(b1: Int, b2: Int, b3: Int, b4: Int) = {
    (b1 << 24) | (b2 << 16) | (b3 << 8) | b4
  }

  private def showByte(b: Int): String = "%08d" format (b.toBinaryString.toInt)
}
