package views.html.lobby

import controllers.routes
import play.api.libs.json.Json

import lila.api.Context
import lila.app.mashup.Preload.Homepage
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.String.html.safeJsonValue
import lila.game.Pov

object home {

  private val maxSpotlights: Int = 3

  def apply(homepage: Homepage)(implicit ctx: Context) = {
    import homepage._

    val chatJson = chatOption map { chat =>
      views.html.chat.json(
        chat.chat.getlast(250),
        name = trans.chatRoom.txt(),
        timeout = chat.timeout,
        public = true,
        resourceId = lila.chat.Chat.ResourceId("lobbyhome/lobbyhome")
      )
    }

    views.html.base.layout(
      title = "",
      fullTitle = Some {
        s"playstrategy.${if (netConfig.isProd) "org" else "dev"} • ${trans.playstrategySiteTitleShort.txt()}"
      },
      moreJs = frag(
        jsModule("lobby"),
        embedJsUnsafeLoadThen(
          s"""PlayStrategyLobby(${safeJsonValue(
            Json.obj(
              "data" -> data,
              "playban" -> playban.map { pb =>
                Json.obj(
                  "minutes"          -> pb.mins,
                  "remainingSeconds" -> (pb.remainingSeconds + 3)
                )
              },
              "i18n"              -> i18nJsObject(i18nKeys),
              "chat"              -> chatJson,
              "chatSocketVersion" -> chatVersion
            )
          )})"""
        )
      ),
      moreCss = cssTag("lobby"),
      chessground = false,
      openGraph = lila.app.ui
        .OpenGraph(
          image = staticAssetUrl("logo/playstrategy-tile-wide.png").some,
          twitterImage = staticAssetUrl("logo/playstrategy-tile.png").some,
          title = trans.playstrategySiteTitle.txt(),
          url = netBaseUrl,
          description = trans.playstrategySiteDescription.txt()
        )
        .some
    ) {
      main(
        cls := List(
          "lobby"            -> true,
          "lobby-nope"       -> (playban.isDefined || currentGame.isDefined),
          "lobby--no-simuls" -> simuls.isEmpty
        )
      )(
        div(cls := "lobby__table")(
          div(cls := "bg-switch", title := "Dark mode")(
            div(cls := "bg-switch__track"),
            div(cls := "bg-switch__thumb")
          ),
          div(cls := "lobby__start")(
            ctx.blind option h2("Play"),
            a(
              href := routes.Setup.gameForm(none),
              cls := List(
                "button button-color-choice config_game" -> true,
                "disabled"                               -> currentGame.isDefined
              ),
              trans.createAGame()
            )
          ),
          div(cls := "lobby__counters")(
            ctx.blind option h2("Counters"),
            a(
              id := "nb_connected_players",
              href := ctx.noBlind.option(routes.User.list.url)
            )(
              trans.nbPlayers(
                strong(dataCount := homepage.counters.members)(homepage.counters.members.localize)
              )
            ),
            a(
              id := "nb_connected_bots",
              href := ctx.noBlind.option(routes.PlayApi.botOnline.url)
            )(
              trans.nbBots(
                strong(dataCount := homepage.nbBots)(homepage.nbBots)
              )
            ),
            a(
              id := "nb_games_in_play",
              href := ctx.noBlind.option(routes.Tv.games.url)
            )(
              trans.nbLiveGamesInPlay(
                strong(dataCount := homepage.counters.rounds)(homepage.counters.rounds.localize)
              )
            )
          )
        ),
        div(cls := "lobby__chat")(
          chatOption.isDefined option frag(views.html.chat.frag)
        ),
        currentGame.map(bits.currentGameInfo) orElse
          playban.map(bits.playbanInfo) getOrElse {
            if (ctx.blind) blindLobby(blindGames)
            else bits.lobbyApp
          },
        div(cls := "lobby__side")(
          ctx.blind option h2("Highlights"),
          ctx.noKid option st.section(cls := "lobby__streams")(
            views.html.streamer.bits liveStreams streams,
            streams.live.streams.nonEmpty option a(href := routes.Streamer.index(), cls := "more")(
              trans.streamersMenu(),
              " »"
            )
          ),
          div(cls := "lobby__spotlights")(
            events.map(bits.spotlight),
            !ctx.isBot option frag(
              lila.tournament.Spotlight.select(tours, ctx.me, maxSpotlights - events.size) map {
                views.html.tournament.homepageSpotlight(_)
              },
              simuls.filter(isFeaturable) map views.html.simul.bits.homepageSpotlight
            )
          ),
          if (ctx.isAuth)
            div(cls := "timeline")(
              ctx.blind option h2("Timeline"),
              views.html.timeline entries userTimeline,
              userTimeline.nonEmpty option a(cls := "more", href := routes.Timeline.home)(
                trans.more(),
                " »"
              )
            )
          else
            div(cls := "about-side")(
              ctx.blind option h2("About"),
              trans.playstrategyAboutSummary("PlayStrategy"),
              " ",
              a(href := "/about")(trans.aboutX("PlayStrategy"), "...")
            )
        ),
        featured map { g =>
          div(cls := "lobby__tv")(
            views.html.game.mini(Pov naturalOrientation g, tv = (homepage.counters.rounds > 3))
          )
        },
        puzzle map { p =>
          views.html.puzzle.embed.dailyLink(p)(ctx.lang)(cls := "lobby__puzzle")
        },
        ctx.noBot option bits.underboards(tours, simuls, leaderboard, tournamentWinners),
        ctx.noKid option div(cls := "lobby__forum lobby__box")(
          a(cls := "lobby__box__top", href := routes.ForumCateg.index)(
            h2(cls := "title text", dataIcon := "d")(trans.latestForumPosts()),
            span(cls := "more")(trans.more(), " »")
          ),
          div(cls := "lobby__box__content")(
            views.html.forum.post recent forumRecent
          )
        ),
        bits.lastPosts(lastPost),
        ctx.noKid option bits.weeklyChallenge(weeklyChallenge),
        bits.gameList,
        div(cls := "lobby__info")(
          div(cls := "lobby__support")(
            a(href := routes.Plan.index)(
              iconTag(patronIconChar),
              span(cls := "lobby__support__text")(
                strong(trans.patron.donate()),
                span(trans.patron.becomePatron())
              )
            ),
            a(href := "https://mindsportsolympiad.com/product-category/merch/")(
              iconTag(""),
              span(cls := "lobby__support__text")(
                strong("Swag Store"),
                span(trans.playInStyle())
              )
            )
          ),
          div(cls := "lobby__about")(
            ctx.blind option h2("About"),
            a(href := "/about")(trans.aboutX("PlayStrategy")),
            a(href := "/faq")(trans.faq.faqAbbreviation()),
            a(href := "/contact")(trans.contact.contact()),
            //a(href := "/mobile")(trans.mobileApp()),
            a(href := routes.Page.tos)(trans.termsOfService()),
            a(href := "/privacy")(trans.privacy()),
            a(href := "/source")(trans.sourceCode()),
            //a(href := "/ads")("Ads"),
            views.html.base.bits.connectLinks
          )
        )
      )
    }
  }

  private val i18nKeys = List(
    trans.realTime,
    trans.byoyomiTime,
    trans.periods,
    trans.byoyomiInSeconds,
    trans.correspondence,
    trans.nbGamesInPlay,
    trans.nbLiveGamesInPlay,
    trans.player,
    trans.time,
    trans.joinTheGame,
    trans.cancel,
    trans.casual,
    trans.rated,
    trans.variant,
    trans.mode,
    trans.list,
    trans.graph,
    trans.filterGames,
    trans.youNeedAnAccountToDoThat,
    trans.oneDay,
    trans.nbDays,
    trans.aiNameLevelAiLevel,
    trans.yourTurn,
    trans.rating,
    trans.createAGame,
    trans.quickPairing,
    trans.lobby,
    trans.custom,
    trans.unlimited,
    trans.anonymous
  ).map(_.key)
}
