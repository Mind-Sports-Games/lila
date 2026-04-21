package lila.event

import reactivemongo.api.bson.*
import play.api.i18n.Lang

import lila.db.dsl.*

private[event] object BsonHandlers {

  implicit private val UserIdBsonHandler: BSONHandler[Event.UserId] =
    stringAnyValHandler[Event.UserId](_.value, Event.UserId.apply)

  implicit private val LangBsonHandler: BSONHandler[Lang] = stringAnyValHandler[Lang](_.code, Lang.apply)

  implicit val EventBsonHandler: BSONDocumentHandler[Event] = Macros.handler[Event]
}
