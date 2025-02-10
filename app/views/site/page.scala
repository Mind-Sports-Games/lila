package views.html.site

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import strategygames.{ GameFamily }

object page {

  def lone(doc: io.prismic.Document, resolver: io.prismic.DocumentLinkResolver)(implicit ctx: Context) =
    views.html.base.layout(
      moreCss = cssTag("page"),
      title = ~doc.getText("pages.title")
    ) {
      main(cls := "page-small box box-pad page")(pageContent(doc, resolver))
    }

  def withMenu(active: String, doc: io.prismic.Document, resolver: io.prismic.DocumentLinkResolver)(implicit
      ctx: Context
  ) =
    layout(
      title = ~doc.getText("pages.title"),
      active = active,
      contentCls = "page box box-pad",
      moreCss = cssTag("page")
    )(pageContent(doc, resolver))

  private def pageContent(doc: io.prismic.Document, resolver: io.prismic.DocumentLinkResolver) = frag(
    h1(doc.getText("pages.title")),
    div(cls := "body")(
      raw {
        ~doc
          .getHtml("pages.content", resolver)
          .map(lila.blog.BlogTransform.markdown.apply)
      }
    )
  )

  def source(doc: io.prismic.Document, resolver: io.prismic.DocumentLinkResolver)(implicit ctx: Context) = {
    val title = ~doc.getText("pages.title")
    layout(
      title = title,
      active = "source",
      moreCss = frag(cssTag("source")),
      contentCls = "page",
      moreJs = embedJsUnsafeLoadThen(
        """$('#asset-version-date').text(window.playstrategy.info.date);
$('#asset-version-commit').attr('href', 'https://github.com/Mind-Sports-Games/lila/commits/' + window.playstrategy.info.commit).find('pre').text(window.playstrategy.info.commit);
$('#asset-version-message').text(window.playstrategy.info.message);"""
      )
    )(
      frag(
        st.section(cls := "box box-pad body")(
          h1(title),
          raw(~doc.getHtml("pages.content", resolver))
        ),
        br,
        st.section(cls := "box")(
          h1(id := "version")("lila version"),
          table(cls := "slist slist-pad")(
            env.appVersionDate zip env.appVersionCommit zip env.appVersionMessage map {
              case ((date, commit), message) =>
                tr(
                  td("Server"),
                  td(date),
                  td(a(href := s"https://github.com/Mind-Sports-Games/lila/commits/$commit")(pre(commit))),
                  td(message)
                )
            },
            tr(
              td("Assets"),
              td(id := "asset-version-date"),
              td(a(id := "asset-version-commit")(pre)),
              td(id := "asset-version-message")
            ),
            tr(
              td("Boot"),
              td(colspan := 3)(momentFromNow(lila.common.Uptime.startedAt))
            )
          )
        ),
        br,
        st.section(cls := "box")(freeJs())
      )
    )
  }

  def webmasters(implicit ctx: Context) = {
    val parameters = frag(
      p("Parameters:"),
      ul(
        li(strong("bg"), ": light, dark"),
        li(strong("gf"), ": ", GameFamily.all.map(gf => s"${gf.id} (${gf.key})").mkString(", ")),
        li(
          strong("theme"),
          ": ",
          GameFamily.all
            .map(gf => s"(${gf.key}) " + lila.pref.Theme.allOfFamily(gf).map(_.name).mkString(", "))
            .mkString("; ")
        ),
        li(
          strong("pieceSet"),
          ": ",
          GameFamily.all
            .map(gf => s"(${gf.key}) " + lila.pref.PieceSet.allOfFamily(gf).map(_.name).mkString(", "))
            .mkString("; ")
        )
      )
    )
    layout(
      title = "Webmasters",
      active = "webmasters",
      moreCss = cssTag("page"),
      contentCls = "page"
    )(
      frag(
        div(cls := "box box-pad developers body")(
          h1("HTTP API"),
          p(
            raw(
              """PlayStrategy exposes a RESTish HTTP/JSON API that you are welcome to use. Read the <a href="/api" class="blue">HTTP API documentation</a>.
              <br/>
              <strong>Note</strong> - Game embed not working for draughts"""
            )
          )
        ),
        br,
        div(cls := "box box-pad developers body") {
          val args = """style="width: 400px; height: 444px;" allowtransparency="true" frameborder="0""""
          frag(
            h1(id := "embed-tv")("Embed PlayStrategy TV in your site"),
            div(cls := "center")(raw(s"""<iframe src="/tv/frame?theme=auto&bg=auto" $args></iframe>""")),
            p("Add the following HTML to your site:"),
            p(cls := "copy-zone")(
              input(
                id := "tv-embed-src",
                cls := "copyable autoselect",
                value := s"""<iframe src="$netBaseUrl/tv/frame?theme=auto&bg=auto" $args></iframe>"""
              ),
              button(title := "Copy code", cls := "copy button", dataRel := "tv-embed-src", dataIcon := "\"")
            ),
            parameters
          )
        },
        br,
        // div(cls := "box box-pad developers body") {
        //   val args = """style="width: 400px; height: 444px;" allowtransparency="true" frameborder="0""""
        //   frag(
        //     h1(id := "embed-puzzle")("Embed the daily puzzle in your site"),
        //     div(cls := "center")(
        //       raw(s"""<iframe src="/training/frame?theme=brown&bg=dark" $args></iframe>""")
        //     ),
        //     p("Add the following HTML to your site:"),
        //     p(cls := "copy-zone")(
        //       input(
        //         id := "puzzle-embed-src",
        //         cls := "copyable autoselect",
        //         value := s"""<iframe src="$netBaseUrl/training/frame?theme=brown&bg=dark" $args></iframe>"""
        //       ),
        //       button(
        //         title := "Copy code",
        //         cls := "copy button",
        //         dataRel := "puzzle-embed-src",
        //         dataIcon := "\""
        //       )
        //     ),
        //     parameters,
        //     p("The text is automatically translated to your visitor's language."),
        //     p(
        //       "Alternatively, you can ",
        //       a(href := routes.Main.dailyPuzzleSlackApp)("post the puzzle in your slack workspace"),
        //       "."
        //     )
        //   )
        // },
        // br,
        div(cls := "box box-pad developers body") {
          val args = """style="width: 600px; height: 397px;" frameborder="0""""
          frag(
            h1(id := "embed-study")("Embed a chess analysis in your site"),
            raw(s"""<iframe src="/study/embed/3H4yU2WE/3H4yU2WE?bg=auto&theme=auto" $args></iframe>"""),
            p(
              "Create ",
              a(href := routes.Study.allDefault(1))("a study"),
              ", then click the share button to get the HTML code for the current chapter."
            ),
            parameters,
            p("The text is automatically translated to your visitor's language.")
          )
        },
        br,
        div(cls := "box box-pad developers body") {
          val args = """style="width: 600px; height: 397px;" frameborder="0""""
          frag(
            h1("Embed a game in your site"),
            raw(s"""<iframe src="/embed/0kpSHmXZ?bg=auto&theme=auto" $args></iframe>"""),
            p(
              raw(
                """On a game analysis page, click the <em>"FEN &amp; PGN"</em> tab at the bottom, then """
              ),
              "\"",
              em(trans.embedInYourWebsite(), "\".")
            ),
            parameters,
            p("The text is automatically translated to your visitor's language.")
          )
        }
      )
    )
  }

  def layout(
      title: String,
      active: String,
      contentCls: String = "",
      moreCss: Frag = emptyFrag,
      moreJs: Frag = emptyFrag
  )(body: Frag)(implicit ctx: Context) =
    views.html.base.layout(
      title = title,
      moreCss = moreCss,
      moreJs = moreJs
    ) {
      val sep                  = div(cls := "sep")
      val external             = frag(" ", i(dataIcon := "0"))
      def activeCls(c: String) = cls := active.activeO(c)
      main(cls := "page-menu")(
        st.nav(cls := "page-menu__menu subnav")(
          a(activeCls("about"), href := "/about")(trans.aboutX("playstrategy.org")),
          a(activeCls("faq"), href := routes.Main.faq)(trans.faq.faqAbbreviation()),
          a(activeCls("contact"), href := routes.Main.contact)(trans.contact.contact()),
          a(activeCls("tos"), href := routes.Page.tos)(trans.termsOfService()),
          a(activeCls("privacy"), href := "/privacy")(trans.privacy()),
          //a(activeCls("master"), href := routes.Page.master)("Title verification"),
          sep,
          a(activeCls("source"), href := routes.Page.source)(trans.sourceCode()),
          a(activeCls("help"), href := routes.Page.help)(trans.contribute()),
          a(activeCls("changelog"), href := routes.Page.menuPage("changelog"))("Changelog"),
          a(activeCls("thanks"), href := "/thanks")(trans.thankYou()),
          sep,
          a(activeCls("webmasters"), href := routes.Main.webmasters)(trans.webmasters()),
          //a(activeCls("database"), href := "https://database.playstrategy.org")(trans.database(), external),
          a(activeCls("api"), href := routes.Api.index)("API", external),
          sep,
          a(activeCls("lag"), href := routes.Main.lag)(trans.lag.isPlayStrategyLagging())
          //a(activeCls("ads"), href := "/ads")("Block ads")
        ),
        div(cls := s"page-menu__content $contentCls")(body)
      )
    }
}
