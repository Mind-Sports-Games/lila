package views.html.user.show

import controllers.routes
import play.api.i18n.Lang

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.rating.PerfType
import lila.user.User

object side {

  def apply(
      u: User,
      rankMap: lila.rating.UserRankMap,
      active: Option[lila.rating.PerfType]
  )(implicit ctx: Context) = {

    def showNonEmptyPerf(perf: lila.rating.Perf, perfType: PerfType) =
      perf.nonEmpty option showPerf(perf, perfType)

    def showPerf(perf: lila.rating.Perf, perfType: PerfType) = {
      val isPuzzle = perfType.key == "puzzle"
      a(
        dataIcon := perfType.iconChar,
        title := perfType.desc,
        cls := List(
          "empty"  -> perf.isEmpty,
          "active" -> active.has(perfType)
        ),
        href := {
          if (isPuzzle) ctx.is(u) option routes.Puzzle.dashboard(30, "home").url
          else routes.User.perfStat(u.username, perfType.key).url.some
        },
        span(
          h3(perfType.trans),
          if (isPuzzle && u.perfs.dubiousPuzzle && !ctx.is(u)) st.rating("?")
          else
            st.rating(
              if (perf.glicko.clueless) strong("?")
              else
                strong(
                  perf.glicko.intRating,
                  perf.provisional option "?"
                ),
              " ",
              ratingProgress(perf.progress),
              " ",
              span(
                if (perfType.key == "puzzle") trans.nbPuzzles(perf.nb, perf.nb.localize)
                else trans.nbGames(perf.nb, perf.nb.localize)
              )
            ),
          rankMap get perfType map { rank =>
            span(cls := "rank", title := trans.rankIsUpdatedEveryNbMinutes.pluralSameTxt(15))(
              trans.rankX(rank.localize)
            )
          }
        ),
        iconTag("G")
      )
    }

    div(cls := "side sub-ratings")(
      (!u.lame || ctx.is(u) || isGranted(_.UserModView)) option frag(
        showNonEmptyPerf(u.perfs.ultraBullet, PerfType.orDefaultSpeed("ultraBullet")),
        showPerf(u.perfs.bullet, PerfType.orDefaultSpeed("bullet")),
        showPerf(u.perfs.blitz, PerfType.orDefaultSpeed("blitz")),
        showPerf(u.perfs.rapid, PerfType.orDefaultSpeed("rapid")),
        showPerf(u.perfs.classical, PerfType.orDefaultSpeed("classical")),
        showPerf(u.perfs.correspondence, PerfType.orDefaultSpeed("correspondence")),
        u.hasVariantRating option hr,
        showNonEmptyPerf(u.perfs.crazyhouse, PerfType.orDefault("crazyhouse")),
        showNonEmptyPerf(u.perfs.chess960, PerfType.orDefault("chess960")),
        showNonEmptyPerf(u.perfs.kingOfTheHill, PerfType.orDefault("kingOfTheHill")),
        showNonEmptyPerf(u.perfs.threeCheck, PerfType.orDefault("threeCheck")),
        showNonEmptyPerf(u.perfs.fiveCheck, PerfType.orDefault("fiveCheck")),
        showNonEmptyPerf(u.perfs.antichess, PerfType.orDefault("antichess")),
        showNonEmptyPerf(u.perfs.atomic, PerfType.orDefault("atomic")),
        showNonEmptyPerf(u.perfs.horde, PerfType.orDefault("horde")),
        showNonEmptyPerf(u.perfs.racingKings, PerfType.orDefault("racingKings")),
        showNonEmptyPerf(u.perfs.noCastling, PerfType.orDefault("noCastling")),
        showNonEmptyPerf(u.perfs.monster, PerfType.orDefault("monster")),
        showNonEmptyPerf(u.perfs.linesOfAction, PerfType.orDefault("linesOfAction")),
        showNonEmptyPerf(u.perfs.scrambledEggs, PerfType.orDefault("scrambledEggs")),
        showNonEmptyPerf(u.perfs.international, PerfType.orDefault("international")),
        showNonEmptyPerf(u.perfs.frisian, PerfType.orDefault("frisian")),
        showNonEmptyPerf(u.perfs.frysk, PerfType.orDefault("frysk")),
        showNonEmptyPerf(u.perfs.antidraughts, PerfType.orDefault("antidraughts")),
        showNonEmptyPerf(u.perfs.breakthrough, PerfType.orDefault("breakthrough")),
        showNonEmptyPerf(u.perfs.russian, PerfType.orDefault("russian")),
        showNonEmptyPerf(u.perfs.brazilian, PerfType.orDefault("brazilian")),
        showNonEmptyPerf(u.perfs.pool, PerfType.orDefault("pool")),
        showNonEmptyPerf(u.perfs.portuguese, PerfType.orDefault("portuguese")),
        showNonEmptyPerf(u.perfs.english, PerfType.orDefault("english")),
        showNonEmptyPerf(u.perfs.shogi, PerfType.orDefault("shogi")),
        showNonEmptyPerf(u.perfs.xiangqi, PerfType.orDefault("xiangqi")),
        showNonEmptyPerf(u.perfs.minishogi, PerfType.orDefault("minishogi")),
        showNonEmptyPerf(u.perfs.minixiangqi, PerfType.orDefault("minixiangqi")),
        showNonEmptyPerf(u.perfs.flipello, PerfType.orDefault("flipello")),
        showNonEmptyPerf(u.perfs.flipello10, PerfType.orDefault("flipello10")),
        showNonEmptyPerf(u.perfs.amazons, PerfType.orDefault("amazons")),
        showNonEmptyPerf(u.perfs.oware, PerfType.orDefault("oware")),
        showNonEmptyPerf(u.perfs.togyzkumalak, PerfType.orDefault("togyzkumalak")),
        showNonEmptyPerf(u.perfs.go9x9, PerfType.orDefault("go9x9")),
        showNonEmptyPerf(u.perfs.go13x13, PerfType.orDefault("go13x13")),
        showNonEmptyPerf(u.perfs.go19x19, PerfType.orDefault("go19x19")),
        showNonEmptyPerf(u.perfs.backgammon, PerfType.orDefault("backgammon")),
        showNonEmptyPerf(u.perfs.nackgammon, PerfType.orDefault("nackgammon")),
        showNonEmptyPerf(u.perfs.breakthroughtroyka, PerfType.orDefault("breakthroughtroyka")),
        showNonEmptyPerf(u.perfs.minibreakthroughtroyka, PerfType.orDefault("minibreakthroughtroyka"))
//         u.noBot option frag(
//           hr,
//           showPerf(u.perfs.puzzle, PerfType.orDefault("puzzle")),
//           showStorm(u.perfs.storm, u),
//           showRacer(u.perfs.racer, u),
//           showStreak(u.perfs.streak, u)
//         )
      )
    )
  }

  private def showStorm(storm: lila.rating.Perf.Storm, user: User)(implicit lang: Lang) =
    a(
      dataIcon := '~',
      cls := List(
        "empty" -> !storm.nonEmpty
      ),
      href := routes.Storm.dashboardOf(user.username),
      span(
        h3("Puzzle Storm"),
        st.rating(
          strong(storm.score),
          storm.nonEmpty option frag(
            " ",
            span(trans.storm.xRuns.plural(storm.runs, storm.runs.localize))
          )
        )
      ),
      iconTag("G")
    )

  private def showRacer(racer: lila.rating.Perf.Racer, user: User)(implicit lang: Lang) =
    a(
      dataIcon := ',',
      cls := List(
        "empty" -> !racer.nonEmpty
      ),
      href := routes.Racer.home,
      span(
        h3("Puzzle Racer"),
        st.rating(
          strong(racer.score),
          racer.nonEmpty option frag(
            " ",
            span(trans.storm.xRuns.plural(racer.runs, racer.runs.localize))
          )
        )
      ),
      iconTag("G")
    )

  private def showStreak(streak: lila.rating.Perf.Streak, user: User)(implicit lang: Lang) =
    a(
      dataIcon := '}',
      cls := List(
        "empty" -> !streak.nonEmpty
      ),
      href := routes.Puzzle.streak,
      span(
        h3("Puzzle Streak"),
        st.rating(
          strong(streak.score),
          streak.nonEmpty option frag(
            " ",
            span(trans.storm.xRuns.plural(streak.runs, streak.runs.localize))
          )
        )
      ),
      iconTag("G")
    )
}
