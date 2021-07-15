package lila.relay

import strategygames.format.pgn.Tags
import lila.study.{ Chapter, Node, PgnImport }

case class RelayGame(
    index: Int,
    tags: Tags,
    variant: strategygames.chess.variant.Variant,
    root: Node.Root,
    end: Option[PgnImport.End]
) {

  def staticTagsMatch(chapterTags: Tags): Boolean =
    RelayGame.staticTags forall { name =>
      chapterTags(name) == tags(name)
    }
  def staticTagsMatch(chapter: Chapter): Boolean = staticTagsMatch(chapter.tags)

  def isEmpty = tags.value.isEmpty && root.children.nodes.isEmpty

  lazy val looksLikePlayStrategy = tags(_.Site) exists { site =>
    RelayGame.playstrategyDomains exists { domain =>
      site startsWith s"https://$domain/"
    }
  }
}

private object RelayGame {

  val playstrategyDomains = List("playstrategy.org", "playstrategy.dev")

  val staticTags = List("white", "black", "round", "event", "site")
}
