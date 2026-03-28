package lila.appeal

import lila.db.dsl._
import reactivemongo.api.bson._

private[appeal] object BsonHandlers {

  import Appeal.Status

  implicit val statusHandler: BSONHandler[Status] = lila.db.dsl.quickHandler[Status](
    {
      case BSONString(v) => Status(v) | Status.Read
      case _             => Status.Read
    },
    s => BSONString(s.key)
  )

  implicit val appealMsgHandler: BSONDocumentHandler[AppealMsg] = Macros.handler[AppealMsg]
  implicit val appealHandler: BSONDocumentHandler[Appeal]       = Macros.handler[Appeal]
}
