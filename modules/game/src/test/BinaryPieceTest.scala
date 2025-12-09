package lila.game

import strategygames.chess._
import strategygames.chess.Pos._
import org.specs2.mutable._

import lila.db.ByteArray
import strategygames.chess.variant._

class BinaryPieceTest extends Specification {

  /*
  val noop = "00000000"
  def write(all: PieceMap): List[String] =
    (BinaryFormat.piece write all).showBytes.split(',').toList
  def read(bytes: List[String], variant: Variant = Standard): PieceMap =
    BinaryFormat.piece.read(ByteArray.parseBytes(bytes), variant)

  "binary pieces" should {
    "write" should {
      "empty board" in {
        write(Map.empty) must_== List.fill(64)(noop)
      }
      "A1 p1 king" in {
        write(Map(A1 -> Piece(P1, King))) must_== {
          "00000001" :: List.fill(63)(noop)
        }
      }
      "A1 p2 knight" in {
        write(Map(A1 -> Piece(P2, Knight))) must_== {
          "10000100" :: List.fill(63)(noop)
        }
      }
      "B1 p2 pawn" in {
        write(Map(B1 -> Piece(P2, Pawn))) must_== {
          noop :: "10000110" :: List.fill(62)(noop)
        }
      }
      "A1 p2 knight, B1 p1 bishop" in {
        write(Map(A1 -> Piece(P2, Knight), B1 -> Piece(P1, Bishop))) must_== {
          "10000100" :: "00000101" :: List.fill(62)(noop)
        }
      }
      "A1 p2 knight, B1 p1 bishop, C1 p1 queen" in {
        write(Map(A1 -> Piece(P2, Knight), B1 -> Piece(P1, Bishop), C1 -> Piece(P1, Queen))) must_== {
          "10000100" :: "00000101" :: "00000010" :: List.fill(61)(noop)
        }
      }
      "H8 p2 knight" in {
        write(Map(H8 -> Piece(P2, Knight))) must_== {
          List.fill(63)(noop) :+ "10000100"
        }
      }
      "G8 p2 knight, H8 p1 bishop" in {
        write(Map(G8 -> Piece(P2, Knight), H8 -> Piece(P1, Bishop))) must_== {
          List.fill(62)(noop) :+ "10000100" :+ "00000101"
        }
      }
      "A1 p2 LOAChecker, B1 p1 LOAChecker" in {
        write(Map(A1 -> Piece(P2, LOAChecker), B1 -> Piece(P1, LOAChecker))) must_== {
          "10001000" :: "00001000" :: List.fill(62)(noop)
        }
      }
    }
    "read" should {
      "empty board" in {
        read(List.fill(64)(noop)) must_== Map.empty
        "A1 p1 king" in {
          read("00000001" :: List.fill(63)(noop)) must_== Map(A1 -> Piece(P1, King))
        }
        "B1 p2 pawn" in {
          read(noop :: "10000110" :: List.fill(62)(noop)) must_== Map(B1 -> Piece(P2, Pawn))
        }
        "A1 p2 LOAChecker, B1 p1 LOAChecker" in {
          read("10001000" :: "00001000" :: List.fill(62)(noop), LinesOfAction) must_== Map(A1 -> Piece(P2, LOAChecker), B1 -> Piece(P1, LOAChecker))
        }
      }
    }
  }
   */
}
