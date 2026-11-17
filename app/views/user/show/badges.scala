package views.html.user.show

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.progress.{ Badge, BadgeGroup, Progress, ProgressIcon }
import lila.user.{ Trophy, User }

object badges {

  def apply(info: lila.app.mashup.UserInfo)(implicit ctx: Context) =
    frag(
      progressBadges(info.user, info.progress),
      info.trophies.filter(_.kind.klass.has("icon3d")).sorted.map { trophy =>
        trophy.kind.icon.map { iconChar =>
          a(
            awardCls(trophy),
            href := trophy.kind.url,
            ariaTitle(trophy.kind.name)
          )(raw(iconChar))
        }
      },
      /*info.isCoach option
        a(
          href := routes.Coach.show(info.user.username),
          cls := "trophy award icon3d coach",
          ariaTitle(trans.coach.playstrategyCoach.txt())
        )(":"),*/
      (info.isStreamer && ctx.noKid) option {
        val streaming = isStreaming(info.user.id)
        views.html.streamer.bits.redirectLink(info.user.username, streaming.some)(
          cls := List(
            "trophy award icon3d streamer" -> true,
            "streaming"                    -> streaming
          ),
          ariaTitle(if (streaming) "Live now!" else "PlayStrategy Streamer")
        )("")
      }
    )

  private def progressBadges(u: User, progress: Progress)(implicit ctx: Context) =
    BadgeGroup.all.flatMap { group =>
      progress.awarded.filter(_._1.group == group).map { case (badge, award) =>
        a(
          href := ctx.is(u).option(routes.User.progress(u.username).url),
          cls  := s"trophy award icon3d progress-badge progress-badge--${badge.key} level-${award.level}",
          ariaTitle(s"${groupName(group)}: ${badgeName(badge, award.level)} · ${showDate(award.at)}")
        )(badgeIcon(badge, award.level))
      }
    }

  private def groupName(group: BadgeGroup)(implicit ctx: Context): String =
    group match {
      case BadgeGroup.Onboarding   => trans.onboarding.badgeGroupOnboarding.txt()
      case BadgeGroup.Achievements => trans.onboarding.achievements.txt()
    }

  private def badgeIcon(badge: Badge, level: Int): String =
    ProgressIcon(badge, level).toString

  private def badgeName(badge: Badge, level: Int)(implicit ctx: Context): String =
    badge match {
      case Badge.Onboarded if level >= 2 => trans.onboarding.badgeFullyOnboarded.txt()
      case Badge.Onboarded               => trans.onboarding.badgeOnboarded.txt()
      case Badge.AllVariants             => trans.onboarding.badgeAllVariants.txt()
    }

  private def awardCls(t: Trophy) = cls := s"trophy award ${t.kind._id} ${~t.kind.klass}"
}
