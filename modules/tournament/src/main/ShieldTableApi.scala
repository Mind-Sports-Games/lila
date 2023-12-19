package lila.tournament

import org.joda.time.DateTime
import reactivemongo.api.bson._
import reactivemongo.api.ReadPreference

import lila.common.LightUser
import lila.common.Maths
import lila.common.config.MaxPerPage
import lila.common.paginator.Paginator
import lila.common.ThreadLocalRandom
import lila.db.dsl._
import lila.db.paginator.Adapter
import lila.rating.PerfType
import lila.user.User

final class ShieldTableApi(
    repo: ShieldTableRepo,
    leaderboardApi: LeaderboardApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  import ShieldTableApi._
  import BSONHandlers._

  private val maxPerPage = MaxPerPage(15)

  def byCategoryId(id: Int) =
    repo.coll
      .find(
        $doc(
          "c" -> id
        )
      )
      .sort($sort desc "p")
      .cursor[ShieldTableEntry]()
      .list()

  def clearRepo(category: Category) =
    byCategoryId(category.id) flatMap { entries =>
      (entries.nonEmpty ?? repo.coll.delete.one($inIds(entries.map(_.id))).void)
    }

  def insert(userPoints: Seq[ShieldTableEntry]) = userPoints.nonEmpty ??
    repo.coll.insert(ordered = false).many(userPoints).void

  def recalculate(category: Category): Funit =
    clearRepo(category).void >>
      leaderboardApi.shieldLeaderboardMetaPoints(2, category).flatMap { metaPoints =>
        insert(
          metaPoints
            .filter { case (u, _) => !LightUser.tourBotsIDs.contains(u) }
            .map { case (u, p) => ShieldTableEntry(ShieldTableEntry.makeId, u, category, p) }
            .toSeq
        )
      }

  def recalculateAll = Category.all.map(recalculate).sequenceFu.void

}

object ShieldTableApi {

  sealed trait Category {
    val id: Int
    val name: String
    val medleyShieldCode: String
  }

  object Category {

    case object Overall  extends Category {
      val id               = 0
      val name             = "Overall"
      // not used for overall all count
      val medleyShieldCode = "spm"
    }
    case object Chess    extends Category {
      val id               = 1
      val name             = "Chess"
      val medleyShieldCode = "scm"
    }
    case object Draughts extends Category {
      val id               = 2
      val name             = "Draughts"
      val medleyShieldCode = "sdm"
    }

    val all: List[Category] = List(
      Overall,
      Chess,
      Draughts
    )

    val allById: Map[Int, Category] = all map { c =>
      (c.id -> c)
    } toMap

    def getFromId(id: Int) = allById.getOrElse(id, Overall)

    def titleFromId(id: Int) = s"${getFromId(id).name} Shield Leaderboard"

    def restrictionGameFamily(id: Int) = if (id == 0) "" else s"${getFromId(id).name} "
  }

  case class ShieldTableEntry(
      id: ShieldTableEntry.ID,
      userId: User.ID,
      category: Category,
      points: Int
  )

  object ShieldTableEntry {

    type ID = String

    def makeId = ThreadLocalRandom nextString 8

  }

}
