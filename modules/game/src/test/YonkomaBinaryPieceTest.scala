package lila.game

import strategygames.Player
import strategygames.yonkoma.{ Board, Checker, Piece, Pos }
import strategygames.yonkoma.variant.{ Yonkoma10x10, Yonkoma12x12 }

class YonkomaBinaryPieceTest extends munit.FunSuite {

  private def roundTrip(pieces: Map[Pos, Piece]) =
    BinaryFormat.piece.readYonkoma(BinaryFormat.piece.writeYonkoma(pieces))

  test("an empty board stays empty") {
    assertEquals(roundTrip(Map.empty), Map.empty[Pos, Piece])
  }

  test("a checker is stored as its binaryInt, 1, with the owner in the high bit") {
    val pieces = Map(Pos.A1 -> Piece(Player.P1, Checker), Pos.B1 -> Piece(Player.P2, Checker))
    assertEquals(roundTrip(pieces), pieces)
    val bytes  = BinaryFormat.piece.writeYonkoma(pieces).value
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

  test("the initial position of either board round-trips in 144 bytes") {
    List(Yonkoma10x10, Yonkoma12x12).foreach { variant =>
      val pieces = Board.init(variant).pieces
      assertEquals(pieces.size, 4)
      assertEquals(BinaryFormat.piece.writeYonkoma(pieces).value.length, 144)
      assertEquals(roundTrip(pieces), pieces)
    }
  }
}
