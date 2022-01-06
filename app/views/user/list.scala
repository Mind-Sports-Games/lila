package views.html
package user

import play.api.i18n.Lang

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.rating.PerfType
import lila.user.User

import controllers.routes

object list {

  def apply(
      tourneyWinners: List[lila.tournament.Winner],
      online: List[User],
      leaderboards: lila.user.Perfs.Leaderboards,
      nbAllTime: List[User.LightCount]
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.players.txt(),
      moreCss = cssTag("user.list"),
      wrapClass = "full-screen-force",
      openGraph = lila.app.ui
        .OpenGraph(
          title = "Chess players and leaderboards",
          url = s"$netBaseUrl${routes.User.list.url}",
          description =
            "Best chess players in bullet, blitz, rapid, classical, Chess960 and more chess variants"
        )
        .some
    ) {
      main(cls := "page-menu")(
        bits.communityMenu("leaderboard"),
        div(cls := "community page-menu__content box box-pad")(
          st.section(cls := "community__online")(
            h2(trans.onlinePlayers()),
            ol(cls := "user-top")(online map { u =>
              li(
                userLink(u),
                showBestPerf(u)
              )
            })
          ),
          div(cls := "community__leaders")(
            h2(trans.leaderboard()),
            div(cls := "leaderboards")(
              userTopPerf(leaderboards.bullet,      PerfType.orDefault("bullet")),
              userTopPerf(leaderboards.blitz,       PerfType.orDefault("blitz")),
              userTopPerf(leaderboards.rapid,       PerfType.orDefault("rapid")),
              userTopPerf(leaderboards.classical,   PerfType.orDefault("classical")),
              userTopPerf(leaderboards.ultraBullet, PerfType.orDefault("ultraBullet")),
              userTopActive(nbAllTime, trans.activePlayers(), icon = 'U'.some),
              tournamentWinners(tourneyWinners),
              userTopPerf(leaderboards.crazyhouse,    PerfType.orDefault("crazyhouse")),
              userTopPerf(leaderboards.chess960,      PerfType.orDefault("chess960")),
              userTopPerf(leaderboards.antichess,     PerfType.orDefault("antichess")),
              userTopPerf(leaderboards.atomic,        PerfType.orDefault("atomic")),
              userTopPerf(leaderboards.threeCheck,    PerfType.orDefault("threeCheck")),
              userTopPerf(leaderboards.fiveCheck,     PerfType.orDefault("fiveCheck")),
              userTopPerf(leaderboards.kingOfTheHill, PerfType.orDefault("kingOfTheHill")),
              userTopPerf(leaderboards.horde,         PerfType.orDefault("horde")),
              userTopPerf(leaderboards.racingKings,   PerfType.orDefault("racingKings")),
              userTopPerf(leaderboards.linesOfAction, PerfType.orDefault("linesOfAction")),
              userTopPerf(leaderboards.international, PerfType.orDefault("international")),
              userTopPerf(leaderboards.frisian,       PerfType.orDefault("frisian")),
              userTopPerf(leaderboards.antidraughts,  PerfType.orDefault("antidraughts")),
              userTopPerf(leaderboards.breakthrough,  PerfType.orDefault("breakthrough")),
              userTopPerf(leaderboards.frysk,         PerfType.orDefault("frysk")),
              userTopPerf(leaderboards.russian,       PerfType.orDefault("russian")),
              userTopPerf(leaderboards.brazilian,     PerfType.orDefault("brazilian")),
              userTopPerf(leaderboards.pool,          PerfType.orDefault("pool")),
              userTopPerf(leaderboards.shogi,         PerfType.orDefault("shogi")),
              userTopPerf(leaderboards.xiangqi,       PerfType.orDefault("xiangqi"))
            )
          )
        )
      )
    }

  private def tournamentWinners(winners: List[lila.tournament.Winner])(implicit ctx: Context) =
    st.section(cls := "user-top")(
      h2(cls := "text", dataIcon := "g")(
        a(href := routes.Tournament.leaderboard)(trans.tournament())
      ),
      ol(winners take 10 map { w =>
        li(
          userIdLink(w.userId.some),
          a(title := w.tourName, href := routes.Tournament.show(w.tourId))(
            scheduledTournamentNameShortHtml(w.tourName)
          )
        )
      })
    )

  private def userTopPerf(users: List[User.LightPerf], perfType: PerfType)(implicit lang: Lang) =
    st.section(cls := "user-top")(
      h2(cls := "text", dataIcon := perfType.iconChar)(
        a(href := routes.User.topNb(200, perfType.key))(perfType.trans)
      ),
      ol(users map { l =>
        li(
          lightUserLink(l.user),
          l.rating
        )
      })
    )

  private def userTopActive(users: List[User.LightCount], hTitle: Frag, icon: Option[Char])(implicit
      ctx: Context
  ) =
    st.section(cls := "user-top")(
      h2(cls := "text", dataIcon := icon.map(_.toString))(hTitle),
      ol(users map { u =>
        li(
          lightUserLink(u.user),
          span(title := trans.gamesPlayed.txt())(s"#${u.count.localize}")
        )
      })
    )
}
