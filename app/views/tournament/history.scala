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

  private val perVariant: Set[Freq] =
    Set(Freq.Shield, Freq.Yearly, Freq.Weekly, Freq.GroupCycle, Freq.Wildcard)

  def hasSeries(freq: Freq) = perVariant(freq)

  def freqNav(current: Freq, variant: Option[Variant]) =
    st.nav(cls := "page-menu__menu subnav")(
      allFreqs.map { f =>
        a(cls := current.name.active(f.name), href := url(f, variant))(nameOf(f))
      }
    )

  def url(freq: Freq, variant: Option[Variant], page: Int = 1) =
    variant.filter(_ => hasSeries(freq)).fold(routes.Tournament.history(freq.name, page).url) { v =>
      lila.tournament.TournamentShield.Category
        .byKey(v.key)
        .filter(_ => freq == Freq.Shield)
        .fold(routes.Tournament.historyVariant(freq.name, v.key, page).url)(c =>
          routes.Tournament.categShields(c.key).url
        )
    }

  // filtered by variant, this is the one stable page for a recurring series ("Yearly Abalone")
  def apply(freq: Freq, variant: Option[Variant], pager: Paginator[Tournament], summary: Option[Summary] = None)(
      implicit ctx: Context
  ) = {
    val heading = variant.fold(s"${nameOf(freq)} tournaments") { v =>
      s"${nameOf(freq)} ${VariantKeys.variantName(v)} tournaments"
    }
    val stats = summary.map { s =>
      series.Stats(s.all.flatMap { t =>
        t.winnerId.map(w => series.Win(w, t.startsAt, routes.Tournament.show(t.id).url))
      })
    }
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
      canonicalPath = url(freq, variant).some,
      openGraph = variant.map { v =>
        lila.app.ui.OpenGraph(
          title = s"$heading — every edition and winner",
          url = s"$netBaseUrl${url(freq, variant)}",
          description = s"Every edition of the ${nameOf(freq)} ${VariantKeys.variantName(v)} arena on PlayStrategy, with its winner." +
            (stats zip wording).map { case (st, w) => series.description(st, w) }.getOrElse("")
        )
      }
    ) {
      main(cls := "page-menu arena-history")(
        freqNav(freq, variant),
        div(cls := "page-menu__content box")(
          h1(heading),
          // one link per game: the series page of that game for this frequency
          hasSeries(freq).option(
            div(cls := "series-links")(
              a(cls := List("text" -> true, "active" -> variant.isEmpty), href := url(freq, none))("All games"),
              Variant.all.filterNot(_.fromPositionVariant).map { v =>
                a(
                  cls      := List("text" -> true, "active" -> variant.contains(v)),
                  dataIcon := v.perfIcon,
                  href     := url(freq, v.some)
                )(VariantKeys.variantName(v))
              }
            )
          ),
          variant.map { v =>
            p(cls := "arena-history__intro")(
              a(href := routes.Library.variant(v.key))(trans.playVariantOnlineFreeTitle(VariantKeys.variantName(v))),
              " · ",
              a(href := url(freq, none))(s"${nameOf(freq)} tournaments of every game")
            )
          },
          (stats zip wording zip summary).map { case ((st, w), sm) =>
            frag(
              series.holderCard(
                st,
                sm.next.map(t => series.Next(routes.Tournament.show(t.id).url, t.startsAt)),
                emptyFrag,
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
                pagerNextTable(pager, p => url(freq, variant, p))
              )
            )
          )
        )
      )
    }
  }

  private def nameOf(f: Freq) = if (f == Freq.Weekend) "Elite" else f.display

  // the sitemap lists one page per frequency, and one per series (see Main.sitemap)
  val allFreqs = List(
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
