package lila.forum

import lila.hub.LightTeam.TeamID
import lila.user.User

case class Categ(
    _id: String, // slug
    name: String,
    desc: String,
    team: Option[TeamID] = None,
    nbTopics: Int,
    nbPosts: Int,
    lastPostId: String,
    nbTopicsTroll: Int,
    nbPostsTroll: Int,
    lastPostIdTroll: String,
    quiet: Boolean = false
) {

  def id = _id

  def nbTopics(forUser: Option[User]): Int = if forUser.exists(_.marks.troll) then nbTopicsTroll else nbTopics
  def nbPosts(forUser: Option[User]): Int  = if forUser.exists(_.marks.troll) then nbPostsTroll else nbPosts
  def lastPostId(forUser: Option[User]): String =
    if forUser.exists(_.marks.troll) then lastPostIdTroll else lastPostId

  def isTeam = team.nonEmpty

  def withPost(topic: Topic, post: Post): Categ =
    copy(
      nbTopics = if post.troll then nbTopics else nbTopics + 1,
      nbPosts = if post.troll then nbPosts else nbPosts + 1,
      lastPostId = if post.troll || topic.isTooBig then lastPostId else post.id,
      nbTopicsTroll = nbTopicsTroll + 1,
      nbPostsTroll = nbPostsTroll + 1,
      lastPostIdTroll = if topic.isTooBig then lastPostIdTroll else post.id
    )

  def slug = id
}

object Categ {

  def isTeamSlug(slug: String) = slug.startsWith("team-")

  def slugToTeamId(slug: String) = isTeamSlug(slug).option(slug.drop(5))
}
