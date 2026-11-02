package lila.bot

import scala.util.Success
import org.joda.time.DateTime
import reactivemongo.api.bson.*
import strategygames.ClockConfig
import strategygames.variant.Variant

import lila.db.dsl.*

final class BotStreamRepo(coll: Coll)(implicit ec: scala.concurrent.ExecutionContext) {

  import BotStreamRepo.*

  def all: Fu[List[BotStream]] =
    coll.find($empty).sort($doc("spotlight" -> -1, "name" -> 1)).cursor[BotStream]().list(100)

  def byId(id: BotStream.ID): Fu[Option[BotStream]] = coll.byId[BotStream](id)

  def byIdOrName(idOrName: String): Fu[Option[BotStream]] =
    coll.one[BotStream]($or($id(idOrName), $doc("name" -> idOrName)))

  def nameTaken(name: String, except: Option[BotStream.ID]): Fu[Boolean] =
    coll.exists($doc("name" -> name) ++ except.so(id => $doc("_id" -> $ne(id))))

  def insert(stream: BotStream): Funit = coll.insert.one(stream).void

  def update(stream: BotStream): Funit = coll.update.one($id(stream.id), stream).void

  def setActive(id: BotStream.ID, active: Boolean): Funit =
    coll.update.one($id(id), $set("active" -> active, "updatedAt" -> DateTime.now)).void

  def activateAllUnexpired: Funit =
    coll.update
      .one(
        $doc("active" -> false) ++ $or(
          $doc("until" -> $exists(false)),
          $doc("until" -> $gt(DateTime.now))
        ),
        $set("active" -> true, "updatedAt" -> DateTime.now),
        multi = true
      )
      .void

  def deactivateAll: Funit =
    coll.update
      .one($doc("active" -> true), $set("active" -> false, "updatedAt" -> DateTime.now), multi = true)
      .void

  def delete(id: BotStream.ID): Funit = coll.delete.one($id(id)).void
}

private object BotStreamRepo {

  implicit val variantsHandler: BSONHandler[List[Variant]] = tryHandler[List[Variant]](
    { case arr: BSONArray =>
      Success(arr.values.toList.collect { case BSONString(key) => key }.flatMap { key =>
        BotVsBotConfig.streamableVariant(key) orElse {
          logger.warn(s"Skipping unknown bot stream variant: $key")
          none
        }
      })
    },
    vs => BSONArray(vs.map(v => BSONString(v.key)))
  )

  implicit val clockHandler: BSONHandler[ClockConfig] = clockConfigHandler

  implicit val matchupHandler: BSONDocumentHandler[BotStream.Matchup] = Macros.handler[BotStream.Matchup]

  implicit val streamHandler: BSONDocumentHandler[BotStream] = Macros.handler[BotStream]
}
