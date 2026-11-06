package lila.game

import strategygames.Player
import strategygames.yonkoma.{ Board, Checker, Piece, Pos }
import strategygames.yonkoma.variant.{ Variant, Yonkoma10x10, Yonkoma12x12 }

class YonkomaBinaryPieceTest extends munit.FunSuite {

  private def roundTrip(pieces: Map[Pos, Piece], variant: Variant = Yonkoma12x12) =
    BinaryFormat.piece.readYonkoma(BinaryFormat.piece.writeYonkoma(pieces, variant), variant)

  test("an empty board stays empty") {
    assertEquals(roundTrip(Map.empty), Map.empty[Pos, Piece])
  }

  test("a checker is stored as its binaryInt, 1, with the owner in the high bit") {
    val pieces = Map(Pos.A1 -> Piece(Player.P1, Checker), Pos.B1 -> Piece(Player.P2, Checker))
    assertEquals(roundTrip(pieces), pieces)
    val bytes = BinaryFormat.piece.writeYonkoma(pieces, Yonkoma10x10).value
    assertEquals(bytes(0).toInt, 1)
    assertEquals(bytes(1).toInt & 0xff, 129)
    assertEquals(bytes(2).toInt, 0)
  }

  test("both owners survive, up to the last square of the 12x12 board") {
    val pieces = Map(
      Pos.A1  -> Piece(Player.P1, Checker),
      Pos.J10 -> Piece(Player.P1, Checker),
      Pos.A12 -> Piece(Player.P2, Checker),
      Pos.L12 -> Piece(Player.P2, Checker)
    )
    assertEquals(roundTrip(pieces), pieces)
  }

  test("a position takes one byte a square of its own board") {
    List(Yonkoma10x10 -> 100, Yonkoma12x12 -> 144).foreach { case (variant, bytes) =>
      val pieces = Board.init(variant).pieces
      assertEquals(pieces.size, 4)
      assertEquals(BinaryFormat.piece.writeYonkoma(pieces, variant).value.length, bytes)
      assertEquals(roundTrip(pieces, variant), pieces)
    }
    // the far corner of the 10x10 board is not the last square of the index space
    val corner = Map(Pos.A1 -> Piece(Player.P1, Checker), Pos.J10 -> Piece(Player.P1, Checker))
    assertEquals(roundTrip(corner, Yonkoma10x10), corner)
    assertEquals(BinaryFormat.piece.writeYonkoma(corner, Yonkoma10x10).value.last.toInt, 1)
  }
}
