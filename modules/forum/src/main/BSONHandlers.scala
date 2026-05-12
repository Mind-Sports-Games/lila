package lila.forum

import lila.db.dsl.*
import reactivemongo.api.bson.*

private object BSONHandlers {

  implicit val CategBSONHandler: BSONDocumentHandler[Categ] = Macros.handler[Categ]

  implicit val PostEditBSONHandler: BSONDocumentHandler[OldVersion] = Macros.handler[OldVersion]
  implicit val PostBSONHandler: BSONDocumentHandler[Post]           = Macros.handler[Post]

  implicit val TopicBSONHandler: BSONDocumentHandler[Topic] = Macros.handler[Topic]
}
