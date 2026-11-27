package views.html
package stat

import play.api.libs.json.Json

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.common.String.html.safeJsonValue
import lila.rating.PerfType

object ratingDistribution {

  def apply(perfType: PerfType, data: List[Int])(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.weeklyPerfTypeRatingDistribution.txt(perfType.trans),
      moreCss = cssTag("user.rating.stats"),
      wrapClass = "full-screen-force",
      moreJs = frag(
        jsModule("chart.ratingDistribution"),
        embedJsUnsafeLoadThen(s"""playstrategy.ratingDistributionChart(${safeJsonValue(
            Json.obj(
              "freq"     -> data,
              "myRating" -> ctx.me.map(_.perfs(perfType)).filter(_.nb > 0).map { perf =>
                Json.obj("rating" -> perf.intRating, "provisional" -> perf.provisional)
              },
              "i18n"     -> i18nJsObject(i18nKeys)
            )
          )})""")
      )
    ) {
      main(cls := "page-menu")(
        user.bits.communityMenu("ratings"),
        div(cls := "rating-stats page-menu__content box box-pad")(
          h1(trans.weeklyPerfTypeRatingDistribution(perfType.trans)),
          views.html.base.gamePicker.chips(
            PerfType.leaderboardable.map { pt =>
              views.html.base.gamePicker.Choice(
                pt.key,
                pt.trans,
                pt.iconChar.toString,
                PerfType.variantOf(pt),
                routes.Stat.ratingDistribution(pt.key).url
              )
            },
            perfType.key
          ),
          div(cls := "desc", dataIcon := perfType.iconChar)(
            ctx.me.flatMap(_.perfs(perfType).glicko.establishedIntRating).map { rating =>
              lila.user.Stat.percentile(data, rating) match {
                case (under, sum) =>
                  div(
                    trans
                      .nbPerfTypePlayersThisWeek(strong(sum.localize), perfType.trans),
                    br,
                    trans.yourPerfTypeRatingIsRating(perfType.trans, strong(rating)),
                    br,
                    trans.youAreBetterThanPercentOfPerfTypePlayers(
                      strong((under * 100.0 / sum).round, "%"),
                      perfType.trans
                    )
                  )
              }
            } getOrElse div(
              trans.nbPerfTypePlayersThisWeek
                .plural(data.sum, strong(data.sum.localize), perfType.trans),
              br,
              trans.youDoNotHaveAnEstablishedPerfTypeRating(perfType.trans)
            )
          ),
          div(id := "rating_distribution_chart")(canvas)
        )
      )
    }

  private val i18nKeys =
    List(
      trans.players,
      trans.yourRating,
      trans.cumulative,
      trans.glicko2Rating
    ).map(_.key)
}
