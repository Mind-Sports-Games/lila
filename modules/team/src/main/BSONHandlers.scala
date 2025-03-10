package lila.team

import reactivemongo.api.bson.Macros

import lila.hub.LeaderTeam
import reactivemongo.api.bson.BSONDocumentHandler

private object BSONHandlers {

  import lila.db.dsl.BSONJodaDateTimeHandler
  implicit val TeamBSONHandler: BSONDocumentHandler[Team]             = Macros.handler[Team]
  implicit val RequestBSONHandler: BSONDocumentHandler[Request]       = Macros.handler[Request]
  implicit val MemberBSONHandler: BSONDocumentHandler[Member]         = Macros.handler[Member]
  implicit val LeaderTeamBSONHandler: BSONDocumentHandler[LeaderTeam] = Macros.handler[LeaderTeam]
}
