package views.html.tournament

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.common.paginator.Paginator
import lila.i18n.VariantKeys
import lila.tournament.Schedule.Freq
import lila.tournament.Tournament

import strategygames.variant.Variant

object history {

  // every finished edition of a series plus the next scheduled one
  case class Summary(all: List[Tournament], next: Option[Tournament])

  // filtered by variant, this is the one stable page for a recurring series ("Yearly Abalone")
  def apply(freq: Freq, variant: Option[Variant], pager: Paginator[Tournament], summary: Option[Summary] = None)(
      implicit ctx: Context
  ) = {
    val variantKey = variant.map(_.key)
    val heading    = variant.fold(s"${nameOf(freq)} tournaments") { v =>
      s"${nameOf(freq)} ${VariantKeys.variantName(v)} tournaments"
    }
    val stats = summary.map(s => series.Stats(s.all.flatMap(t => t.winnerId.map(w => series.Win(w, t.startsAt, t.id)))))
    val wording = variant.map { v =>
      series.Wording(
        holderLabel = "Reigning champion",
        unit = "title",
        unitsName = s"${nameOf(freq)} ${VariantKeys.variantName(v)} titles",
        arenaName = s"${nameOf(freq)} ${VariantKeys.variantName(v)} Arena",
        takeLabel = "Take the title",
        contested = false
      )
    }
    views.html.base.layout(
      title = variant.fold("Tournament history")(_ => s"$heading — every edition and winner"),
      moreJs = infiniteScrollTag,
      moreCss = frag(cssTag("tournament.history"), summary.isDefined.option(cssTag("tournament.leaderboard"))),
      canonicalPath = routes.Tournament.history(freq.name, 1, variantKey).url.some,
      openGraph = variant.map { v =>
        lila.app.ui.OpenGraph(
          title = s"$heading — every edition and winner",
          url = s"$netBaseUrl${routes.Tournament.history(freq.name, 1, variantKey).url}",
          description = s"Every edition of the ${nameOf(freq)} ${VariantKeys.variantName(v)} arena on PlayStrategy, with its winner." +
            (stats zip wording).map { case (st, w) => series.description(st, w) }.getOrElse("")
        )
      }
    ) {
      main(cls := "page-menu arena-history")(
        st.nav(cls := "page-menu__menu subnav")(
          allFreqs.map { f =>
            a(cls := freq.name.active(f.name), href := routes.Tournament.history(f.name, 1, variantKey))(
              nameOf(f)
            )
          }
        ),
        div(cls := "page-menu__content box")(
          h1(heading),
          // one link per game: the series page of that game for this frequency
          div(cls := "arena-history__variants")(
            a(cls := List("text" -> true, "active" -> variant.isEmpty), href := routes.Tournament.history(freq.name, 1, none))("All games"),
            Variant.all.filterNot(_.fromPositionVariant).map { v =>
              a(
                cls      := List("text" -> true, "active" -> variant.contains(v)),
                dataIcon := v.perfIcon,
                href     := routes.Tournament.history(freq.name, 1, v.key.some)
              )(VariantKeys.variantName(v))
            }
          ),
          variant.map { v =>
            p(cls := "arena-history__intro")(
              a(href := routes.Library.variant(v.key))(trans.playVariantOnlineFreeTitle(VariantKeys.variantName(v))),
              " · ",
              a(href := routes.Tournament.history(freq.name, 1, none))(s"${nameOf(freq)} tournaments of every game")
            )
          },
          (stats zip wording zip variant zip summary).map { case (((st, w), v), sm) =>
            frag(
              series.holderCard(
                st,
                sm.next,
                span(cls := "categ-shield-holder__trophy categ-shield-holder__trophy--cup")(v.perfIcon.toString),
                w
              ),
              series.podium(st, w),
              h2(cls := "shield-section")("Every edition")
            )
          },
          div(cls := "arena-list")(
            table(cls := "slist slist-pad")(
              tbody(cls := "infinite-scroll")(
                pager.currentPageResults map finishedList.apply,
                pagerNextTable(pager, p => routes.Tournament.history(freq.name, p, variantKey).url)
              )
            )
          )
        )
      )
    }
  }

  private def nameOf(f: Freq) = if (f == Freq.Weekend) "Elite" else f.display

  private val allFreqs = List(
    Freq.Annual,
    Freq.Introductory,
    Freq.MSOGP,
    Freq.MSO21,
    // Freq.MSOWarmUp,
    Freq.Unique,
    // Freq.Marathon,
    Freq.Shield,
    Freq.Yearly,
    Freq.MedleyMarathon,
    // Freq.Monthly,
    // Freq.Weekend,
    Freq.GroupCycle,
    Freq.Wildcard,
    Freq.Weekly
    // Freq.Daily,
    // Freq.Hourly
  )
}
