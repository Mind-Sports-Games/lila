package lila.game

import strategygames.fairysf
import strategygames.fairysf.format
import strategygames.fairysf.{ Piece, PieceMap, Pos, PositionHash, Role }
import strategygames.{ Player => SGPlayer, GameFamily }

import lila.db.ByteArray

sealed trait PfnStorage

private object PfnStorage {

  case object OldBin extends PfnStorage {

    def encode(gf: GameFamily, pgnMoves: PgnMoves) =
      ByteArray {
        monitor(_.game.pgn.encode("old")) {
          format.pgn.Binary.writeMoves(gf, pgnMoves).get
        }
      }

    def decode(bytes: ByteArray, plies: Int): PgnMoves =
      monitor(_.game.pgn.decode("old")) {
        format.pgn.Binary.readMoves(bytes.value.toList, plies).get.toVector
      }
  }

  //is Huffman used/needed anywhere for PfnStorage?
  //we default to Shogi in here
  case object Huffman extends PfnStorage {

    import org.lichess.compression.game.{ Encoder, Piece => JavaPiece, Role => JavaRole }
    import scala.jdk.CollectionConverters._

    def encode(pgnMoves: PgnMoves) =
      ByteArray {
        monitor(_.game.pgn.encode("huffman")) {
          Encoder.encode(pgnMoves.toArray)
        }
      }
    def decode(bytes: ByteArray, plies: Int): Decoded =
      monitor(_.game.pgn.decode("huffman")) {
        val decoded      = Encoder.decode(bytes.value, plies)
        val unmovedRooks = decoded.unmovedRooks.asScala.view.flatMap(fairysfPos).to(Set)
        Decoded(
          pgnMoves = decoded.pgnMoves.toVector,
          pieces = decoded.pieces.asScala.view.flatMap { case (k, v) =>
            fairysfPos(k).map(_ -> fairysfPiece(v))
          }.toMap,
          positionHashes = decoded.positionHashes,
          //unmovedRooks = UnmovedRooks(unmovedRooks),
          lastMove = Option(decoded.lastUci).flatMap(m => format.Uci.apply(GameFamily.Shogi(), m)),
          //castles = Castles(
          //  p1KingSide = unmovedRooks(Pos.H1),
          //  p1QueenSide = unmovedRooks(Pos.A1),
          //  p2KingSide = unmovedRooks(Pos.H8),
          //  p2QueenSide = unmovedRooks(Pos.A8)
          //),
          halfMoveClock = decoded.halfMoveClock
        )
      }

    private def fairysfPos(sq: Integer): Option[Pos] = Pos(sq)
    private def fairysfRole(role: JavaRole): Role =
      Role.javaSymbolToRole(role.symbol)

    private def fairysfPiece(piece: JavaPiece): Piece =
      Piece(SGPlayer.fromP1(piece.white), fairysfRole(piece.role))
  }

  case class Decoded(
      pgnMoves: PgnMoves,
      pieces: PieceMap,
      positionHashes: PositionHash, // irrelevant after game ends
      //unmovedRooks: UnmovedRooks,   // irrelevant after game ends
      lastMove: Option[format.Uci],
      //castles: Castles,  // irrelevant after game ends
      halfMoveClock: Int // irrelevant after game ends
  )

  private def monitor[A](mon: lila.mon.TimerPath)(f: => A): A =
    lila.common.Chronometer.syncMon(mon)(f)
}
