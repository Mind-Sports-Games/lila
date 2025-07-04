package views.html.challenge

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.challenge.Challenge
import lila.challenge.Challenge.Status

import controllers.routes

object theirs {

  def apply(
      c: Challenge,
      json: play.api.libs.json.JsObject,
      user: Option[lila.user.User],
      playerIndex: Option[strategygames.Player]
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = challengeTitle(c),
      openGraph = challengeOpenGraph(c).some,
      moreJs = bits.js(c, json, owner = false, playerIndex),
      moreCss = cssTag("challenge.page")
    ) {
      main(cls := "page-small challenge-page challenge-theirs box box-pad")(
        c.status match {
          case Status.Created | Status.Offline =>
            frag(
              h1(
                if (c.isOpen) c.name | "Open Challenge"
                else
                  user.fold[Frag]("Anonymous")(u =>
                    frag(
                      userLink(u),
                      " (",
                      u.perfs(c.perfType).glicko.display,
                      ")"
                    )
                  )
              ),
              bits.details(c, playerIndex),
              c.notableInitialFen.map { fen =>
                div(
                  cls := "board-preview",
                  views.html.board.bits.miniForVariant(fen, c.variant, !c.finalPlayerIndex)(div)
                )
              },
              if (playerIndex.map(Challenge.PlayerIndexChoice.apply).has(c.playerIndexChoice))
                badTag(
                  // very rare message, don't translate
                  s"You have the wrong playerIndex link for this open challenge. The ${playerIndex.??(_.name)} player has already joined."
                )
              else if (!c.mode.rated || ctx.isAuth) {
                frag(
                  (c.mode.rated && c.unlimited) option
                    badTag(trans.bewareTheGameIsRatedButHasNoClock()),
                  postForm(cls := "accept", action := routes.Challenge.accept(c.id, playerIndex.map(_.name)))(
                    submitButton(cls := "text button button-fat", dataIcon := "G")(trans.joinTheGame())
                  )
                )
              } else
                frag(
                  hr,
                  badTag(
                    p(trans.thisGameIsRated()),
                    a(
                      cls := "button",
                      href := s"${routes.Auth.login}?referrer=${routes.Round.watcher(c.id, "p1")}"
                    )(trans.signIn())
                  )
                )
            )
          case Status.Declined =>
            div(cls := "follow-up")(
              h1(trans.challenge.challengeDeclined()),
              bits.details(c, playerIndex),
              a(cls := "button button-fat button-color-choice", href := routes.Lobby.home)(
                trans.newOpponent()
              )
            )
          case Status.Accepted =>
            div(cls := "follow-up")(
              h1(trans.challenge.challengeAccepted()),
              bits.details(c, playerIndex),
              a(
                id := "challenge-redirect",
                href := routes.Round.watcher(c.id, "p1"),
                cls := "button button-fat button-color-choice"
              )(
                trans.joinTheGame()
              )
            )
          case Status.Canceled =>
            div(cls := "follow-up")(
              h1(trans.challenge.challengeCanceled()),
              bits.details(c, playerIndex),
              a(cls := "button button-fat button-color-choice", href := routes.Lobby.home)(
                trans.newOpponent()
              )
            )
        }
      )
    }
}
