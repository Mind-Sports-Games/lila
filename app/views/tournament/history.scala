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

  case class Summary(all: List[Tournament], next: Option[Tournament])

  private val perVariant: Set[Freq] =
    Set(Freq.Shield, Freq.Yearly, Freq.Weekly, Freq.GroupCycle, Freq.Wildcard)

  def hasSeries(freq: Freq) = perVariant(freq)

  // switching frequency keeps the game or family; the current section's entry drops it instead,
  // the only way back from a shield category to /tournament/history/shield
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

  def apply(
      freq: Freq,
      variant: Option[Variant],
      group: Option[GameGroup],
      pager: Paginator[Tournament],
      summary: Option[Summary] = None
  )(
      implicit ctx: Context
  ) = {
    // a family named after one of its games says so, or the two pages would share a title
    val subject = variant.map(VariantKeys.variantName) orElse group.map(VariantKeys.gameGroupName)
    val heading = subject.fold(trans.historyHeading.txt(nameOf(freq))) { s =>
      if (group.isDefined) trans.historyHeadingFamily.txt(nameOf(freq), s)
      else trans.historyHeadingGame.txt(nameOf(freq), s)
    }
    val stats = summary.map { s =>
      series.Stats(s.all.flatMap { t =>
        t.winnerId.map(w => series.Win(w, t.startsAt, routes.Tournament.show(t.id).url))
      })
    }
    val wording = variant.map { v =>
      series.Wording(contested = false, subject = s"${nameOf(freq)} ${VariantKeys.variantName(v)}")
    }
    views.html.base.layout(
      title = subject.fold(heading)(_ => trans.historyTitle.txt(heading)),
      moreJs = infiniteScrollTag,
      moreCss = frag(cssTag("tournament.history"), summary.isDefined.option(cssTag("tournament.leaderboard"))),
      canonicalPath = pageUrl(freq, variant, group).some,
      openGraph = subject.map { name =>
        lila.app.ui.OpenGraph(
          title = trans.historyTitle.txt(heading),
          url = s"$netBaseUrl${pageUrl(freq, variant, group)}",
          description =
            trans.historyDescription.txt(s"${nameOf(freq)} $name") +
              (stats zip wording).map { case (st, w) => series.description(st, w) }.getOrElse("")
        )
      }
    ) {
      main(cls := "page-menu arena-history")(
        freqNav(freq, variant, group),
        div(cls := "page-menu__content box")(
          // kept out of the h1, which stays the page's name
          div(cls := "arena-history__head")(
            h1(heading),
            variant.map { v =>
              a(cls := "arena-history__about", href := routes.Library.variant(v.key))(
                trans.aboutX(VariantKeys.variantName(v)),
                " ›"
              )
            }
          ),
          // game group, then game: each chip is a link to a page of its own
          hasSeries(freq).option {
            val open = group orElse variant.flatMap(v => displayedGameGroups.find(_.variants.contains(v)))
            frag(
              div(cls := "series-links series-links__groups")(
                a(
                  cls  := List("text" -> true, "active" -> (variant.isEmpty && group.isEmpty)),
                  href := url(freq, none)
                )(trans.historyAllGames()),
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
          // not "yet": a legacy series may never have another edition
          if (pager.currentPage == 1 && pager.currentPageResults.isEmpty)
            p(cls := "arena-history__empty")(trans.noFinishedTournament())
          else
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
