package views.html.tournament

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.tournament.{ ShieldTableApi, Tournament, TournamentShield }
import lila.swiss.Swiss
import lila.i18n.VariantKeys


object shields {

  private val section = st.section(cls := "tournament-shields__item")

  def apply(history: TournamentShield.History)(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.shieldsTitle.txt(),
      moreCss = frag(cssTag("tournament.leaderboard"), cssTag("slist")),
      wrapClass = "full-screen-force",
      openGraph = lila.app.ui
        .OpenGraph(
          title = trans.shieldsTitle.txt(),
          url = s"$netBaseUrl${routes.Tournament.shields.url}",
          description = trans.shieldsDescription.txt()
        )
        .some
    ) {
      main(cls := "page-menu tournament-shields-page")(
        views.html.user.bits.communityMenu("shield"),
        div(cls := "page-menu__content box box-pad")(
          h1(trans.shieldsHeading()),
          p(cls := "tournament-shields__intro")(trans.shieldsIntro()),
          h2(trans.shieldsHeadhunter()),
          p(cls := "tournament-shields__hint")(trans.shieldsHeadhunterHint()),
          div(cls := "shield-leaderboards")(
            ShieldTableApi.Category.all.map { category =>
              section(
                h2(
                  a(href := routes.Tournament.shieldLeaderboard(category.id))(
                    category.name
                  )
                )
              )
            },
            section(
              h2(a(href := routes.Tournament.shieldStreaks)(trans.shieldsHotStreaks()))
            )
          ),
          h2(trans.shieldsMedley()),
          p(cls := "tournament-shields__hint")(trans.shieldsMedleyHint()),
          div(cls := "medley-shields")(
            TournamentShield.MedleyShield.all.map { medley =>
              section(
                h2(
                  a(href := routes.Tournament.medleyShield(medley.key))(
                    span(cls := "medley-shield-trophy")(
                      img(cls := "medley-trophy", src := staticAssetUrl(s"images/trophy/${medley.key}.png"))
                    ),
                    medley.name
                  )
                )
              )
            }
          ),
          h2(trans.shieldsVariant()),
          p(cls := "tournament-shields__hint")(trans.shieldsVariantHint()),
          div(cls := "tournament-shields")(
            history.sorted.map { case (categ, awards) =>
              section(
                h2(
                  a(href := routes.Tournament.categShields(categ.key))(
                    span(cls := "shield-trophy")(categ.iconChar.toString),
                    categ.name
                  )
                ),
                ol(awards.map { aw =>
                  li(
                    userIdLink(aw.owner.value.some),
                    a(href := routes.Tournament.show(aw.tourId))(showDate(aw.date))
                  )
                })
              )
            }
          )
        )
      )
    }

  def streaks(history: TournamentShield.History, upcoming: List[Tournament])(implicit ctx: Context) = {
    val bounties = history.sorted
      .flatMap { case (categ, awards) =>
        val stats = series.Stats(
          awards.map(aw => series.Win(aw.owner.value, aw.date, routes.Tournament.show(aw.tourId).url))
        )
        stats.currentReign.filter(_.count > 1).map { reign =>
          (categ, reign, upcoming.find(_.variant == categ.variant))
        }
      }
      .sortBy { case (_, reign, _) => (-reign.count, reign.from.getMillis) }
    val title = trans.shieldsStreaksTitle.txt()
    views.html.base.layout(
      title = title,
      moreCss = frag(cssTag("tournament.leaderboard"), cssTag("slist")),
      openGraph = lila.app.ui
        .OpenGraph(
          title = title,
          url = s"$netBaseUrl${routes.Tournament.shieldStreaks.url}",
          description = trans.shieldsStreaksDescription.txt(
            bounties
              .take(5)
              .map { case (categ, reign, _) =>
                s"${categ.name} (${usernameOrId(reign.userId)}, ${reign.count})"
              }
              .mkString(", ")
          )
        )
        .some
    ) {
      main(cls := "page-menu page-small tournament-categ-shields shield-headhunter")(
        views.html.user.bits.communityMenu("shield"),
        div(cls := "page-menu__content box")(
          h1(a(href := routes.Tournament.shields, dataIcon := "I", cls := "text"), trans.shieldsHotStreaks()),
          p(cls := "shield-headhunter__intro")(trans.shieldsStreaksIntro()),
          if (bounties.isEmpty) p(cls := "shield-headhunter__intro")(trans.shieldsNoStreak())
          else
            table(cls := "slist slist-pad shield-headhunter__list")(
              tbody(
                bounties.map { case (categ, reign, next) =>
                  tr(
                    td(cls := "shield-headhunter__game")(
                      a(href := routes.Tournament.categShields(categ.key), cls := "text", dataIcon := categ.iconChar)(
                        categ.name
                      )
                    ),
                    td(userIdLink(reign.userId.some, withOnline = false)),
                    td(cls := "shield-headhunter__streak")(
                      trans.seriesInARow(raw(strong(reign.count).render))
                    ),
                    td(cls := "shield-headhunter__next")(
                      next.map { t =>
                        a(cls := "button", href := routes.Tournament.show(t.id))(
                          s"${trans.seriesTakeShield.txt()} · ",
                          absClientDateTime(t.startsAt)
                        )
                      }
                    )
                  )
                }
              )
            )
        )
      )
    }
  }

  def byCateg(
      categ: TournamentShield.Category,
      awards: List[TournamentShield.Award],
      next: Option[Tournament]
  )(implicit ctx: Context) = {
    val title = trans.shieldCategTitle.txt(categ.name)
    val stats = series.Stats(awards.map(aw => series.Win(aw.owner.value, aw.date, routes.Tournament.show(aw.tourId).url)))
    val wording = series.Wording(contested = true, subject = categ.name)
    views.html.base.layout(
      title = title,
      // a category no one has ever held is an empty page
      robots = netConfig.crawlable && (awards.nonEmpty || next.isDefined),
      moreCss = frag(cssTag("tournament.leaderboard"), cssTag("slist")),
      openGraph = lila.app.ui
        .OpenGraph(
          title = title,
          url = s"$netBaseUrl${routes.Tournament.categShields(categ.key).url}",
          description = trans.shieldCategDescription.txt(categ.name) + series.description(stats, wording)
        )
        .some
    ) {
      main(cls := "page-menu page-small tournament-categ-shields arena-history")(
        history.freqNav(lila.tournament.Schedule.Freq.Shield, categ.variant.some),
        div(cls := "page-menu__content box")(
          h1(
            a(href := routes.Tournament.shields, dataIcon := "I", cls := "text"),
            trans.shieldCategName(categ.name)
          ),
          p(cls := "tournament-categ-shields__intro")(
            trans.shieldCategIntro(categ.name),
            " ",
            a(href := routes.Library.variant(categ.variant.key))(trans.aboutX(categ.name))
          ),
          series.holderCard(
            stats,
            next.map(t => series.Next(routes.Tournament.show(t.id).url, t.startsAt)),
            span(cls := "categ-shield-holder__trophy")(categ.iconChar.toString),
            wording
          ),
          series.record(stats, wording),
          series.podium(stats, wording),
          h2(cls := "shield-section")(trans.seriesRollOfHonour()),
          ol(cls := "shield-roll")(awards.map { aw =>
            li(
              span(cls := "shield-trophy")(categ.iconChar.toString),
              userIdLink(aw.owner.value.some),
              a(href := routes.Tournament.show(aw.tourId))(showDate(aw.date))
            )
          })
        )
      )
    }
  }

  def leaderboardByCateg(
      userPoints: List[ShieldTableApi.ShieldTableEntry],
      title: String,
      restrictionGameFamily: String
  )(implicit
      ctx: Context
  ) =
    views.html.base.layout(
      title = trans.shieldLeaderboard.txt(),
      moreCss = frag(cssTag("tournament.leaderboard"), cssTag("slist"))
    ) {
      main(cls := "page-small box tournament-categ-shields")(
        h1(a(href := routes.Tournament.shields, dataIcon := "I"), title),
        table(cls := "slist slist-pad")(
          tbody(
            userPoints.zipWithIndex.map { case (u, i) =>
              tr(
                td(i + 1),
                td(userIdLink(u.userId.some)),
                td(cls := "row-num")(
                  a(href := routes.UserTournament.path(u.userId, "shieldleaderboard"))(u.points)
                )
              )
            }
          )
        ),
        div(cls := "shield-leaderboard-faq")(
          h2(trans.faq.howDoesTheSheildLeaderboardWork.txt()),
          p(
            trans.faq.shieldLeaderboardOverview(
              a(href := routes.Tournament.shields)(s"${restrictionGameFamily}Shield")
            )
          ),
          ol(
            li(trans.faq.firstPlaceShield()),
            li(trans.faq.secondPlaceShield()),
            li(trans.faq.thirdPlaceShield()),
            li(trans.faq.playedAtLeastOneGameShield())
          ),
          p(
            trans.faq.otherShieldLeaderboardRestrictons()
          )
        )
      )
    }

  def medley(
      medleyShield: TournamentShield.MedleyShield,
      next: Option[Either[Tournament, Swiss]],
      history: List[Either[Tournament, Swiss]]
  )(implicit
      ctx: Context
  ) = {
    def editionUrl(e: Either[Tournament, Swiss]) =
      e.fold(arena => routes.Tournament.show(arena.id).url, swiss => routes.Swiss.show(swiss.id.value).url)
    val name  = trans.medleyShieldName.txt(medleyShield.name)
    val title = trans.medleyShieldTitle.txt(name)
    val stats = series.Stats(history.flatMap { e =>
      e.fold(_.winnerId, _.winnerId).map(w => series.Win(w, e.fold(_.startsAt, _.startsAt), editionUrl(e)))
    })
    val wording = series.Wording(contested = true, subject = name)
    views.html.base.layout(
      title = title,
      moreCss = frag(cssTag("tournament.leaderboard"), cssTag("slist")),
      openGraph = lila.app.ui
        .OpenGraph(
          title = title,
          url = s"$netBaseUrl${routes.Tournament.medleyShield(medleyShield.key).url}",
          description = trans.medleyShieldDescription.txt(name) + series.description(stats, wording)
        )
        .some
    ) {
      main(cls := "page-menu page-small tournament-medley-shields tournament-categ-shields")(
        views.html.user.bits.communityMenu("shield"),
        div(cls := "page-menu__content box")(
          h1(a(href := routes.Tournament.shields, dataIcon := "I", cls := "text"), name),
          p(cls := "tournament-categ-shields__intro")(trans.medleyShieldIntro(name)),
          series.holderCard(
            stats,
            next.map(n => series.Next(editionUrl(n), n.fold(_.startsAt, _.startsAt))),
            img(
              cls := "one-medley-shield-trophy",
              src := staticAssetUrl(s"images/trophy/${medleyShield.key}.png")
            ),
            wording
          ),
          series.record(stats, wording),
          series.podium(stats, wording),
          h2(cls := "shield-section")(trans.medleyShieldFormat()),
          p(cls := "shield-format")(medleyShield.arenaFormatFull),
          h2(cls := "shield-section")(trans.medleyShieldVariants()),
          if (medleyShield.hasAllVariants)
            p(cls := "shield-format")(
              a(cls := "all-variants", href := routes.Page.variantHome)(trans.medleyShieldAllVariants())
            )
          else
            div(cls := "medley-variants")(
              medleyShield.eligibleVariants.map { variant =>
                section(
                  h2(
                    a(
                      cls      := "medley-variant",
                      href     := routes.Library.variant(variant.key),
                      dataIcon := variant.perfIcon
                    )(span(cls := "medley-variant-name")(VariantKeys.variantName(variant)))
                  )
                )
              }
            ),
          h2(cls := "shield-section")(trans.seriesRollOfHonour()),
          ol(cls := "shield-roll")(history.map { aw =>
            li(
              userIdLink(aw.fold(_.winnerId, _.winnerId)),
              a(href := editionUrl(aw))(showDate(aw.fold(_.startsAt, _.startsAt)))
            )
          })
        )
      )
    }
  }
}
