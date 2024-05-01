package lila.team

import org.joda.time.DateTime
import scala.util.chaining._

import lila.user.User
import org.joda.time.Days

case class Team(
    _id: Team.ID, // also the url slug
    name: String,
    location: Option[String],
    password: Option[String],
    description: String,
    descPrivate: Option[String],
    nbMembers: Int,
    enabled: Boolean,
    open: Boolean,
    createdAt: DateTime,
    createdBy: User.ID,
    leaders: Set[User.ID],
    chat: Team.ChatFor,
    hideMembers: Option[Boolean],
    hideForum: Option[Boolean]
) {

  def id = _id

  def slug = id

  def disabled = !enabled

  def isChatFor(f: Team.ChatFor.type => Team.ChatFor) =
    chat == f(Team.ChatFor)

  def publicMembers: Boolean = !hideMembers.has(true)

  def publicForum: Boolean = !hideForum.has(true)

  def isOfficial: Boolean = createdBy == "playstrategy"

}

object Team {

  case class Mini(id: Team.ID, name: String)

  val maxJoinCeiling = 50

  def maxJoin(u: User) =
    if (u.isVerified) maxJoinCeiling * 2
    else {
      15 + Days.daysBetween(u.createdAt, DateTime.now).getDays / 7
    } atMost maxJoinCeiling

  type ID = String

  type ChatFor = Int
  object ChatFor {
    val NONE    = 0
    val LEADERS = 10
    val MEMBERS = 20
    val all     = List(NONE, LEADERS, MEMBERS)
  }

  case class IdsStr(value: String) extends AnyVal {

    import IdsStr.separator

    def contains(teamId: ID) =
      value == teamId ||
        value.startsWith(s"$teamId$separator") ||
        value.endsWith(s"$separator$teamId") ||
        value.contains(s"$separator$teamId$separator")

    def toArray: Array[String] = value split IdsStr.separator
    def toList                 = value.nonEmpty ?? toArray.toList
  }

  object IdsStr {

    private val separator = ' '

    val empty = IdsStr("")

    def apply(ids: Iterable[ID]): IdsStr = IdsStr(ids mkString separator.toString)
  }

  def make(
      id: String,
      name: String,
      location: Option[String],
      password: Option[String],
      description: String,
      descPrivate: Option[String],
      open: Boolean,
      createdBy: User,
      hideMembers: Option[Boolean],
      hideForum: Option[Boolean]
  ): Team =
    new Team(
      _id = id,
      name = name,
      location = location,
      password = password,
      description = description,
      descPrivate = descPrivate,
      nbMembers = 1,
      enabled = true,
      open = open,
      createdAt = DateTime.now,
      createdBy = createdBy.id,
      leaders = Set(createdBy.id),
      chat = ChatFor.MEMBERS,
      hideMembers = hideMembers,
      hideForum = hideForum
    )

  def nameToId(name: String) =
    (lila.common.String slugify name) pipe { slug =>
      // if most chars are not latin, go for random slug
      if (slug.lengthIs > (name.lengthIs / 2)) slug else randomId()
    }

  private[team] def randomId() = lila.common.ThreadLocalRandom nextString 8
}
