package lila.coordinate

import reactivemongo.api.bson.*
import reactivemongo.api.ReadPreference

import lila.user.User
import lila.db.dsl.*

import strategygames.Player as PlayerIndex

final class CoordinateApi(scoreColl: Coll)(implicit ec: scala.concurrent.ExecutionContext) {

  implicit private val scoreBSONHandler: BSONDocumentHandler[Score] = Macros.handler[Score]

  def getScore(userId: User.ID): Fu[Score] =
    scoreColl.byId[Score](userId) map (_ | Score(userId))

  def addScore(userId: User.ID, p1: Boolean, hits: Int): Funit =
    scoreColl.update
      .one(
        $id(userId),
        $push(
          $doc(
            "p1" -> BSONDocument(
              "$each"  -> (p1 so List(BSONInteger(hits))),
              "$slice" -> -20
            ),
            "p2" -> BSONDocument(
              "$each"  -> (!p1 so List(BSONInteger(hits))),
              "$slice" -> -20
            )
          )
        ),
        upsert = true
      )
      .void

  def bestScores(userIds: List[User.ID]): Fu[Map[User.ID, PlayerIndex.Map[Int]]] =
    scoreColl
      .aggregateList(
        maxDocs = Int.MaxValue,
        readPreference = ReadPreference.secondaryPreferred
      ) { framework =>
        import framework.*
        Match($doc("_id".$in(userIds))) -> List(
          Project(
            $doc(
              "p1" -> $doc("$max" -> "$p1"),
              "p2" -> $doc("$max" -> "$p2")
            )
          )
        )
      }
      .map {
        _.flatMap { doc =>
          doc.string("_id") map {
            _ -> PlayerIndex.Map(
              ~doc.int("p1"),
              ~doc.int("p2")
            )
          }
        }.toMap
      }
}
