package views.html.streamer

import play.api.i18n.Lang

import controllers.routes
import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.user.User

object bits extends Context.ToLang {

  import trans.streamer._

  def create(implicit ctx: Context) =
    views.html.site.message(
      title = becomeStreamer.txt(),
      icon = Some(""),
      moreCss = cssTag("streamer.form").some
    )(
      postForm(cls := "streamer-new", action := routes.Streamer.create)(
        h2(doYouHaveStream()),
        br,
        br,
        bits.rules,
        br,
        br,
        p(style := "text-align: center")(
          submitButton(cls := "button button-fat text", dataIcon := "")(hereWeGo())
        )
      )
    )

  def pic(s: lila.streamer.Streamer, u: User, size: Int = 300) =
    s.picturePath match {
      case Some(path) =>
        img(
          width := size,
          height := size,
          cls := "picture",
          src := dbImageUrl(path.value),
          alt := s"${u.titleUsername} PlayStrategy streamer picture"
        )
      case _ =>
        img(
          width := size,
          height := size,
          cls := "default picture",
          src := staticAssetUrl("images/placeholder.png"),
          alt := "Default PlayStrategy streamer picture"
        )
    }

  def menu(active: String, s: Option[lila.streamer.Streamer.WithUser])(implicit ctx: Context) =
    st.nav(cls := "subnav")(
      a(cls := active.active("index"), href := routes.Streamer.index())(allStreamers()),
      s.map { st =>
        frag(
          a(cls := active.active("show"), href := routes.Streamer.show(st.streamer.id.value))(
            st.streamer.name
          ),
          (ctx.is(st.user) || isGranted(_.Streamers)) option
            a(cls := active.active("edit"), href := s"${routes.Streamer.edit}?u=${st.streamer.id.value}")(
              editPage()
            )
        )
      } getOrElse a(href := routes.Streamer.edit)(yourPage()),
      isGranted(_.Streamers) option a(
        cls := active.active("requests"),
        href := s"${routes.Streamer.index()}?requests=1"
      )("Approval requests"),
      a(
        dataIcon := "",
        cls := "text",
        href := "/blog/Y3zSrBEAACEAECTj/join-the-playstrategy-streamer-community"
      )(
        "Streamer community"
      ),
      a(href := "/about")(downloadKit())
    )

  def redirectLink(username: String, isStreaming: Option[Boolean] = None) =
    isStreaming match {
      case Some(false) => a(href := routes.Streamer.show(username))
      case _ =>
        a(
          href := routes.Streamer.redirect(username),
          targetBlank,
          noFollow
        )
    }

  def liveStreams(l: lila.streamer.LiveStreams.WithTitles): Frag =
    l.live.streams.map { s =>
      redirectLink(s.streamer.id.value)(
        cls := "stream highlight",
        title := s.status
      )(
        strong(cls := "text", dataIcon := "")(l titleName s),
        " ",
        s.cleanStatus
      )
    }

  def contextual(userId: User.ID)(implicit lang: Lang): Frag =
    redirectLink(userId)(cls := "context-streamer text", dataIcon := "")(
      xIsStreaming(usernameOrId(userId))
    )

  def rules(implicit lang: Lang) =
    ul(cls := "streamer-rules")(
      h2(trans.streamer.rules()),
      ul(
        li(rule1()),
        li(rule2()),
        li(a(href := routes.Page.lonePage("streamer-page-activation"))(rule3()))
      ),
      h2(perks()),
      ul(
        li(perk1()),
        li(perk2()),
        li(perk3()),
        li(perk4())
      )
    )
}
