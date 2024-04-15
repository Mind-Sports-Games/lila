package views.html.lobby

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import strategygames.variant.Variant
import strategygames.GameGroup
import lila.i18n.VariantKeys
import scala.util.Random

object bits {

  val lobbyApp = div(cls := "lobby__app")(
    div(cls := "tabs-horiz")(span(nbsp)),
    div(cls := "lobby__app__content")
  )

  def underboards(
      tours: List[lila.tournament.Tournament],
      simuls: List[lila.simul.Simul],
      leaderboard: List[lila.user.User.LightPerf],
      tournamentWinners: List[lila.tournament.Winner]
  )(implicit ctx: Context) =
    frag(
      /*div(cls := "lobby__leaderboard lobby__box")(
        div(cls := "lobby__box__top")(
          h2(cls := "title text", dataIcon := "C")(trans.leaderboard()),
          a(cls := "more", href := routes.User.list)(trans.more(), " »")
        ),
        div(cls := "lobby__box__content")(
          table(
            tbody(
              leaderboard map { l =>
                tr(
                  td(lightUserLink(l.user)),
                  lila.rating.PerfType(l.perfKey) map { pt =>
                    td(cls := "text", dataIcon := pt.iconChar)(l.rating)
                  },
                  td(ratingProgress(l.progress))
                )
              }
            )
          )
        )
      ),*/
      div(cls := "lobby__winners lobby__box")(
        div(cls := "lobby__box__top")(
          h2(cls := "title text", dataIcon := "g")(trans.tournamentWinners()),
          a(cls := "more", href := routes.Tournament.leaderboard)(trans.more(), " »")
        ),
        div(cls := "lobby__box__content")(
          table(
            tbody(
              tournamentWinners take 10 map { w =>
                tr(
                  td(userIdLink(w.userId.some)),
                  td(
                    a(cls := "color-choice", title := w.tourName, href := routes.Tournament.show(w.tourId))(
                      scheduledTournamentNameShortHtml(w.tourName)
                    )
                  )
                )
              }
            )
          )
        )
      ),
      div(cls := "lobby__tournaments lobby__box")(
        a(cls := "lobby__box__top", href := routes.Tournament.home)(
          h2(cls := "title text", dataIcon := "g")(trans.openTournaments()),
          span(cls := "more")(trans.more(), " »")
        ),
        div(cls := "enterable_list lobby__box__content")(
          views.html.tournament.bits.enterable(tours.take(10))
        )
      ),
      simuls.nonEmpty option div(cls := "lobby__simuls lobby__box")(
        a(cls := "lobby__box__top", href := routes.Simul.home)(
          h2(cls := "title text", dataIcon := "f")(trans.simultaneousExhibitions()),
          span(cls := "more")(trans.more(), " »")
        ),
        div(cls := "enterable_list lobby__box__content")(
          views.html.simul.bits.allCreated(simuls)
        )
      )
    )

  def lastPosts(posts: List[lila.blog.MiniPost])(implicit ctx: Context): Option[Frag] =
    posts.nonEmpty option
      div(cls := "lobby__blog blog-post-cards")(
        posts map { post =>
          a(cls := "blog-post-card blog-post-card--link", href := routes.Blog.show(post.id, post.slug))(
            div(cls := "blog-post-card__container")(
              img(
                src := post.image,
                cls := "blog-post-card__image",
                widthA := 400,
                heightA := 400 * 10 / 16
              ),
              span(cls := "blog-post-card__content")(
                h2(cls := "blog-post-card__title")(post.title),
                semanticDate(post.date)(ctx.lang)(cls := "blog-post-card__over-image")
              )
            )
          )
        }
      )

  def playbanInfo(ban: lila.playban.TempBan)(implicit ctx: Context) =
    nopeInfo(
      h1(trans.sorry()),
      p(trans.weHadToTimeYouOutForAWhile()),
      p(trans.timeoutExpires(strong(secondsFromNow(ban.remainingSeconds)))),
      h2(trans.why()),
      p(
        trans.pleasantChessExperience(),
        br,
        trans.goodPractice(),
        br,
        trans.potentialProblem()
      ),
      h2(trans.howToAvoidThis()),
      ul(
        li(trans.playEveryGame()),
        li(trans.tryToWin()),
        li(trans.resignLostGames())
      ),
      p(
        trans.temporaryInconvenience(),
        br,
        trans.wishYouGreatGames(),
        br,
        trans.thankYouForReading()
      )
    )

  def currentGameInfo(current: lila.app.mashup.Preload.CurrentGame) =
    nopeInfo(
      h1("Hang on!"),
      p("You have a game in progress with ", strong(current.opponent), "."),
      br,
      br,
      a(cls := "text button button-fat", dataIcon := "G", href := routes.Round.player(current.pov.fullId))(
        "Join the game"
      ),
      br,
      br,
      "or",
      br,
      br,
      postForm(action := routes.Round.resign(current.pov.fullId))(
        button(cls := "text button button-red", dataIcon := "L")(
          if (current.pov.game.abortable) "Abort" else "Resign",
          " the game"
        )
      ),
      br,
      p("You can't start a new game until this one is finished.")
    )

  def nopeInfo(content: Modifier*) =
    frag(
      div(cls := "lobby__app"),
      div(cls := "lobby__nope")(
        st.section(cls := "lobby__app__content")(content)
      )
    )

  def spotlight(e: lila.event.Event)(implicit ctx: Context) =
    a(
      href := (if (e.isNow || !e.countdown) e.url else routes.Event.show(e.id).url),
      cls := List(
        s"tour-spotlight event-spotlight id_${e.id}" -> true,
        "invert"                                     -> e.isNowOrSoon
      )
    )(
      views.html.event.iconOf(e),
      span(cls := "content")(
        span(cls := "name")(e.title),
        span(cls := "headline")(e.headline),
        span(cls := "more")(
          if (e.isNow) e.duringMessage.fold(trans.eventInProgress())(m => raw(m))
          else e.beforeMessage.fold[Frag](momentFromNow(e.startsAt))(m => raw(m))
        )
      )
    )

  def weeklyChallenge(weekCha: lila.lobby.WeeklyChallenge)(implicit ctx: Context) =
    div(cls := "lobby__weekcha lobby__box")(
      div(cls := "lobby__box__top")(
        h2(cls := "title text", dataIcon := '')(trans.weeklyChallenge()),
        a(cls := "more", href := "/blog/ZAthlBAAACMA7gGg/weekly-challenges")(trans.more(), " »")
      ),
      div(cls := "current_week")(
        a(href := s"/forum/weekly-challenges/${weekCha.currentKey}")(
          iconTag(weekCha.currentIconChar),
          weekCha.currentName
        )
      ),
      div(cls := "previous_week")(
        a(cls := "last_week", href := s"/forum/weekly-challenges/${weekCha.previousKey}")(
          iconTag(weekCha.previousIconChar),
          weekCha.previousName
        ),
        userIdLink(weekCha.winner.some, dataIcon = 'g'.some)
      )
    )

  def gameList(implicit ctx: Context) =
    div(cls := "lobby__gamelist lobby__box")(
      div(cls := "lobby__box__top")(
        h2(cls := "title text")(trans.learnHowToPlay()),
        a(cls := "more", href := "/variant")(trans.more(), " »")
      ),
      div(cls := "game-icon-area")(
        div(cls := "arrow", title := "Prev", id := "slideLeft", dataIcon := "I")(),
        div(cls := "game-icon-list", id := "game-icons-container")(
          variantsOrdered
            .map(v =>
              a(cls := "game-icon color-choice", href := s"/variant/${v.key}")(
                i(cls := "img", dataIcon := v.perfIcon),
                p(cls := "text")(VariantKeys.variantName(v))
              )
            )
        ),
        div(cls := "arrow", title := "Next", id := "slideRight", dataIcon := "H")()
      )
    )

  private def variantsOrdered: List[Variant] = {
    val randomOrder     = Random.shuffle(Variant.all.filterNot(_.fromPositionVariant))
    val gameGroups      = GameGroup.medley.filter(gg => gg.variants.exists(randomOrder.contains(_)))
    val onePerGameGroup = generateOnePerGameGroup(randomOrder, gameGroups)
    val newOrder        = onePerGameGroup ::: randomOrder.filterNot(onePerGameGroup.contains(_))
    newOrder ::: newOrder ::: newOrder // 3 copies for infinte scroll
  }

  private def generateOnePerGameGroup(variants: List[Variant], gameGroups: List[GameGroup]) =
    Random.shuffle(
      gameGroups.map(gg =>
        Random
          .shuffle(
            gg.variants.filter(v => variants.contains(v))
          )
          .head
      )
    )

}
