package controllers

import akka.pattern.ask
import play.api.data.*, Forms.*
import play.api.libs.json.*
import play.api.mvc.*
import scala.annotation.nowarn

import lila.app.{ *, given }
import lila.common.HTTPRequest
import lila.hub.actorApi.captcha.ValidCaptcha
private given akka.util.Timeout = akka.util.Timeout(5.seconds)
import views.*

final class Main(
    env: Env,
    @annotation.nowarn("msg=unused") prismicC: Prismic,
    assetsC: ExternalAssets
) extends LilaController(env) {

  private lazy val blindForm = Form(
    tuple(
      "enable"   -> nonEmptyText,
      "redirect" -> nonEmptyText
    )
  )

  def toggleBlindMode =
    OpenBody { implicit ctx =>
      implicit val req = ctx.body
      fuccess {
        blindForm
          .bindFromRequest()
          .fold(
            _ => BadRequest,
            { case (enable, redirect) =>
              Redirect(redirect).withCookies(
                env.lilaCookie.cookie(
                  env.api.config.accessibility.blindCookieName,
                  if (enable == "0") "" else env.api.config.accessibility.hash,
                  maxAge = env.api.config.accessibility.blindCookieMaxAge.toSeconds.toInt.some,
                  httpOnly = true.some
                )
              )
            }
          )
      }
    }

  def handlerNotFound(req: play.api.mvc.RequestHeader) = reqToCtx(req) map renderNotFound

  def captchaCheck(id: String) =
    Open { implicit ctx =>
      env.hub.captcher.actor ? ValidCaptcha(id, ~get("solution")) map { case valid: Boolean =>
        Ok(if (valid) 1 else 0)
      }
    }

  def webmasters =
    Open { implicit ctx =>
      pageHit
      fuccess {
        html.site.page.webmasters
      }
    }

  def lag =
    Open { implicit ctx =>
      pageHit
      fuccess {
        html.site.lag()
      }
    }

  /*
  def mobile =
    Open { implicit ctx =>
      pageHit
      OptionOk(prismicC getPage "mobile-apk") { case (doc, resolver) =>
        html.mobile(doc, resolver)
      }
    }
   */

  def dailyPuzzleSlackApp =
    Open { implicit ctx =>
      pageHit
      fuccess {
        html.site.dailyPuzzleSlackApp()
      }
    }

  def jslog(id: String) =
    Open { ctx =>
      env.round.selfReport(
        userId = ctx.userId,
        ip = HTTPRequest.ipAddress(ctx.req),
        fullId = lila.game.Game.FullId(id),
        name = get("n", ctx.req) | "?"
      )
      NoContent.fuccess
    }

  /** Event monitoring endpoint
    */
  def jsmon(event: String) =
    Action {
      lila.mon.http.jsmon(event).increment()
      NoContent
    }

  def image(id: String, @nowarn("msg=unused") hash: String, @nowarn("msg=unused") name: String) =
    Action.async {
      env.imageRepo
        .fetch(id)
        .map {
          case None        => NotFound
          case Some(image) =>
            lila.mon.http.imageBytes.record(image.size.toLong)
            Ok(image.data).withHeaders(
              CACHE_CONTROL -> "max-age=1209600"
            ).as(image.contentType.getOrElse("image/jpeg"))
        }
    }

  val robots = Action { (req: play.api.mvc.RequestHeader) =>
    Ok {
      if (env.net.crawlable && req.domain == env.net.domain.value) s"""User-agent: *
Allow: /
Disallow: /game/export/
Disallow: /games/export/
Allow: /game/export/gif/thumbnail/

User-agent: Twitterbot
Allow: /

Sitemap: ${env.net.baseUrl.value}${routes.Main.sitemap.url}
Sitemap: ${env.net.baseUrl.value}${routes.Blog.sitemapTxt.url}
"""
      else "User-agent: *\nDisallow: /"
    }
  }

  import scala.concurrent.duration.*
  import lila.memo.CacheApi.*
  // every game's library page, rules page, study listing and analysis board, its puzzle trainer and
  // theme index where it has one, every tournament series, the static hubs, and the studies a
  // crawler is allowed to see (Study.notable). Excluded on purpose: the puzzle theme hubs
  // (/training/<variant>/<theme>), which 404 for a visitor with no puzzle in that theme.
  def sitemap = Action.async {
    if (!env.net.crawlable) fuccess(NotFound)
    else sitemapCache.getUnit map { Ok(_).as(XML).withHeaders(CACHE_CONTROL -> "max-age=86400") }
  }

  private val sitemapCache = env.memo.cacheApi.unit[String] {
    _.refreshAfterWrite(1.day)
      .buildAsyncFuture { _ =>
        val Freq          = lila.tournament.Schedule.Freq
        val base          = env.net.baseUrl.value
        val variants      = strategygames.variant.Variant.all.filterNot(_.fromPositionVariant)
        val chessStandard = strategygames.variant.Variant.libStandard(strategygames.GameLogic.Chess())
        val freqs         = html.tournament.history.allFreqs
        // a shield's per-variant series page IS the shield page, which gets its own date below
        val shieldKeys = lila.tournament.TournamentShield.Category.all.map(_.key).toSet
        // the pages with a URL per locale, so the ones carrying hreflang alternates
        val localisedPaths = routes.UserAnalysis.index.url ::
          variants.map(v => routes.Library.variant(v.key).url) :::
          // only the variants that have an analysis board: draughts has none, so its pages
          // render a board with no pieces. The standard chess board canonicalises to
          // /analysis, already listed above.
          views.html.board.userAnalysis.analysisVariants
            .filterNot(_ == chessStandard)
            .map(v => routes.UserAnalysis.parseArg(v.key).url)
        val paths =
          List(
            "/",
            routes.Library.home.url,
            routes.Page.variantHome.url,
            routes.Tournament.home.url,
            routes.Tournament.shields.url,
            routes.Swiss.home.url,
            routes.Puzzle.base.url,
            routes.Study.allDefault().url,
            routes.Blog.index().url,
            routes.Editor.index.url
          ) :::
            variants.map(v => routes.Page.variant(v.key).url) :::
            variants.map(v => routes.Study.byVariantDefault(v.key).url) :::
            lila.puzzle.Puzzle.puzzleVariants.map(v => routes.Puzzle.home(v.key).url) :::
            lila.puzzle.Puzzle.puzzleVariants.map(v => routes.Puzzle.themes(v.key).url) :::
            freqs.map(f => html.tournament.history.url(f, none))
        for {
          studies <- env.study.studyRepo.notable(10000)
          shields <- env.tournament.shieldApi.history(none) // in memory, filled by /tournament/shields
          winners <- env.tournament.winners.all             // in memory, filled by the homepage
        } yield {
          def loc(path: String, lastmod: Option[org.joda.time.DateTime] = none, children: String = "") = {
            val mod = lastmod.fold("")(d => s"<lastmod>${d.toString("yyyy-MM-dd")}</lastmod>")
            s"  <url><loc>$base$path</loc>$mod$children</url>"
          }
          // the set the page itself links (views.html.base.layout): a crawler that has only seen the
          // sitemap still finds every localised URL
          def alternates(path: String) = lila.i18n.SeoLang
            .alternates(path)
            .map { case (tag, p) => s"""<xhtml:link rel="alternate" hreflang="$tag" href="$base$p"/>""" }
            .mkString
          // one page per series, dated by its last edition where a cache knows it
          val seriesUrls = freqs.filter(html.tournament.history.hasSeries).flatMap { freq =>
            variants.filterNot(v => freq == Freq.Shield && shieldKeys(v.key)).map { v =>
              val lastEdition = winners.variants.get(v.key).flatMap { w =>
                if (freq == Freq.Yearly) w.yearly
                else if (freq == Freq.GroupCycle) w.groupCycle
                else if (freq == Freq.Wildcard) w.wildcard
                else none
              }
              loc(html.tournament.history.url(freq, v.some), lastEdition.map(_.date))
            }
          }
          // a category with no award yet has no page
          val shieldUrls = lila.tournament.TournamentShield.Category.all.flatMap { categ =>
            shields.value.get(categ).flatMap(_.headOption) map { last =>
              loc(routes.Tournament.categShields(categ.key).url, last.date.some)
            }
          }
          val urls =
            paths.map(p => loc(p)) :::
              localisedPaths.map(p => loc(p, children = alternates(p))) :::
              seriesUrls :::
              shieldUrls :::
              studies.map(s => loc(routes.Study.show(s.id.value).url, s.updatedAt.some))
          s"""<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">
${urls.mkString("\n")}
</urlset>
"""
        }
      }
  }

  def manifest =
    Action {
      JsonOk {
        Json.obj(
          "name"                   -> env.net.domain.value,
          "short_name"             -> "PlayStrategy",
          "start_url"              -> "/",
          "display"                -> "standalone",
          "background_playerIndex" -> "#161512",
          "theme_playerIndex"      -> "#161512",
          "description"            -> "The (really) free, no-ads, open source chess server.",
          "icons"                  -> List(32, 64, 128, 192, 256, 512, 1024).map { size =>
            Json.obj(
              "src"   -> s"//${env.net.assetDomain.value}/assets/logo/playstrategy-favicon-$size.png",
              "sizes" -> s"${size}x$size",
              "type"  -> "image/png"
            )
          }
        )
      }.withHeaders(CACHE_CONTROL -> "max-age=1209600")
    }

  def getFishnet =
    Open { implicit ctx =>
      pageHit
      Ok(html.site.bits.getFishnet()).fuccess
    }

  def costs =
    Action { (req: play.api.mvc.RequestHeader) =>
      pageHit(req)
      Redirect("https://docs.google.com/spreadsheets/d/1Si3PMUJGR9KrpE5lngSkHLJKJkb0ZuI4/preview")
    }

  def verifyTitle =
    Action { (req: play.api.mvc.RequestHeader) =>
      pageHit(req)
      Redirect(
        "https://docs.google.com/forms/d/e/1FAIpQLSelXSHdiFw_PmZetxY8AaIJSM-Ahb5QnJcfQMDaiPJSf24lDQ/viewform"
      )
    }

  def contact =
    Open { implicit ctx =>
      pageHit
      Ok(html.site.contact()).fuccess
    }

  def faq =
    Open { implicit ctx =>
      pageHit
      Ok(html.site.faq()).fuccess
    }

  def movedPermanently(to: String) =
    Action {
      MovedPermanently(to)
    }

  def instantChess =
    Open { implicit ctx =>
      pageHit
      if (ctx.isAuth) fuccess(Redirect(routes.Lobby.home))
      else
        fuccess {
          Redirect(s"${routes.Lobby.home}#pool/3+2-standard").withCookies(
            env.lilaCookie.withSession { s =>
              s // + ("theme" -> "ic") //+ ("pieceSet" -> "icpieces") //these are both arrays now, and not really required
            }
          )
        }
    }

  def legacyQaQuestion(id: Int, @nowarn("msg=unused") slug: String) =
    Open { _ =>
      MovedPermanently {
        val faq = routes.Main.faq.url
        id match {
          case 103  => s"$faq#acpl"
          case 258  => s"$faq#marks"
          case 13   => s"$faq#titles"
          case 87   => routes.Stat.ratingDistribution("blitz").url
          case 110  => s"$faq#name"
          case 29   => s"$faq#titles"
          case 4811 => s"$faq#lm"
          // case 216  => routes.Main.mobile.url
          case 340 => s"$faq#trophies"
          case 6   => s"$faq#ratings"
          case 207 => s"$faq#hide-ratings"
          case 547 => s"$faq#leaving"
          case 259 => s"$faq#trophies"
          case 342 => s"$faq#provisional"
          case 50  => routes.Page.help.url
          case 46  => s"$faq#name"
          case 122 => s"$faq#marks"
          case _   => faq
        }
      }.fuccess
    }

  def devAsset(@nowarn("msg=unused") v: String, path: String, file: String) = assetsC.at(path, file)
}
