package views.html.user.show

import org.joda.time.DateTime

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.user.{ Trophy, TrophyKind }

import controllers.routes

object badges {

  def apply(info: lila.app.mashup.UserInfo)(implicit ctx: Context) =
    frag(
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

  private def awardCls(t: Trophy) = cls := s"trophy award ${t.kind._id} ${~t.kind.klass}"
}
