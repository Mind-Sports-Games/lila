package lila.game

import strategygames.Player
import strategygames.entropy.{ Black, Piece, Pos, Purple, White }

class EntropyBinaryPieceTest extends munit.FunSuite {

  private def roundTrip(pieces: Map[Pos, Piece]) =
    BinaryFormat.piece.readEntropy(BinaryFormat.piece.writeEntropy(pieces))

  test("an empty board stays empty") {
    assertEquals(roundTrip(Map.empty), Map.empty[Pos, Piece])
  }

  test("a white counter held by P1 is not mistaken for an empty square") {
    val pieces = Map(Pos.A1 -> Piece(Player.P1, White))
    assertEquals(roundTrip(pieces), pieces)
  }

  test("every colour and owner survives, including the last square") {
    val pieces = Map(
      Pos.A1 -> Piece(Player.P2, White),
      Pos.D4 -> Piece(Player.P1, Black),
      Pos.G7 -> Piece(Player.P2, Purple)
    )
    assertEquals(roundTrip(pieces), pieces)
  }
}
