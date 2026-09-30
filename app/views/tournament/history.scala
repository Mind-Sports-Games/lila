package views.html.tournament

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.common.paginator.Paginator
import lila.i18n.VariantKeys
import lila.tournament.Schedule.Freq
import lila.tournament.Tournament
import strategygames.GameGroup
import strategygames.variant.Variant

object history {

  // every finished edition of a series plus the next scheduled one
  case class Summary(all: List[Tournament], next: Option[Tournament])

  private val perVariant: Set[Freq] =
    Set(Freq.Shield, Freq.Yearly, Freq.Weekly, Freq.GroupCycle, Freq.Wildcard)

  def hasSeries(freq: Freq) = perVariant(freq)

  // Switching frequency keeps the game or family you were looking at, but the entry for the
  // section you are already in drops it instead of pointing at this very page: from a shield
  // category that is the only way back to /tournament/history/shield, since url() sends a
  // shield variant to its category page.
  def freqNav(current: Freq, variant: Option[Variant], group: Option[GameGroup] = None) =
    st.nav(cls := "page-menu__menu subnav")(
      allFreqs.map { f =>
        val target = if (f == current) url(f, none) else pageUrl(f, variant, group)
        a(cls := current.name.active(f.name), href := target)(nameOf(f))
      }
    )

  // a family of one game - Amazons - has no page of its own: the game's page is the family's
  def soleVariant(group: GameGroup): Option[Variant] =
    group.variants.filterNot(_.fromPositionVariant) match {
      case v :: Nil => v.some
      case _        => none
    }

  // the families with a page of their own, so the sitemap and the chips cannot disagree
  def displayedGroups = displayedGameGroups.filter(soleVariant(_).isEmpty)

  def groupByKey(key: String): Option[GameGroup] =
    displayedGameGroups.find(_.key == key)

  def groupUrl(freq: Freq, group: GameGroup, page: Int = 1) =
    if (!hasSeries(freq)) routes.Tournament.history(freq.name, page).url
    else
      soleVariant(group).fold(routes.Tournament.historyGroup(freq.name, group.key, page).url) { v =>
        url(freq, v.some, page)
      }

  // the page as filtered: one game, else a family, else everything of the frequency
  def pageUrl(freq: Freq, variant: Option[Variant], group: Option[GameGroup], page: Int = 1) =
    group.fold(url(freq, variant, page))(groupUrl(freq, _, page))

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
  def apply(
      freq: Freq,
      variant: Option[Variant],
      group: Option[GameGroup],
      pager: Paginator[Tournament],
      summary: Option[Summary] = None
  )(
      implicit ctx: Context
  ) = {
    // One variant, a whole family, or everything. A family is named after one of its games in
    // eight cases out of twelve - Abalone holds Abalone and Grand Abalone - so the family page
    // says so, or the two would carry the same title.
    val subject = variant.map(VariantKeys.variantName) orElse group.map(VariantKeys.gameGroupName)
    val heading = subject.fold(s"${nameOf(freq)} tournaments") { s =>
      s"${nameOf(freq)} $s tournaments" + group.so(_ => ", all variants")
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
      // the heading already names the frequency, so nine frequency pages stop sharing one title
      title = subject.fold(heading)(_ => s"$heading — every edition and winner"),
      moreJs = infiniteScrollTag,
      moreCss = frag(cssTag("tournament.history"), summary.isDefined.option(cssTag("tournament.leaderboard"))),
      canonicalPath = pageUrl(freq, variant, group).some,
      openGraph = subject.map { name =>
        lila.app.ui.OpenGraph(
          title = s"$heading — every edition and winner",
          url = s"$netBaseUrl${pageUrl(freq, variant, group)}",
          description =
            s"Every edition of the ${nameOf(freq)} $name arena on PlayStrategy, with its winner." +
              (stats zip wording).map { case (st, w) => series.description(st, w) }.getOrElse("")
        )
      }
    ) {
      main(cls := "page-menu arena-history")(
        freqNav(freq, variant, group),
        div(cls := "page-menu__content box")(
          h1(heading),
          // Two stages, game group then game, because 47 chips in one row is not a chooser.
          // A group is a page of its own - every Abalone tournament of this frequency, across
          // the family - so each chip is a link and none of this needs a line of script.
          hasSeries(freq).option {
            val open = group orElse variant.flatMap(v => displayedGameGroups.find(_.variants.contains(v)))
            frag(
              div(cls := "series-links series-links__groups")(
                a(
                  cls  := List("text" -> true, "active" -> (variant.isEmpty && group.isEmpty)),
                  href := url(freq, none)
                )("All games"),
                displayedGameGroups.map { g =>
                  a(
                    cls := List(
                      "text"   -> true,
                      "group"  -> true,
                      "active" -> open.contains(g)
                    ),
                    dataIcon := g.variants.headOption.map(_.perfIcon.toString).getOrElse(""),
                    // an active chip steps back out: a family chip drops the filter entirely,
                    // whether the page is showing that family or one of its games
                    href := (if (open.contains(g)) url(freq, none) else groupUrl(freq, g))
                  )(VariantKeys.gameGroupName(g))
                }
              ),
              // a family of one game has nothing to choose under its chip
              open.filter(soleVariant(_).isEmpty).map { g =>
                div(cls := "series-links series-links__variants")(
                  g.variants.filterNot(_.fromPositionVariant).map { v =>
                    a(
                      cls      := List("text" -> true, "active" -> variant.contains(v)),
                      dataIcon := v.perfIcon,
                      href     := (if (variant.contains(v)) groupUrl(freq, g) else url(freq, v.some))
                    )(VariantKeys.variantName(v))
                  }
                )
              }
            )
          },
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
                // a family page pages within the family, not into the unfiltered list
                pagerNextTable(pager, pageUrl(freq, variant, group, _))
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
