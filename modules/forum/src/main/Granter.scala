package lila.forum

import lila.security.{ Permission, Granter => Master }
import lila.user.{ User, UserContext }

trait Granter {

  protected def userBelongsToTeam(teamId: String, userId: String): Fu[Boolean]
  protected def userOwnsTeam(teamId: String, userId: String): Fu[Boolean]

  def isGrantedWrite(categSlug: String)(implicit ctx: UserContext): Fu[Boolean] =
    ctx.me.filter(canForum) so { me =>
      Categ.slugToTeamId(categSlug).fold(fuTrue) { teamId =>
        userBelongsToTeam(teamId, me.id)
      }
    }

  private def canForum(u: User) =
    !u.isBot && {
      (u.count.game > 0 && u.createdSinceDays(2)) || u.hasTitle || u.isVerified || u.isPatron
    }

  def isGrantedMod(categSlug: String)(implicit ctx: UserContext): Fu[Boolean] =
    if (ctx.me so Master(Permission.ModerateForum)) fuTrue
    else
      Categ.slugToTeamId(categSlug) so { teamId =>
        ctx.userId so {
          userOwnsTeam(teamId, _)
        }
      }
}
