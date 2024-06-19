package lila.study

import akka.stream.scaladsl._
import strategygames.format.sgf.{ Dumper, Tag, Tags }
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
    format(chapter.setup.variant, actionStrs, tags)
  }

  def format(variant: Variant, actionStrs: ActionStrs, tags: Tags): String = {
    "(;" ++ tags.toString ++ "\n\n" ++ validSgf(variant, actionStrs) ++ ")"
  }

  def validSgf(variant: Variant, actionStrs: ActionStrs): String = {
    if (
      variant.gameLogic == GameLogic.FairySF() || variant.gameLogic == GameLogic
        .Go() || variant.gameLogic == GameLogic.Backgammon()
    ) {
      Dumper(variant, actionStrs)
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

  private def makeTags(study: Study, chapter: Chapter): Tags =
    Tags {
      List(
        Tag(_.FF, 4),
        Tag(_.CA, "UTF-8"),
        Tag(_.EV, s"${study.name}: ${chapter.name}"),
        Tag(_.PC, chapterUrl(study.id, chapter.id)),
        Tag(_.DT, Tag.DT.format.print(chapter.createdAt)),
        Tag(_.RE, "*")
      ) ::: (!chapter.root.fen.initial).??(
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
          case GameFamily.Go() =>
            List(
              Tag(_.GM, 1),
              Tag(_.SZ, chapter.setup.variant.toGo.boardSize.height),
              Tag(_.KM, chapter.root.fen.toGo.komi),
              Tag(_.HA, chapter.root.fen.toGo.handicap.getOrElse(0)),
              Tag(_.RU, "Chinese"),
              Tag(_.TB, chapter.root.fen.toGo.player1Score),
              Tag(_.TW, chapter.root.fen.toGo.player2Score)
            )
          case GameFamily.Backgammon() =>
            List(
              Tag(_.GM, 6),
              //Tag(_.RU, "Crawford"), // multipoint info
              Tag(_.CV, 1),
              Tag(_.CO, "n"),
              Tag.matchInfo(1, 1, 0, 0), // multipoint info
              Tag(_.SU, if (chapter.setup.variant.key == "backgammon") "Standard" else "Nackgammon")
            )
          case _ => List()
        }
      )
    }
}

object SgfDump {

  //TODO this doesn't work for multiaction games....
  def toActionStrs(line: Vector[Node]): ActionStrs = {
    line.map(n => Vector(n.move.uci.uci))
  }
}
