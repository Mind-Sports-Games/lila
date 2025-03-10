package lila.relay

import reactivemongo.api.bson._

import lila.db.dsl._

object BSONHandlers {

  implicit val relayIdHandler: BSONHandler[RelayRound.Id] =
    stringAnyValHandler[RelayRound.Id](_.value, RelayRound.Id.apply)
  implicit val relayTourIdHandler: BSONHandler[RelayTour.Id] =
    stringAnyValHandler[RelayTour.Id](_.value, RelayTour.Id.apply)

  import RelayRound.Sync
  import Sync.{ Upstream, UpstreamIds, UpstreamUrl }
  implicit val upstreamUrlHandler: BSONDocumentHandler[UpstreamUrl] = Macros.handler[UpstreamUrl]
  implicit val upstreamIdsHandler: BSONDocumentHandler[UpstreamIds] = Macros.handler[UpstreamIds]

  implicit val upstreamHandler: BSONHandler[Upstream] = tryHandler[Upstream](
    {
      case d: BSONDocument if d.contains("url") => upstreamUrlHandler readTry d
      case d: BSONDocument if d.contains("ids") => upstreamIdsHandler readTry d
    },
    {
      case url: UpstreamUrl => upstreamUrlHandler.writeTry(url).get
      case ids: UpstreamIds => upstreamIdsHandler.writeTry(ids).get
    }
  )

  import SyncLog.Event
  implicit val syncLogEventHandler: BSONDocumentHandler[Event] = Macros.handler[Event]

  implicit val syncLogHandler: BSONHandler[SyncLog] =
    isoHandler[SyncLog, Vector[Event]]((s: SyncLog) => s.events, SyncLog.apply _)

  implicit val syncHandler: BSONDocumentHandler[Sync] = Macros.handler[Sync]

  implicit val relayHandler: BSONDocumentHandler[RelayRound] = Macros.handler[RelayRound]

  implicit val relayTourHandler: BSONDocumentHandler[RelayTour] = Macros.handler[RelayTour]

  def readRoundWithTour(doc: Bdoc): Option[RelayRound.WithTour] = for {
    round <- doc.asOpt[RelayRound]
    tour  <- doc.getAsOpt[RelayTour]("tour")
  } yield RelayRound.WithTour(round, tour)
}
