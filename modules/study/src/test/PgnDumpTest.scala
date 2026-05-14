package lila.study

// import strategygames.format.pgn._
// import strategygames.format.{ FEN, Uci, UciCharPair }
// import strategygames.variant.Variant
// import strategygames.GameLogic
// import Node._

class PgnDumpTest extends munit.FunSuite {

  /*
  implicit private val flags = PgnDump.WithFlags(true, true, true)

  val P = PgnDump

  def node(ply: Int, uci: String, san: String, children: Children = emptyChildren) =
    Node(
      id = UciCharPair(GameLogic.Chess(), Uci(GameLogic.Chess(), uci).get),
      ply = ply,
      move = Uci.WithSan(GameLogic.Chess(), Uci(GameLogic.Chess(), uci).get, san),
      fen = FEN(GameLogic.Chess(), "<fen>"),
      check = false,
      clock = None,
      pocketData = None,
      children = children,
      forceVariation = false
    )

  def children(nodes: Node*) = Children(nodes.toVector)

  val root = Node.Root.default(Variant.libStandard(GameLogic.Chess()))

  test("empty") {
    assert(P.toTurns(root).isEmpty)
  }

  test("one move") {
    val tree = root.copy(children = children(node(1, "e2e4", "e4")))
    P.toTurns(tree) match {
      case Vector(Turn(1, Some(move), None)) =>
        assertEquals(move.san, "e4")
        assert(move.variations.isEmpty)
      case _ => fail("unexpected shape")
    }
  }

  test("one move and variation") {
    val tree = root.copy(children =
      children(
        node(1, "e2e4", "e4"),
        node(1, "g1f3", "Nf3")
      )
    )
    P.toTurns(tree) match {
      case Vector(Turn(1, Some(move), None)) =>
        assertEquals(move.san, "e4")
        move.variations match {
          case List(List(Turn(1, Some(move), None))) =>
            assertEquals(move.san, "Nf3")
            assert(move.variations.isEmpty)
          case _ => fail("unexpected variations shape")
        }
      case _ => fail("unexpected shape")
    }
  }

  test("two moves and one variation") {
    val tree = root.copy(children =
      children(
        node(1, "e2e4", "e4", children(node(2, "d7d5", "d5"))),
        node(1, "g1f3", "Nf3")
      )
    )
    P.toTurns(tree) match {
      case Vector(Turn(1, Some(p1), Some(p2))) =>
        assertEquals(p1.san, "e4")
        p1.variations match {
          case List(List(Turn(1, Some(move), None))) =>
            assertEquals(move.san, "Nf3")
            assert(move.variations.isEmpty)
          case _ => fail("unexpected variations shape")
        }
        assertEquals(p2.san, "d5")
        assert(p2.variations.isEmpty)
      case _ => fail("unexpected shape")
    }
  }

  test("two moves and two variations") {
    val tree = root.copy(children =
      children(
        node(1, "e2e4", "e4", children(node(2, "d7d5", "d5"), node(2, "g8f6", "Nf6"))),
        node(1, "g1f3", "Nf3")
      )
    )
    assertEquals(P.toTurns(tree).mkString(" ").toString, "1. e4 (1. Nf3) 1... d5 (1... Nf6)")
    P.toTurns(tree) match {
      case Vector(Turn(1, Some(p1), Some(p2))) =>
        assertEquals(p1.san, "e4")
        p1.variations match {
          case List(List(Turn(1, Some(move), None))) =>
            assertEquals(move.san, "Nf3")
            assert(move.variations.isEmpty)
          case _ => fail("unexpected p1 variations shape")
        }
        assertEquals(p2.san, "d5")
        p2.variations match {
          case List(List(Turn(1, None, Some(move)))) =>
            assertEquals(move.san, "Nf6")
            assert(move.variations.isEmpty)
          case _ => fail("unexpected p2 variations shape")
        }
      case _ => fail("unexpected shape")
    }
  }

  test("more moves and variations") {
    val tree = root.copy(children =
      children(
        node(
          1, "e2e4", "e4",
          children(
            node(2, "d7d5", "d5", children(node(3, "a2a3", "a3"), node(3, "b2b3", "b3"))),
            node(2, "g8f6", "Nf6", children(node(3, "h2h4", "h4")))
          )
        ),
        node(
          1, "g1f3", "Nf3",
          children(
            node(2, "a7a6", "a6"),
            node(2, "b7b6", "b6", children(node(3, "c2c4", "c4")))
          )
        )
      )
    )
    assertEquals(
      P.toTurns(tree).mkString(" ").toString,
      "1. e4 (1. Nf3 a6 (1... b6 2. c4)) 1... d5 (1... Nf6 2. h4) 2. a3 (2. b3)"
    )
  }
   */
}
