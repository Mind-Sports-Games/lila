package lila.study

import strategygames.opening.FullOpeningDB
import strategygames.format.FEN
import strategygames.variant.Variant
import strategygames.{ Game, GameLogic, Situation }
import lila.tree

object TreeBuilder {

  private def initialStandardDests(lib: GameLogic) =
    Game(lib, Variant.libStandard(lib)).situation.destinations

  def apply(root: Node.Root, variant: Variant): tree.Root = {
    val dests =
      if (variant.standard && root.fen.initial) initialStandardDests(variant.gameLogic)
      else {
        val sit = Game(variant.gameLogic, variant.some, root.fen.some).situation
        sit.playable(false) ?? sit.destinations
      }
    val dropsByRole = {
      val sit = Game(variant.gameLogic, variant.some, root.fen.some).situation
      sit.playable(false) ?? sit.dropsByRole
    }
    makeRoot(root, variant).copy(dests = dests.some, dropsByRole = dropsByRole)
  }

  def toBranch(node: Node, variant: Variant): tree.Branch = {
    val g = Game(variant.gameLogic, variant.some, node.fen.some)
    tree.Branch(
      id = node.id,
      ply = node.ply,
      variant = variant,
      move = node.move,
      fen = node.fen,
      check = node.check,
      shapes = node.shapes,
      comments = node.comments,
      gamebook = node.gamebook,
      glyphs = node.glyphs,
      clock = node.clock,
      pocketData = node.pocketData,
      eval = node.score.map(_.eval),
      children = toBranches(node.children, variant),
      opening = Variant.openingSensibleVariants(variant.gameLogic)(variant) ?? (
        FullOpeningDB.findByFen(variant.gameLogic, node.fen)
      ),
      forceVariation = node.forceVariation,
      dropsByRole = Game(variant.gameLogic, variant.some, node.fen.some).situation.dropsByRole
    )
  }

  def makeRoot(root: Node.Root, variant: Variant): tree.Root = {
    tree.Root(
      ply = root.ply,
      variant = variant,
      fen = root.fen,
      check = root.check,
      shapes = root.shapes,
      comments = root.comments,
      gamebook = root.gamebook,
      glyphs = root.glyphs,
      clock = root.clock,
      pocketData = root.pocketData,
      eval = root.score.map(_.eval),
      children = toBranches(root.children, variant),
      opening = Variant.openingSensibleVariants(variant.gameLogic)(variant) ?? (
        FullOpeningDB.findByFen(variant.gameLogic, root.fen)
      ),
      dropsByRole = Game(variant.gameLogic, variant.some, root.fen.some).situation.dropsByRole
    )
  }

  private def toBranches(children: Node.Children, variant: Variant): List[tree.Branch] =
    children.nodes.view.map(toBranch(_, variant)).toList
}
