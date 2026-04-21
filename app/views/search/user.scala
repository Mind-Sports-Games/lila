package views.html.search

import play.api.data.Form

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.user.User

object user {

  import trans.search.*

  def apply(u: User, form: Form[?])(implicit ctx: Context) = {
    val commons = bits.of(form)
    st.form(
      noFollow,
      cls    := "search__form",
      action := routes.User.games(u.username, "search"),
      method := "GET"
    )(commons.dataReqs)(
      table(
        commons.date,
        commons.rating,
        commons.turns,
        commons.duration,
        commons.clockTime,
        commons.clockIncrement,
        commons.source,
        commons.perf,
        commons.mode
      ),
      table(
        commons.hasAi,
        commons.aiLevel,
        tr(cls := "opponentName")(
          th(label(`for` := form3.id(form("players")("b")))(opponentName())),
          td(cls := "usernames")(
            st.input(tpe                          := "hidden", value := u.id, name := "players.a"),
            form3.input(form("players")("b"))(tpe := "text")
          )
        ),
        commons.winner(hide = false),
        commons.loser(hide = false),
        commons.playerIndexs(hide = false),
        commons.status,
        commons.winnerPlayerIndex,
        commons.sort,
        commons.analysed,
        tr(cls := "action")(
          th,
          td(button(cls := "button")(search()))
        )
      )
    )
  }
}
