package lila.game

import strategygames.chess.format
import strategygames.chess.{ Castles, Piece, PieceMap, Pos, PositionHash, Role, UnmovedRooks }
import strategygames.{ ActionStrs, Player as PlayerIndex, VActionStrs }

import lila.db.ByteArray

sealed trait PgnStorage

private object PgnStorage {

  case object OldBin extends PgnStorage {

    def encode(pgnMoves: PgnMoves) =
      ByteArray {
        monitor(_.game.pgn.encode("old")) {
          format.pgn.Binary.writeMoves(pgnMoves).get
        }
      }

    def encodeActionStrs(actionStrs: ActionStrs) =
      ByteArray {
        monitor(_.game.pgn.encode("ngla")) {
          format.pgn.Binary.writeActionStrs(actionStrs).get
        }
      }

    def decode(bytes: ByteArray, plies: Int): VActionStrs =
      monitor(_.game.pgn.decode("old")) {
        format.pgn.Binary.readActionStrs(bytes.value.toList, plies).get.toVector.map(_.toVector)
      }
  }

  case object Huffman extends PgnStorage {

    import org.lichess.compression.game.{ Encoder, Piece as JavaPiece, Role as JavaRole }

    def encode(pgnMoves: PgnMoves) =
      ByteArray {
        monitor(_.game.pgn.encode("huffman")) {
          Encoder.encode(pgnMoves.toArray)
        }
      }
    def decode(bytes: ByteArray, plies: Int): Decoded =
      monitor(_.game.pgn.decode("huffman")) {
        val decoded         = Encoder.decode(bytes.value, plies)
        val unmovedRooks = unmovedRooksFromBitboard(decoded.board.castlingRights)
        Decoded(
          actionStrs = decoded.pgnMoves.toVector.map(Vector(_)),
          pieces = decoded.board.pieceMap().view.flatMap { case (k, v) =>
            chessPos(k).map(_ -> chessPiece(v))
          }.toMap,
          positionHashes = decoded.positionHashes,
          unmovedRooks = UnmovedRooks(unmovedRooks),
          lastMove = Option(decoded.lastUci) flatMap format.Uci.apply,
          castles = Castles(
            p1KingSide = unmovedRooks(Pos.H1),
            p1QueenSide = unmovedRooks(Pos.A1),
            p2KingSide = unmovedRooks(Pos.H8),
            p2QueenSide = unmovedRooks(Pos.A8)
          ),
          halfMoveClock = decoded.halfMoveClock
        )
      }

    private def unmovedRooksFromBitboard(bitboard: Long): Set[Pos] =
      Iterator
        .unfold(bitboard) { remaining =>
          Option.when(remaining != 0L) {
            val sq = java.lang.Long.numberOfTrailingZeros(remaining).toInt
            (sq, remaining & (remaining - 1L))
          }
        }
        .flatMap(chessPos(_))
        .toSet

    private def chessPos(sq: Integer): Option[Pos] = Pos(sq)
    private def chessRole(role: JavaRole): Role    =
      Role.javaSymbolToRole(role.symbol)

    private def chessPiece(piece: JavaPiece): Piece =
      Piece(PlayerIndex.fromP1(piece.white), chessRole(piece.role))
  }

  case class Decoded(
      actionStrs: VActionStrs,
      pieces: PieceMap,
      positionHashes: PositionHash, // irrelevant after game ends
      unmovedRooks: UnmovedRooks,   // irrelevant after game ends
      lastMove: Option[format.Uci],
      castles: Castles,  // irrelevant after game ends
      halfMoveClock: Int // irrelevant after game ends
  )

  private def monitor[A](mon: lila.mon.TimerPath)(f: => A): A =
    lila.common.Chronometer.syncMon(mon)(f)
}
