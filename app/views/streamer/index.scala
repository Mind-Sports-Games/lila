package views.html.streamer

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.common.paginator.Paginator

object index {

  import trans.streamer.*

  private val dataDedup = attr("data-dedup")

  def apply(
      live: List[lila.streamer.Streamer.WithUserAndStream],
      pager: Paginator[lila.streamer.Streamer.WithUser],
      requests: Boolean
  )(implicit ctx: Context) = {

    val title = if requests then "Streamer approval requests" else playstrategyStreamers.txt()

    def widget(s: lila.streamer.Streamer.WithUser, stream: Option[lila.streamer.Stream]) =
      frag(
        if requests then a(href := s"${routes.Streamer.edit}?u=${s.user.username}", cls := "overlay")
        else bits.redirectLink(s.user.username, stream.isDefined.some)(cls := "overlay"),
        stream.isDefined.option(span(cls := "ribbon")(span(trans.streamer.live()))),
        bits.pic(s.streamer, s.user),
        div(cls := "overview")(
          h1(dataIcon := "")(titleTag(s.user.title), s.streamer.name),
          s.streamer.headline.map(_.value).map { d =>
            p(
              cls := s"headline ${
                  if d.length < 60 then "small" else if d.length < 120 then "medium" else "large"
                }"
            )(d)
          },
          div(cls := "services")(
            s.streamer.twitch.map { twitch =>
              div(cls := "service twitch")(twitch.minUrl)
            },
            s.streamer.youTube.map { youTube =>
              div(cls := "service youTube")(youTube.minUrl)
            }
          ),
          div(cls := "ats")(
            stream.map { s =>
              p(cls := "at")(
                currentlyStreaming(strong(s.cleanStatus))
              )
            } getOrElse frag(
              p(cls := "at")(trans.lastSeenActive(momentFromNow(s.streamer.seenAt))),
              s.streamer.liveAt.map { liveAt =>
                p(cls := "at")(lastStream(momentFromNow(liveAt)))
              }
            )
          )
        )
      )

    views.html.base.layout(
      title = title,
      moreCss = cssTag("streamer.list"),
      moreJs = infiniteScrollTag
    ) {
      main(cls := "page-menu")(
        bits.menu(if requests then "requests" else "index", none)(using ctx)(cls := " page-menu__menu"),
        div(cls := "page-menu__content box streamer-list")(
          h1(dataIcon := "", cls := "text")(title),
          (!requests).option(
            div(cls := "list live")(
              live.map { s =>
                st.article(cls := "streamer")(widget(s.withoutStream, s.stream))
              }
            )
          ),
          div(cls := "list infinite-scroll")(
            (live.size % 2 == 1).option(div(cls := "none")),
            pager.currentPageResults.map { s =>
              st.article(cls := "streamer paginated", dataDedup := s.streamer.id.value)(widget(s, none))
            },
            pagerNext(
              pager,
              np =>
                addQueryParameter(
                  addQueryParameter(routes.Streamer.index().url, "page", np),
                  "requests",
                  if requests then 1 else 0
                )
            )
          )
        )
      )
    }
  }
}
