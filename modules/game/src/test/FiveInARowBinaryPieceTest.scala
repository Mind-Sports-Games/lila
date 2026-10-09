package lila.game

import strategygames.Player
import strategygames.fiveinarow.{ BlackStone, Piece, Pos, WhiteStone }

class FiveInARowBinaryPieceTest extends munit.FunSuite {

  private def roundTrip(pieces: Map[Pos, Piece], blackSeat: Player) =
    BinaryFormat.piece.readFiveInARow(BinaryFormat.piece.writeFiveInARow(pieces), blackSeat)

  private val first = Pos.at(0, 0).get
  private val last  = Pos.at(14, 14).get

  test("an empty board stays empty") {
    assertEquals(roundTrip(Map.empty, Player.P1), Map.empty[Pos, Piece])
  }

  test("each stone keeps its colour, including on the last point") {
    val pieces = Map(first -> Piece(Player.P1, BlackStone), last -> Piece(Player.P2, WhiteStone))
    assertEquals(roundTrip(pieces, Player.P1), pieces)
  }

  test("once P2 plays black, every stone is read back as the other seat's") {
    val pieces = Map(first -> Piece(Player.P2, BlackStone), last -> Piece(Player.P1, WhiteStone))
    assertEquals(roundTrip(pieces, Player.P2), pieces)
  }
}
