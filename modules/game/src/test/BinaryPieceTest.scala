package lila.game

import strategygames._
import strategygames.Pos._
import org.specs2.mutable._

import lila.db.ByteArray
import strategygames.chess.variant._

class BinaryPieceTest extends Specification {

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
      "A1 white king" in {
        write(Map(A1 -> Piece(White, King))) must_== {
          "00000001" :: List.fill(63)(noop)
        }
      }
      "A1 black knight" in {
        write(Map(A1 -> Piece(Black, Knight))) must_== {
          "10000100" :: List.fill(63)(noop)
        }
      }
      "B1 black pawn" in {
        write(Map(B1 -> Piece(Black, Pawn))) must_== {
          noop :: "10000110" :: List.fill(62)(noop)
        }
      }
      "A1 black knight, B1 white bishop" in {
        write(Map(A1 -> Piece(Black, Knight), B1 -> Piece(White, Bishop))) must_== {
          "10000100" :: "00000101" :: List.fill(62)(noop)
        }
      }
      "A1 black knight, B1 white bishop, C1 white queen" in {
        write(Map(A1 -> Piece(Black, Knight), B1 -> Piece(White, Bishop), C1 -> Piece(White, Queen))) must_== {
          "10000100" :: "00000101" :: "00000010" :: List.fill(61)(noop)
        }
      }
      "H8 black knight" in {
        write(Map(H8 -> Piece(Black, Knight))) must_== {
          List.fill(63)(noop) :+ "10000100"
        }
      }
      "G8 black knight, H8 white bishop" in {
        write(Map(G8 -> Piece(Black, Knight), H8 -> Piece(White, Bishop))) must_== {
          List.fill(62)(noop) :+ "10000100" :+ "00000101"
        }
      }
      "A1 black LOAChecker, B1 white LOAChecker" in {
        write(Map(A1 -> Piece(Black, LOAChecker), B1 -> Piece(White, LOAChecker))) must_== {
          "10001000" :: "00001000" :: List.fill(62)(noop)
        }
      }
    }
    "read" should {
      "empty board" in {
        read(List.fill(64)(noop)) must_== Map.empty
        "A1 white king" in {
          read("00000001" :: List.fill(63)(noop)) must_== Map(A1 -> Piece(White, King))
        }
        "B1 black pawn" in {
          read(noop :: "10000110" :: List.fill(62)(noop)) must_== Map(B1 -> Piece(Black, Pawn))
        }
        "A1 black LOAChecker, B1 white LOAChecker" in {
          read("10001000" :: "00001000" :: List.fill(62)(noop), LinesOfAction) must_== Map(A1 -> Piece(Black, LOAChecker), B1 -> Piece(White, LOAChecker))
        }
      }
    }
  }
}
