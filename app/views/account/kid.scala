package views.html
package account

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*

object kid {

  def apply(
      u: lila.user.User,
      form: play.api.data.Form[?],
      managed: Boolean,
      page: Option[(io.prismic.Document, io.prismic.DocumentLinkResolver)]
  )(implicit ctx: Context) =
    account.layout(
      title = s"${u.username} - ${trans.kidMode.txt()}",
      active = "kid",
      evenMoreCss = page.isDefined.option(cssTag("page"))
    ) {
      frag(
        div(cls := List("account box box-pad" -> true, "kid-mode" -> page.isDefined))(
          h1(trans.kidMode()),
          standardFlash(),
          p(trans.kidModeExplanation()),
          br,
          br,
          br,
          if (managed) p("Your account is managed. Ask your chess teacher about lifting kid mode.")
          else
            postForm(cls := "form3", action := s"${routes.Account.kidPost}?v=${!u.kid}")(
              form3.passwordModified(form("passwd"), trans.password())(autofocus, autocomplete := "off"),
              submitButton(
                cls := List(
                  "button"     -> true,
                  "button-red" -> u.kid
                )
              )(if (u.kid) trans.disableKidMode.txt() else trans.enableKidMode.txt())
            )
          ,
          br,
          br,
          p(
            trans.inKidModeThePlayStrategyLogoGetsIconX(
              span(cls := "kiddo", title := trans.kidMode.txt())(":)")
            )
          )
        ),
        page.map { case (doc, resolver) =>
          st.section(cls := "box box-pad page kid-mode-page")(views.html.site.page.pageContent(doc, resolver))
        }
      )
    }
}
