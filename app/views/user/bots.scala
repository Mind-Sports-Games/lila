package views.html
package user

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.user.User

object bots {

  def apply(users: List[User])(implicit ctx: Context) = {

    val title = s"${users.size} Online bots"

    val sorted = users.sortBy { -_.playTime.??(_.total) }

    views.html.base.layout(
      title = title,
      moreCss = frag(cssTag("slist"), cssTag("user.list")),
      wrapClass = "full-screen-force"
    )(
      main(cls := "page-menu bots")(
        user.bits.communityMenu("bots"),
        sorted.partition(_.isVerified) match {
          case (featured, all) =>
            div(cls := "bots page-menu__content")(
              div(cls := "box bots__featured")(
                div(cls := "box__top")(h1("Featured bots")),
                botTable(featured)
              ),
              div(cls := "box")(
                div(cls := "box__top")(
                  h1("Community bots"),
                  a(
                    cls := "bots__about",
                    href := "https://playstrategy.org/blog/YvJkQxAAACIApTnD/welcome-playstrategy-bots"
                  )(
                    "About PlayStrategy Bots"
                  )
                ),
                botTable(all)
              )
            )
        }
      )
    )
  }

  private def botTable(users: List[User])(implicit ctx: Context) = div(cls := "bots__list")(
    users map { u =>
      div(cls := "bots__list__entry")(
        div(cls := "bots__list__entry__desc")(
          div(cls := "bots__list__entry__head")(
            userLink(u),
            div(cls := "bots__list__entry__rating")(
              u.best3Perfs.map { showPerfRating(u, _) }
            )
          ),
          u.profile
            .ifTrue(ctx.noKid)
            .ifTrue(!u.marks.troll || ctx.is(u))
            .flatMap(_.nonEmptyBio)
            .map { bio => td(shorten(bio, 400)) }
        ),
        a(
          dataIcon := "U",
          cls := List("button button-empty text" -> true),
          st.title := trans.challenge.challengeToPlay.txt(),
          href := s"${routes.Lobby.home}?user=${u.username}#friend"
        )(trans.play())
      )
    }
  )
}
