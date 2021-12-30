package lila.game

import strategygames.draughts
import strategygames.draughts.{ format, PieceMap }
import strategygames.draughts.format.Uci
import strategygames.draughts.{ Piece, Pos, PositionHash, Role }
import strategygames.{ Player => SGPlayer }
import strategygames.draughts.variant.{ Standard, Variant }
import lila.db.ByteArray

sealed trait PdnStorage

private object PdnStorage {

  case object OldBin extends PdnStorage {

    def encode(pdnmoves: PgnMoves) = ByteArray {
      monitor(_.game.pgn.encode("draughts.old")) {
        format.pdn.Binary.writeMoves(pdnmoves).get
      }
    }

    def decode(bytes: ByteArray, plies: Int): PgnMoves =
      monitor(_.game.pgn.decode("draughts.old")) {
        format.pdn.Binary.readMoves(bytes.value.toList, plies).get.toVector
      }
  }

  case object Huffman extends PdnStorage {

    import org.lichess.compression.game.{
      Encoder,
      Square => JavaSquare,
      Piece => JavaPiece,
      Role => JavaRole
    }
    import scala.jdk.CollectionConverters._

    def encode(pdnmoves: PgnMoves) = ByteArray {
      monitor(_.game.pgn.encode("draughts.huffman")) {
        Encoder.encode(pdnmoves.toArray)
      }
    }
    def decode(bytes: ByteArray, plies: Int): Decoded = {
      monitor(_.game.pgn.decode("draughts.huffman")) {
        val decoded      = Encoder.decode(bytes.value, plies)
        val unmovedRooks = decoded.unmovedRooks.asScala.view.flatMap(draughtsPos).to(Set)
        Decoded(
          pdnMoves = decoded.pgnMoves.toVector,
          pieces = decoded.pieces.asScala.view.flatMap { case (k, v) =>
            draughtsPos(k).map(_ -> draughtsPiece(v))
          }.toMap,
          positionHashes = decoded.positionHashes,
          lastMove = Option(decoded.lastUci) flatMap (Uci.apply),
          format = Huffman
        )
      }
    }

    private def draughtsPos(sq: Integer): Option[Pos] =
      Standard.boardSize.pos.posAt(JavaSquare.file(sq) + 1, JavaSquare.rank(sq) + 1)

    private def draughtsRole(role: JavaRole): Role =
      Role.javaSymbolToRole(role.symbol)

    private def draughtsPiece(piece: JavaPiece): Piece =
      Piece(SGPlayer(piece.white), draughtsRole(piece.role))

  }

  case class Decoded(
      pdnMoves: PgnMoves,
      pieces: PieceMap,
      positionHashes: PositionHash, // irrelevant after game ends
      lastMove: Option[Uci], // Draughts UCI
      format: PdnStorage
  )

  private val betaTesters                                                                = Set("")
  private def shouldUseHuffman(variant: Variant, playerUserIds: List[lila.user.User.ID]) = false
  // TODO: Seems huffman encoding is beta for lidraughts, so let's skip it for now?
  /*try {
      lidraughts.game.Env.current.pdnEncodingSetting.get() match {
        case "all" => true
        case "beta" if playerUserIds.exists(betaTesters.contains) => true
        case _ => false
      }
    } catch {
      case e: Throwable =>
        println(e)
        false // breaks in tests. The shouldUseHuffman function is temporary anyway
    }
  }*/
  private[game] def apply(variant: Variant, playerUserIds: List[lila.user.User.ID]): PdnStorage =
    if (shouldUseHuffman(variant, playerUserIds)) Huffman else OldBin

  private def monitor[A](mon: lila.mon.TimerPath)(f: => A): A =
    lila.common.Chronometer.syncMon(mon)(f)
}
