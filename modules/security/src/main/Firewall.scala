package lila.security

import org.joda.time.DateTime
import play.api.mvc.RequestHeader
import reactivemongo.api.ReadPreference

import lila.common.IpAddress
import lila.db.BSON.BSONJodaDateTimeHandler
import lila.db.dsl.*

final class Firewall(
    coll: Coll,
    scheduler: akka.actor.Scheduler
)(implicit ec: scala.concurrent.ExecutionContext) {

  private var current: Set[String] = Set.empty

  val _ = scheduler.scheduleOnce(10 minutes)(loadFromDb.discard)

  def blocksIp(ip: IpAddress): Boolean = current contains ip.value

  def blocks(req: RequestHeader): Boolean = {
    val v = blocksIp {
      lila.common.HTTPRequest.ipAddress(req)
    }
    if (v) lila.mon.security.firewall.block.increment()
    v
  }

  def accepts(req: RequestHeader): Boolean = !blocks(req)

  def blockIps(ips: Iterable[IpAddress]): Funit =
    Future.sequence(ips.map { ip =>
      coll.update
        .one(
          $id(ip),
          $doc("_id" -> ip, "date" -> DateTime.now),
          upsert = true
        )
        .void
    }) >> loadFromDb

  def unblockIps(ips: Iterable[IpAddress]): Funit =
    coll.delete.one($inIds(ips)).void.andDo(loadFromDb.discard)

  private def loadFromDb: Funit =
    coll.distinctEasy[String, Set]("_id", $empty, ReadPreference.secondaryPreferred).map { ips =>
      current = ips
      val _ = lila.mon.security.firewall.ip.update(ips.size)
    }
}
