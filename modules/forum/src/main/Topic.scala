package lila.forum

import org.joda.time.DateTime
import lila.common.ThreadLocalRandom

import lila.user.User

case class Topic(
    _id: Topic.ID,
    categId: String,
    slug: String,
    name: String,
    createdAt: DateTime,
    updatedAt: DateTime,
    nbPosts: Int,
    lastPostId: String,
    updatedAtTroll: DateTime,
    nbPostsTroll: Int,
    lastPostIdTroll: String,
    troll: Boolean,
    closed: Boolean,
    hidden: Boolean,
    sticky: Option[Boolean],
    userId: Option[String] = None // only since SB mutes
) {

  def id = _id

  def updatedAt(forUser: Option[User]): DateTime =
    if forUser.exists(_.marks.troll) then updatedAtTroll else updatedAt
  def nbPosts(forUser: Option[User]): Int   = if forUser.exists(_.marks.troll) then nbPostsTroll else nbPosts
  def nbReplies(forUser: Option[User]): Int = nbPosts(forUser) - 1
  def lastPostId(forUser: Option[User]): String =
    if forUser.exists(_.marks.troll) then lastPostIdTroll else lastPostId

  def open          = !closed
  def visibleOnHome = !hidden

  def isTooBig = nbPosts > (if Categ.isTeamSlug(categId) then 500 else 50)

  def looksLikeTeamForum = Categ.isTeamSlug(categId)

  def isSticky = ~sticky

  def withPost(post: Post): Topic =
    copy(
      nbPosts = if post.troll then nbPosts else nbPosts + 1,
      lastPostId = if post.troll then lastPostId else post.id,
      updatedAt = if isTooBig || post.troll then updatedAt else post.createdAt,
      nbPostsTroll = nbPostsTroll + 1,
      lastPostIdTroll = post.id,
      updatedAtTroll = if isTooBig then updatedAt else post.createdAt
    )

  def incNbPosts = copy(nbPosts = nbPosts + 1)

  def isOld = updatedAt.isBefore(DateTime.now.minusMonths(3))
}

object Topic {

  type ID = String

  def nameToId(name: String) =
    (lila.common.String.slugify(name)) pipe { slug =>
      // if most chars are not latin, go for random slug
      if slug.lengthIs > (name.lengthIs / 2) then slug else ThreadLocalRandom.nextString(8)
    }

  val idSize = 8

  def make(
      categId: String,
      slug: String,
      name: String,
      userId: User.ID,
      troll: Boolean,
      hidden: Boolean
  ): Topic =
    Topic(
      _id = ThreadLocalRandom.nextString(idSize),
      categId = categId,
      slug = slug,
      name = name,
      createdAt = DateTime.now,
      updatedAt = DateTime.now,
      nbPosts = 0,
      lastPostId = "",
      updatedAtTroll = DateTime.now,
      nbPostsTroll = 0,
      lastPostIdTroll = "",
      troll = troll,
      userId = userId.some,
      closed = false,
      hidden = hidden,
      sticky = None
    )
}
