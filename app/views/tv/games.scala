package views.html.tv

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*

object games {

  def apply(channel: lila.tv.Tv.Channel, povs: List[lila.game.Pov], champions: lila.tv.Tv.Champions)(implicit
      ctx: Context
  ) =
    // no openGraph: the card would describe a list whose contents turn over every few
    // minutes. What gets shared is a game, and a game page carries its own.
    views.html.base.layout(
      title = s"${channel.name} • ${trans.currentGames.txt()}",
      moreCss = cssTag("tv.games")
    ) {
      main(cls := "page-menu tv-games")(
        st.aside(cls := "page-menu__menu")(
          side.channels(channel, champions, "/games")
        ),
        div(cls := "page-menu__content now-playing")(
          h1(cls := "offscreen")(channel.name),
          povs map { views.html.game.mini(_) }
        )
      )
    }
}
