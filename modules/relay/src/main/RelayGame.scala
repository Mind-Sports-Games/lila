package lila.relay

import strategygames.GameLogic
import strategygames.format.pgn.{ Tag, Tags }
import lila.study.{ Chapter, Node, PgnImport }

case class RelayGame(
    index: Int,
    tags: Tags,
    variant: strategygames.variant.Variant,
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
      site.startsWith(s"https://$domain/")
    }
  }
}

private object RelayGame {

  val playstrategyDomains = List("playstrategy.org", "playstrategy.dev")

  val staticTags = List("p1", "p2", "round", "event", "site")

  val unsupportedVariant = "Broadcasts currently support chess only"

  private val variantTagRegex = """(?im)^\[\s*(Variant|GameType)\s+"([^"]*)"\s*\]""".r

  private val playerTagRegex = """(?m)^\[(White|Black)(Elo|Title|Team)?(\s+")""".r

  /* Upstream sources publish standard chess PGN, which names the players with
   * White and Black. PlayStrategy calls them P1 and P2, and PgnTags drops every
   * tag it does not recognise, so leaving them alone loses the player names. */
  def withPlayStrategyPlayerTags(pgn: String): String =
    playerTagRegex.replaceAllIn(
      pgn,
      m =>
        java.util.regex.Matcher.quoteReplacement(
          s"[${if (m.group(1) == "White") "P1" else "P2"}${Option(m.group(2)) | ""}${m.group(3)}"
        )
    )

  /* The PGN import chain resolves variants through the chess-only lila.importer,
   * which throws rather than returning an error for any other game logic.
   * Read the variant tags up front so an unsupported source fails as a sync log
   * message instead of an exception. */
  def isChessOnly(pgn: String): Boolean =
    Tags(
      variantTagRegex.findAllMatchIn(pgn).map(m => Tag(m.group(1), m.group(2))).toList
    ).variant.forall(_.gameLogic == GameLogic.Chess())
}
