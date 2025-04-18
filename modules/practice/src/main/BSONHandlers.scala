package lila.practice

import reactivemongo.api.bson.{ BSONHandler, Macros }

import lila.db.dsl._
import lila.study.Chapter
import reactivemongo.api.bson.BSONDocumentHandler

object BSONHandlers {

  import PracticeProgress.{ ChapterNbMoves, NbMoves }

  implicit private val nbMovesHandler: BSONHandler[NbMoves] =
    isoHandler(PracticeProgress.nbMovesIso)
  implicit private val chapterNbMovesHandler: BSONHandler[ChapterNbMoves] =
    typedMapHandler[Chapter.Id, NbMoves](Chapter.idIso)

  implicit val practiceProgressIdHandler: BSONHandler[PracticeProgress.Id] =
    stringAnyValHandler[PracticeProgress.Id](_.value, PracticeProgress.Id.apply)
  implicit val practiceProgressHandler: BSONDocumentHandler[PracticeProgress] =
    Macros.handler[PracticeProgress]
}
