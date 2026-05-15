package lila.game


// import strategygames.chess._
// import strategygames.chess.Pos._
// import strategygames.chess.variant._
// import lila.db.ByteArray

class BinaryPieceTest extends munit.FunSuite {

  /*
  val noop = "00000000"
  def write(all: PieceMap): List[String] =
    (BinaryFormat.piece write all).showBytes.split(',').toList
  def read(bytes: List[String], variant: Variant = Standard): PieceMap =
    BinaryFormat.piece.read(ByteArray.parseBytes(bytes), variant)

  test("write - empty board") {
    assertEquals(write(Map.empty), List.fill(64)(noop))
  }
  test("write - A1 p1 king") {
    assertEquals(write(Map(A1 -> Piece(P1, King))), "00000001" :: List.fill(63)(noop))
  }
  test("write - A1 p2 knight") {
    assertEquals(write(Map(A1 -> Piece(P2, Knight))), "10000100" :: List.fill(63)(noop))
  }
  test("write - B1 p2 pawn") {
    assertEquals(write(Map(B1 -> Piece(P2, Pawn))), noop :: "10000110" :: List.fill(62)(noop))
  }
  test("write - A1 p2 knight, B1 p1 bishop") {
    assertEquals(
      write(Map(A1 -> Piece(P2, Knight), B1 -> Piece(P1, Bishop))),
      "10000100" :: "00000101" :: List.fill(62)(noop)
    )
  }
  test("write - A1 p2 knight, B1 p1 bishop, C1 p1 queen") {
    assertEquals(
      write(Map(A1 -> Piece(P2, Knight), B1 -> Piece(P1, Bishop), C1 -> Piece(P1, Queen))),
      "10000100" :: "00000101" :: "00000010" :: List.fill(61)(noop)
    )
  }
  test("write - H8 p2 knight") {
    assertEquals(write(Map(H8 -> Piece(P2, Knight))), List.fill(63)(noop) :+ "10000100")
  }
  test("write - G8 p2 knight, H8 p1 bishop") {
    assertEquals(
      write(Map(G8 -> Piece(P2, Knight), H8 -> Piece(P1, Bishop))),
      List.fill(62)(noop) :+ "10000100" :+ "00000101"
    )
  }
  test("write - A1 p2 LOAChecker, B1 p1 LOAChecker") {
    assertEquals(
      write(Map(A1 -> Piece(P2, LOAChecker), B1 -> Piece(P1, LOAChecker))),
      "10001000" :: "00001000" :: List.fill(62)(noop)
    )
  }
  test("read - empty board") {
    assertEquals(read(List.fill(64)(noop)), Map.empty)
  }
  test("read - A1 p1 king") {
    assertEquals(read("00000001" :: List.fill(63)(noop)), Map(A1 -> Piece(P1, King)))
  }
  test("read - B1 p2 pawn") {
    assertEquals(read(noop :: "10000110" :: List.fill(62)(noop)), Map(B1 -> Piece(P2, Pawn)))
  }
  test("read - A1 p2 LOAChecker, B1 p1 LOAChecker") {
    assertEquals(
      read("10001000" :: "00001000" :: List.fill(62)(noop), LinesOfAction),
      Map(A1 -> Piece(P2, LOAChecker), B1 -> Piece(P1, LOAChecker))
    )
  }
   */
}
