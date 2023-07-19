package lila.study

import akka.stream.scaladsl._
import strategygames.format.pgn.{ FullTurn, Glyphs, Initial, Pgn, Tag, Tags, Turn }
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

  private def node2action(node: Node, variations: Variations)(implicit flags: WithFlags) =
    Turn(
      //TODO: Work out how to do nodes for multiaction. Do we need to mash the turn into one node?
      san = node.move.san,
      glyphs = if (flags.comments) node.glyphs else Glyphs.empty,
      comments = flags.comments ?? {
        node.comments.list.map(_.text.value) ::: shapeComment(node.shapes).toList
      },
      opening = none,
      result = none,
      variations = flags.variations ?? {
        variations.view.map { child =>
          toFullTurns(child.mainline, noVariations).toList
        }.toList
      },
      secondsLeft = flags.clocks ?? node.clock.map(_.roundSeconds)
    )

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

  //TODO this wont work for multiaction if each node is a singular ply
  def toFullTurn(first: Node, second: Option[Node], variations: Variations)(implicit flags: WithFlags) =
    FullTurn(
      fullTurnNumber = first.fullTurnCount,
      p1 = node2action(first, variations).some,
      p2 = second map { node2action(_, first.children.variations) }
    )

  def toFullTurns(root: Node.Root)(implicit flags: WithFlags): Vector[FullTurn] =
    toFullTurns(root.mainline, root.children.variations)

  def toFullTurns(
      line: Vector[Node],
      variations: Variations
  )(implicit flags: WithFlags): Vector[FullTurn] = {
    line match {
      case Vector() => Vector()
      case first +: rest if first.ply % 2 == 0 =>
        FullTurn(
          //TODO: Multiaction this is correct if turn, not ply is being used here
          fullTurnNumber = 1 + (first.ply - 1) / 2,
          p1 = none,
          p2 = node2action(first, variations).some
        ) +: toFullTurnsFromP1(rest, first.children.variations)
      case l => toFullTurnsFromP1(l, variations)
    }
  }.filterNot(_.isEmpty)

  def toFullTurnsFromP1(line: Vector[Node], variations: Variations)(implicit
      flags: WithFlags
  ): Vector[FullTurn] =
    line
      .grouped(2)
      .foldLeft(variations -> Vector.empty[FullTurn]) { case ((variations, turns), pair) =>
        pair.headOption.fold(variations -> turns) { first =>
          pair
            .lift(1)
            .getOrElse(first)
            .children
            .variations -> (toFullTurn(first, pair lift 1, variations) +: turns)
        }
      }
      ._2
      .reverse
}
