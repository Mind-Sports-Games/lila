package views.html.tournament

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.tournament.{ ShieldTableApi, Tournament, TournamentShield }
import lila.swiss.Swiss
import lila.i18n.VariantKeys

import controllers.routes

object shields {

  private val section = st.section(cls := "tournament-shields__item")

  def apply(history: TournamentShield.History)(implicit ctx: Context) =
    views.html.base.layout(
      title = "Tournament Shields",
      moreCss = cssTag("tournament.leaderboard"),
      wrapClass = "full-screen-force"
    ) {
      main(cls := "page-menu")(
        views.html.user.bits.communityMenu("shield"),
        div(cls := "page-menu__content box box-pad")(
          h1("Tournament Shields"),
          h2("Shield Leaderboards:"),
          div(cls := "shield-leaderboards")(
            ShieldTableApi.Category.all.map { category =>
              section(
                h2(
                  a(href := routes.Tournament.shieldLeaderboard(category.id))(
                    category.name
                  )
                )
              )
            }
          ),
          h2("Medley Shields:"),
          div(cls := "medley-shields")(
            TournamentShield.MedleyShield.all.map { medley =>
              section(
                h2(
                  a(href := routes.Tournament.medleyShield(medley.key))(
                    span(cls := "medley-shield-trophy")(
                      img(cls := "medley-trophy", src := assetUrl(s"images/trophy/${medley.key}.png"))
                    ),
                    medley.name
                  )
                )
              )
            }
          ),
          h2("Variant Shields:"),
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

  def byCateg(categ: TournamentShield.Category, awards: List[TournamentShield.Award])(implicit ctx: Context) =
    views.html.base.layout(
      title = "Tournament shields",
      moreCss = frag(cssTag("tournament.leaderboard"), cssTag("slist"))
    ) {
      main(cls := "page-menu page-small tournament-categ-shields")(
        views.html.user.bits.communityMenu("shield"),
        div(cls := "page-menu__content box")(
          h1(
            a(href := routes.Tournament.shields, dataIcon := "I", cls := "text"),
            categ.name,
            " shields"
          ),
          ol(awards.map { aw =>
            li(
              span(cls := "shield-trophy")(categ.iconChar.toString),
              userIdLink(aw.owner.value.some),
              a(href := routes.Tournament.show(aw.tourId))(showDate(aw.date))
            )
          })
        )
      )
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
            img(cls := "one-medley-shield-trophy", src := assetUrl(s"images/trophy/${medleyShield.key}.png")),
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
              cls := "next-tournament",
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
                cls := "all-variants",
                href := routes.Page.variantHome
              )("All variants on PlayStrategy!")
            )
          } else {
            div(cls := "medley-variants")(
              medleyShield.eligibleVariants.map { variant =>
                section(
                  h2(
                    a(
                      cls := "medley-variant",
                      href := routes.Page.variant(variant.key),
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
          h2("Roll of Honour"),
          ol(history.map { aw =>
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
