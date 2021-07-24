package lila.study

import strategygames.chess.opening._
import strategygames.format.FEN
import strategygames.variant.Variant
import strategygames.{ Game, GameLib }
import lila.tree

object TreeBuilder {

  private val initialStandardDests = Game(Variant.libStandard(GameLib.Chess())).situation.destinations

  def apply(root: Node.Root, variant: Variant): tree.Root = {
    val dests =
      if (variant.standard && root.fen.initial) initialStandardDests
      else {
        val sit = Game(GameLib.Chess(), variant.some, root.fen.some).situation
        sit.playable(false) ?? sit.destinations
      }
    makeRoot(root, variant).copy(dests = dests.some)
  }

  def toBranch(node: Node, variant: Variant): tree.Branch =
    tree.Branch(
      id = node.id,
      ply = node.ply,
      move = node.move,
      fen = node.fen,
      check = node.check,
      shapes = node.shapes,
      comments = node.comments,
      gamebook = node.gamebook,
      glyphs = node.glyphs,
      clock = node.clock,
      crazyData = node.crazyData,
      eval = node.score.map(_.eval),
      children = toBranches(node.children, variant),
      opening = Variant.openingSensibleVariants(GameLib.Chess())(variant) ?? (node.fen match {
        case FEN.Chess(fen) => FullOpeningDB findByFen fen
        case _ => sys.error("Invalid fen lib")
      }),
      forceVariation = node.forceVariation
    )

  def makeRoot(root: Node.Root, variant: Variant): tree.Root =
    tree.Root(
      ply = root.ply,
      fen = root.fen,
      check = root.check,
      shapes = root.shapes,
      comments = root.comments,
      gamebook = root.gamebook,
      glyphs = root.glyphs,
      clock = root.clock,
      crazyData = root.crazyData,
      eval = root.score.map(_.eval),
      children = toBranches(root.children, variant),
      opening = Variant.openingSensibleVariants(GameLib.Chess())(variant) ?? (root.fen match {
        case FEN.Chess(fen) => FullOpeningDB findByFen fen
        case _ => sys.error("Invalid fen lib")
      }),
    )

  private def toBranches(children: Node.Children, variant: Variant): List[tree.Branch] =
    children.nodes.view.map(toBranch(_, variant)).toList
}
