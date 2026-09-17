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
      title = "Tournament Shields — current holders for every game",
      moreCss = frag(cssTag("tournament.leaderboard"), cssTag("slist")),
      wrapClass = "full-screen-force",
      openGraph = lila.app.ui
        .OpenGraph(
          title = "Tournament Shields — current holders for every game",
          url = s"$netBaseUrl${routes.Tournament.shields.url}",
          description =
            "Who holds the shield of every game on PlayStrategy: the monthly shield arena of each game crowns a holder who keeps the trophy until the next one."
        )
        .some
    ) {
      main(cls := "page-menu tournament-shields-page")(
        views.html.user.bits.communityMenu("shield"),
        div(cls := "page-menu__content box box-pad")(
          h1("Tournament Shields"),
          p(cls := "tournament-shields__intro")(
            "Every game has a monthly shield arena. The winner holds the shield until the next edition; the shield leaderboards count who has held it most."
          ),
          h2("Headhunter:"),
          p(cls := "tournament-shields__hint")(
            "The best shield players of the last two months (5, 3 and 2 points for 1st, 2nd and 3rd place in any shield, 1 for playing, best result per shield), and the longest shield streaks currently held."
          ),
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
              h2(a(href := routes.Tournament.shieldStreaks)("Hot streaks"))
            )
          ),
          h2("Medley Shields:"),
          p(cls := "tournament-shields__hint")("One shield per game family, played across several of its variants."),
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
          h2("Variant Shields:"),
          p(cls := "tournament-shields__hint")("The current holder and the last editions of every game's shield."),
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

  // every shield whose defender is on a streak, longest first: the ones worth taking away
  def streaks(history: TournamentShield.History, upcoming: List[Tournament])(implicit ctx: Context) = {
    val bounties = history.sorted
      .flatMap { case (categ, awards) =>
        val stats = series.Stats(awards.map(aw => series.Win(aw.owner.value, aw.date, aw.tourId)))
        stats.currentReign.filter(_.count > 1).map { reign =>
          (categ, reign, upcoming.find(_.variant == categ.variant))
        }
      }
      .sortBy { case (_, reign, _) => (-reign.count, reign.from.getMillis) }
    val title = "Hot streaks — shield defenders to stop"
    views.html.base.layout(
      title = title,
      moreCss = frag(cssTag("tournament.leaderboard"), cssTag("slist")),
      openGraph = lila.app.ui
        .OpenGraph(
          title = title,
          url = s"$netBaseUrl${routes.Tournament.shieldStreaks.url}",
          description = "Every shield currently held for two editions or more, longest streak first, with the next arena to take it back: " +
            bounties.take(5).map { case (categ, reign, _) => s"${categ.name} (${usernameOrId(reign.userId)}, ${reign.count})" }.mkString(", ")
        )
        .some
    ) {
      main(cls := "page-menu page-small tournament-categ-shields shield-headhunter")(
        views.html.user.bits.communityMenu("shield"),
        div(cls := "page-menu__content box")(
          h1(a(href := routes.Tournament.shields, dataIcon := "I", cls := "text"), "Hot streaks"),
          p(cls := "shield-headhunter__intro")(
            "Defenders currently holding their shield for two editions or more, longest streak first. Stop them at the next one."
          ),
          if (bounties.isEmpty) p(cls := "shield-headhunter__intro")("No defender is on a streak right now.")
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
                    td(cls := "shield-headhunter__streak")(strong(reign.count), " in a row"),
                    td(cls := "shield-headhunter__next")(
                      next.map { t =>
                        a(cls := "button", href := routes.Tournament.show(t.id))(
                          "Take the shield · ",
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
    val title = s"${categ.name} Shield — monthly tournament champions"
    val stats = series.Stats(awards.map(aw => series.Win(aw.owner.value, aw.date, aw.tourId)))
    val wording = series.Wording(
      holderLabel = "Current holder",
      unit = "shield",
      unitsName = s"${categ.name} shields",
      arenaName = "shield arena",
      takeLabel = "Take the shield"
    )
    views.html.base.layout(
      title = title,
      moreCss = frag(cssTag("tournament.leaderboard"), cssTag("slist")),
      openGraph = lila.app.ui
        .OpenGraph(
          title = title,
          url = s"$netBaseUrl${routes.Tournament.categShields(categ.key).url}",
          description = s"Every holder of the monthly ${categ.name} Shield arena on PlayStrategy." +
            series.description(stats, wording)
        )
        .some
    ) {
      main(cls := "page-menu page-small tournament-categ-shields")(
        views.html.user.bits.communityMenu("shield"),
        div(cls := "page-menu__content box")(
          h1(
            a(href := routes.Tournament.shields, dataIcon := "I", cls := "text"),
            categ.name,
            " Shield"
          ),
          p(cls := "tournament-categ-shields__intro")(
            s"The ${categ.name} Shield is a monthly arena; its winner holds the shield until the next edition. ",
            a(href := routes.Library.variant(categ.variant.key))(s"Play ${categ.name} online"),
            "."
          ),
          series.holderCard(
            stats,
            next,
            span(cls := "categ-shield-holder__trophy")(categ.iconChar.toString),
            wording
          ),
          series.longestReign(stats, wording),
          series.podium(stats, wording),
          h2(cls := "shield-section")("Roll of honour"),
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
      title = "Shield Leaderboard",
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
  ) =
    views.html.base.layout(
      title = s"${medleyShield.name} Medley Shield",
      moreCss = frag(cssTag("tournament.leaderboard"), cssTag("slist"))
    ) {
      main(cls := "page-menu page-small tournament-medley-shields")(
        views.html.user.bits.communityMenu("shield"),
        div(cls := "page-menu__content box")(
          h1(
            a(href := routes.Tournament.shields, dataIcon := "I", cls := "text"),
            medleyShield.name,
            " Medley Shield"
          ),
          div(cls := "page-medley-current")(
            img(
              cls := "one-medley-shield-trophy",
              src := staticAssetUrl(s"images/trophy/${medleyShield.key}.png")
            ),
            history.headOption.map { latest =>
              span(
                a(
                  href := latest.fold(
                    arena => routes.Tournament.show(arena.id),
                    swiss => routes.Swiss.show(swiss.id.value)
                  )
                )("Holder"),
                br
              )
            },
            history.headOption.map { latest =>
              span(
                userIdLink(
                  userIdOption = latest.fold(_.winnerId, _.winnerId),
                  cssClass = "reigning-shield-holder".some,
                  withOnline = false
                )
              )
            }
          ),
          h2("Next Tournament"),
          next.map { next =>
            a(
              cls  := "next-tournament",
              href := next.fold(
                arena => routes.Tournament.show(arena.id),
                swiss => routes.Swiss.show(swiss.id.value)
              )
            )(
              h2(
                s"${next.fold(_.name, _.name)} @ ",
                absClientDateTime(next.fold(_.startsAt, _.startsAt))
              )
            )
          },
          h2("Current Tournament Format"),
          h4(medleyShield.arenaFormatFull),
          h2("Variants Used in this Medley"),
          if (medleyShield.hasAllVariants) {
            h4(
              a(
                cls  := "all-variants",
                href := routes.Page.variantHome
              )("All variants on PlayStrategy!")
            )
          } else {
            div(cls := "medley-variants")(
              medleyShield.eligibleVariants.map { variant =>
                section(
                  h2(
                    a(
                      cls      := "medley-variant",
                      href     := routes.Page.variant(variant.key),
                      dataIcon := variant.perfIcon
                    )(
                      span(cls := "medley-variant-name")(
                        VariantKeys.variantName(variant)
                      )
                    )
                  )
                )
              }
            )
          },
          h2(cls := "shield-section")("Roll of Honour"),
          ol(cls := "shield-roll")(history.map { aw =>
            li(
              userIdLink(aw.fold(_.winnerId, _.winnerId)),
              a(
                href := aw.fold(
                  arena => routes.Tournament.show(arena.id),
                  swiss => routes.Swiss.show(swiss.id.value)
                )
              )(showDate(aw.fold(_.startsAt, _.startsAt)))
            )
          })
        )
      )
    }
}
