package controllers

import strategygames.format.FEN
import strategygames.{ Player => PlayerIndex, Replay, P1 }
import play.api.mvc._
import views._

import lila.api.Context
import lila.app._
import lila.common.HTTPRequest
import lila.game.{ PgnDump, Pov, SgfDump }
import lila.round.JsonView.WithFlags

final class Analyse(
    env: Env,
    gameC: => Game,
    roundC: => Round
) extends LilaController(env) {

  def requestAnalysis(id: String) =
    Auth { implicit ctx => me =>
      OptionFuResult(env.game.gameRepo game id) { game =>
        env.fishnet.analyser(
          game,
          lila.fishnet.Work.Sender(
            userId = me.id,
            ip = HTTPRequest.ipAddress(ctx.req).some,
            mod = isGranted(_.Hunter) || isGranted(_.Relay),
            system = false
          )
        ) map {
          case true  => NoContent
          case false => Unauthorized
        }
      }
    }

  def replay(pov: Pov, userTv: Option[lila.user.User])(implicit ctx: Context) =
    if (HTTPRequest isCrawler ctx.req) replayBot(pov)
    else
      env.game.gameRepo initialFen pov.gameId flatMap { initialFen =>
        gameC.preloadUsers(pov.game) >> redirectAtFen(pov, initialFen) {
          (env.analyse.analyser get pov.game) zip
            (!pov.game.metadata.analysed ?? env.fishnet.api.userAnalysisExists(pov.gameId)) zip
            (pov.game.simulId ?? env.simul.repo.find) zip
            roundC.getWatcherChat(pov.game) zip
            (ctx.noBlind ?? env.game.crosstableApi.withMatchup(pov.game)) zip
            env.bookmark.api.exists(pov.game, ctx.me) zip
            env.swiss.api.getSwissPairingGamesForGame(pov.game) zip
            env.api.pgnDump(
              pov.game,
              initialFen,
              analysis = none,
              PgnDump.WithFlags(clocks = false)
            ) zip
            env.api.sgfDump(
              pov.game,
              initialFen,
              PgnDump.WithFlags(clocks = false)
            ) flatMap {
              case (
                    (
                      (
                        (((((analysis, analysisInProgress), simul), chat), crosstable), bookmarked),
                        swissPairingGames
                      ),
                      pgn
                    ),
                    sgf
                  ) =>
                env.api.roundApi.review(
                  pov,
                  lila.api.Mobile.Api.currentVersion,
                  tv = userTv.map { u =>
                    lila.round.OnUserTv(u.id)
                  },
                  analysis,
                  initialFenO = initialFen.some,
                  withFlags = WithFlags(
                    plytimes = true,
                    clocks = true,
                    division = true,
                    opening = true
                  )
                ) map { data =>
                  EnableSharedArrayBuffer(
                    Ok(
                      html.analyse.replay(
                        pov,
                        data,
                        initialFen,
                        env.analyse.annotator(pgn, pov.game, analysis).toString,
                        sgf,
                        analysis,
                        analysisInProgress,
                        simul,
                        crosstable,
                        userTv,
                        chat,
                        bookmarked = bookmarked,
                        swissPairingGames = swissPairingGames
                      )
                    )
                  )
                }
            }
        }
      }

  def embed(gameId: String, playerIndex: String) =
    Open { implicit ctx: Context =>
      env.game.gameRepo.gameWithInitialFen(gameId) flatMap {
        case Some((game, initialFen)) =>
          val pov = Pov(game, PlayerIndex.fromName(playerIndex) | P1)
          env.api.roundApi.embed(
            pov,
            lila.api.Mobile.Api.currentVersion,
            initialFenO = initialFen.some,
            withFlags = WithFlags(opening = true)
          ) map { data =>
            Ok(html.analyse.embed(pov, data)(ui.EmbedConfig.fromContext(ctx)))
          }
        case _ => fuccess(NotFound(html.analyse.embed.notFound(ui.EmbedConfig.fromContext(ctx))))
      } dmap EnableSharedArrayBuffer
    }

  private def redirectAtFen(pov: Pov, initialFen: Option[FEN])(or: => Fu[Result])(implicit ctx: Context) =
    get("fen").map(s => FEN.clean(pov.game.variant.gameLogic, s)).fold(or) { atFen =>
      val url = routes.Round.watcher(pov.gameId, pov.playerIndex.name)
      fuccess {
        //TODO: This function (plyAtFen) wont work for non chess/draughts
        Replay
          .plyAtFen(pov.game.variant.gameLogic, pov.game.actionStrs, initialFen, pov.game.variant, atFen)
          .fold(
            err => {
              lila.log("analyse").info(s"redirectAtFen: ${pov.gameId} $atFen $err")
              Redirect(url)
            },
            ply => Redirect(s"$url#$ply")
          )
      }
    }

  private def replayBot(pov: Pov)(implicit ctx: Context) =
    for {
      initialFen <- env.game.gameRepo initialFen pov.gameId
      analysis   <- env.analyse.analyser get pov.game
      simul      <- pov.game.simulId ?? env.simul.repo.find
      crosstable <- env.game.crosstableApi.withMatchup(pov.game)
      pgn        <- env.api.pgnDump(pov.game, initialFen, analysis, PgnDump.WithFlags(clocks = false))
      sgf        <- env.api.sgfDump(pov.game, initialFen, PgnDump.WithFlags(clocks = false))
    } yield Ok(
      html.analyse.replayBot(
        pov,
        initialFen,
        env.analyse.annotator(pgn, pov.game, analysis).toString,
        sgf,
        simul,
        crosstable
      )
    )
}
