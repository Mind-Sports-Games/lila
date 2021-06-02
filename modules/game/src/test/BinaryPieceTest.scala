package lila.game

import chess._
import chess.Pos._
import org.specs2.mutable._

import lila.db.ByteArray
import chess.variant._

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
        write(Map(A1 -> White.king)) must_== {
          "00000001" :: List.fill(63)(noop)
        }
      }
      "A1 black knight" in {
        write(Map(A1 -> Black.knight)) must_== {
          "10000100" :: List.fill(63)(noop)
        }
      }
      "B1 black pawn" in {
        write(Map(B1 -> Black.pawn)) must_== {
          noop :: "10000110" :: List.fill(62)(noop)
        }
      }
      "A1 black knight, B1 white bishop" in {
        write(Map(A1 -> Black.knight, B1 -> White.bishop)) must_== {
          "10000100" :: "00000101" :: List.fill(62)(noop)
        }
      }
      "A1 black knight, B1 white bishop, C1 white queen" in {
        write(Map(A1 -> Black.knight, B1 -> White.bishop, C1 -> White.queen)) must_== {
          "10000100" :: "00000101" :: "00000010" :: List.fill(61)(noop)
        }
      }
      "H8 black knight" in {
        write(Map(H8 -> Black.knight)) must_== {
          List.fill(63)(noop) :+ "10000100"
        }
      }
      "G8 black knight, H8 white bishop" in {
        write(Map(G8 -> Black.knight, H8 -> White.bishop)) must_== {
          List.fill(62)(noop) :+ "10000100" :+ "00000101"
        }
      }
      "A1 black LOAChecker, B1 white LOAChecker" in {
        write(Map(A1 -> Black.loachecker, B1 -> White.loachecker)) must_== {
          "10001000" :: "00001000" :: List.fill(62)(noop)
        }
      }
    }
    "read" should {
      "empty board" in {
        read(List.fill(64)(noop)) must_== Map.empty
        "A1 white king" in {
          read("00000001" :: List.fill(63)(noop)) must_== Map(A1 -> White.king)
        }
        "B1 black pawn" in {
          read(noop :: "10000110" :: List.fill(62)(noop)) must_== Map(B1 -> Black.pawn)
        }
        "A1 black LOAChecker, B1 white LOAChecker" in {
          read("10001000" :: "00001000" :: List.fill(62)(noop), LinesOfAction) must_== Map(A1 -> Black.loachecker, B1 -> White.loachecker)
        }
      }
    }
  }
}
