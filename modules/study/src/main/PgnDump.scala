package lila.study

import akka.stream.scaladsl._
import strategygames.format.pgn.{ FullTurn, Glyphs, Initial, Pgn, Tag, Tags, Turn }
import strategygames.Player
import org.joda.time.format.DateTimeFormat

import lila.common.String.slugify
import lila.tree.Node.{ Shape, Shapes }
import lila.i18n.VariantKeys

final class PgnDump(
    chapterRepo: ChapterRepo,
    lightUserApi: lila.user.LightUserApi,
    net: lila.common.config.NetConfig
) {

  import PgnDump._

  def apply(study: Study, flags: WithFlags): Source[String, _] =
    chapterRepo
      .orderedByStudySource(study.id)
      .map(ofChapter(study, flags))
      .map(_.toString)
      .intersperse("\n\n\n")

  def ofChapter(study: Study, flags: WithFlags)(chapter: Chapter) =
    Pgn(
      tags = makeTags(study, chapter),
      fullTurns = toFullTurns(chapter.root)(flags).toList,
      initial = Initial(
        chapter.root.comments.list.map(_.text.value) ::: shapeComment(chapter.root.shapes).toList
      )
    )

  private val fileR = """[\s,]""".r

  def ownerName(study: Study) = lightUserApi.sync(study.ownerId).fold(study.ownerId)(_.name)

  def filename(study: Study): String = {
    val date = dateFormat.print(study.createdAt)
    fileR.replaceAllIn(
      s"playstrategy_study_${slugify(study.name.value)}_by_${ownerName(study)}_$date",
      ""
    )
  }

  def filename(study: Study, chapter: Chapter): String = {
    val date = dateFormat.print(chapter.createdAt)
    fileR.replaceAllIn(
      s"playstrategy_study_${slugify(study.name.value)}_${slugify(chapter.name.value)}_by_${ownerName(study)}_$date",
      ""
    )
  }

  private def chapterUrl(studyId: Study.Id, chapterId: Chapter.Id) =
    s"${net.baseUrl}/study/$studyId/$chapterId"

  private val dateFormat = DateTimeFormat forPattern "yyyy.MM.dd"

  private def annotatorTag(study: Study) =
    Tag(_.Annotator, s"https://playstrategy.org/@/${ownerName(study)}")

  private def makeTags(study: Study, chapter: Chapter): Tags =
    Tags {
      val opening = chapter.opening
      val genTags = List(
        Tag(_.Event, s"${study.name}: ${chapter.name}"),
        Tag(_.Site, chapterUrl(study.id, chapter.id)),
        Tag(_.UTCDate, Tag.UTCDate.format.print(chapter.createdAt)),
        Tag(_.UTCTime, Tag.UTCTime.format.print(chapter.createdAt)),
        Tag(_.Variant, VariantKeys.variantName(chapter.setup.variant).capitalize),
        Tag(_.ECO, opening.fold("?")(_.eco)),
        Tag(_.Opening, opening.fold("?")(_.name)),
        Tag(_.Result, "*") // required for SCID to import
      ) ::: List(annotatorTag(study)) ::: (!chapter.root.fen.initial).??(
        List(
          Tag(_.FEN, chapter.root.fen.value),
          Tag("SetUp", "1")
        )
      )
      genTags
        .foldLeft(chapter.tags.value.reverse) { case (tags, tag) =>
          if (tags.exists(t => tag.name == t.name)) tags
          else tag :: tags
        }
        .reverse
    }
}

object PgnDump {

  case class WithFlags(comments: Boolean, variations: Boolean, clocks: Boolean)

  private type Variations = Vector[Node]
  private val noVariations: Variations = Vector.empty

  //assume comments and info is stored on first node of turn
  private def nodes2Multiaction(nodes: Vector[Node], variations: Variations)(implicit flags: WithFlags) = {
    nodes.headOption.map { firstNode =>
      Turn(
        san = nodes.map(_.move.san).mkString(","),
        glyphs = if (flags.comments) firstNode.glyphs else Glyphs.empty,
        comments = flags.comments ?? {
          firstNode.comments.list.map(_.text.value) ::: shapeComment(firstNode.shapes).toList
        },
        opening = none,
        result = none,
        variations = flags.variations ?? {
          variations.view.map { child =>
            toFullTurns(child.mainline, noVariations).toList
          }.toList
        },
        secondsLeft = flags.clocks ?? firstNode.clock.map(_.roundSeconds)
      )
    }
  }

  // [%csl Gb4,Yd5,Rf6][%cal Ge2e4,Ye2d4,Re2g4]
  private def shapeComment(shapes: Shapes): Option[String] = {
    def render(as: String)(shapes: List[String]) =
      shapes match {
        case Nil    => ""
        case shapes => s"[%$as ${shapes.mkString(",")}]"
      }
    val circles = render("csl") {
      shapes.value.collect { case Shape.Circle(brush, orig) =>
        s"${brush.head.toUpper}$orig"
      }
    }
    val arrows = render("cal") {
      shapes.value.collect { case Shape.Arrow(brush, orig, dest) =>
        s"${brush.head.toUpper}$orig$dest"
      }
    }
    s"$circles$arrows".some.filter(_.nonEmpty)
  }

  def toFullTurn(first: Vector[Node], second: Option[Vector[Node]], variations: Variations)(implicit
      flags: WithFlags
  ) =
    FullTurn(
      fullTurnNumber = first.head.fullTurnCount,
      p1 = nodes2Multiaction(first, variations),
      p2 = second flatMap { nodes2Multiaction(_, first.head.children.variations) }
    )

  def toFullTurns(root: Node.Root)(implicit flags: WithFlags): Vector[FullTurn] =
    toFullTurns(root.mainline, root.children.variations)

  def toFullTurns(
      line: Vector[Node],
      variations: Variations
  )(implicit flags: WithFlags): Vector[FullTurn] = {
    val nodeTurns: Vector[Vector[Node]] = toNodeTurns(line)
    nodeTurns match {
      case Vector(Vector()) => Vector()
      case first +: rest if first.head.playedPlayerIndex == Player.P2 =>
        FullTurn(
          first.head.fullTurnCount,
          p1 = none,
          p2 = nodes2Multiaction(first, variations)
        ) +: toFullTurnsFromP1(
          rest,
          first.head.children.variations
        )
      case l => toFullTurnsFromP1(l, variations)
    }
  }.filterNot(_.isEmpty)

  def toFullTurnsFromP1(line: Vector[Vector[Node]], variations: Variations)(implicit
      flags: WithFlags
  ): Vector[FullTurn] =
    line
      .grouped(2)
      .foldLeft(variations -> Vector.empty[FullTurn]) { case ((variations, turns), pair) =>
        pair.headOption.fold(variations -> turns) { first =>
          pair
            .lift(1)
            .getOrElse(first)
            .head
            .children
            .variations -> (toFullTurn(first, pair lift 1, variations) +: turns)
        }
      }
      ._2
      .reverse

  def toNodeTurns(line: Vector[Node]): Vector[Vector[Node]] = {
    line
      .drop(1)
      .foldLeft(Vector(line.take(1))) { case (turn, node) =>
        if (turn.head.head.playedPlayerIndex != node.playedPlayerIndex) {
          Vector(node) +: turn
        } else {
          (turn.head :+ node) +: turn.tail
        }
      }
      .reverse
  }
}
