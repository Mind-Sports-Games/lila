package lila.study

import akka.stream.scaladsl._
import strategygames.format.sgf.{ Dumper, Tag, Tags }
import strategygames.format.FEN
import strategygames.{ ActionStrs, GameFamily, GameLogic }
import strategygames.variant.Variant
import org.joda.time.format.DateTimeFormat

import lila.common.String.slugify
import lila.tree.Node.{ Shape, Shapes }
import lila.i18n.VariantKeys

final class SgfDump(
    chapterRepo: ChapterRepo,
    lightUserApi: lila.user.LightUserApi,
    net: lila.common.config.NetConfig
) {

  import SgfDump._

  def apply(study: Study): Source[String, _] =
    chapterRepo
      .orderedByStudySource(study.id)
      .map(ofChapter(study))
      .map(_.toString)
      .intersperse("\n\n\n")

  def ofChapter(study: Study)(chapter: Chapter): String = {
    val actionStrs = toActionStrs(chapter.root.mainline)
    val tags       = makeTags(study, chapter)
    val initialFen = !chapter.root.fen.initial option chapter.root.fen
    format(chapter.setup.variant, actionStrs, tags, initialFen)
  }

  def format(variant: Variant, actionStrs: ActionStrs, tags: Tags, initialFen: Option[FEN]): String = {
    "(;" ++ tags.toString ++ "\n\n" ++ validSgf(variant, actionStrs, initialFen) ++ ")"
  }

  def validSgf(variant: Variant, actionStrs: ActionStrs, initialFen: Option[FEN]): String = {
    if (
      variant.gameLogic == GameLogic.FairySF() || variant.gameLogic == GameLogic
        .Go() || variant.gameLogic == GameLogic.Backgammon()
    ) {
      Dumper(variant, actionStrs, initialFen)
    } else {
      "SGF NOT SUPPORTED"
    }
  }

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

  private def makeTags(study: Study, chapter: Chapter): Tags = {
    val isGo = chapter.setup.variant.gameFamily == GameFamily.Go()
    Tags {
      List(
        Tag(_.FF, 4),
        Tag(_.CA, "UTF-8"),
        Tag(_.EV, s"${study.name}: ${chapter.name}"),
        Tag(_.PC, chapterUrl(study.id, chapter.id)),
        Tag(_.DT, Tag.DT.format.print(chapter.createdAt))
      ) ::: (!chapter.root.fen.initial && !isGo).??(
        List(
          Tag(_.IP, chapter.root.fen.value)
        )
      ) ::: (
        chapter.setup.variant.gameFamily match {
          case GameFamily.LinesOfAction() => //not currently used
            List(
              Tag(_.GM, 9),
              Tag(_.SU, if (chapter.setup.variant.key == "linesOfAction") "Standard" else "Scrambled-eggs")
            )
          case GameFamily.Shogi() =>
            List(
              Tag(_.GM, 8),
              Tag(_.SZ, chapter.setup.variant.toFairySF.boardSize.height),
              Tag(_.SU, if (chapter.setup.variant.key == "shogi") "Standard" else "MiniShogi")
            )
          case GameFamily.Xiangqi() =>
            List(
              Tag(_.GM, 7),
              Tag(_.SZ, chapter.setup.variant.toFairySF.boardSize.height),
              Tag(_.SU, if (chapter.setup.variant.key == "xiangqi") "Standard" else "MiniXiangqi")
            )
          case GameFamily.Flipello() =>
            List(Tag(_.GM, 2), Tag(_.SZ, chapter.setup.variant.toFairySF.boardSize.height))
          case GameFamily.Amazons() => List(Tag(_.GM, 18))
          case GameFamily.BreakthroughTroyka() =>
            List(
              Tag(_.GM, 41),
              Tag(_.SZ, chapter.setup.variant.toFairySF.boardSize.height),
              Tag(
                _.SU,
                if (chapter.setup.variant.key == "breakthroughtroyka") "Standard" else "MiniBreakthrough"
              )
            )
          case GameFamily.Go() =>
            List(
              Tag(_.GM, 1),
              Tag(_.SZ, chapter.setup.variant.toGo.boardSize.height),
              Tag(_.KM, chapter.root.fen.toGo.komi),
              Tag(_.HA, chapter.root.fen.toGo.handicap.getOrElse(0)),
              Tag(_.RU, "Chinese")
            )
          case GameFamily.Backgammon() =>
            List(
              Tag(_.GM, 6),
              //Tag(_.RU, "Crawford"), // multipoint info
              Tag(_.CV, 1),
              Tag(_.CO, "n"),
              Tag.matchInfo(1, 1, 0, 0), // multipoint info
              Tag(
                _.SU,
                if (chapter.setup.variant.key == "backgammon") "Standard" else chapter.setup.variant.name
              )
            )
          case _ => List()
        }
      )
    }
  }
}

object SgfDump {

  def toActionStrs(line: Vector[Node]): ActionStrs = {
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
      .map(t => t.map(_.move.uci.uci))
  }
}
