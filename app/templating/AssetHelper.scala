package lila.app
package templating

import play.api.mvc.RequestHeader

import lila.api.Context
import lila.app.ui.ScalatagsTemplate._
import lila.common.{ AssetVersion, ContentSecurityPolicy, Nonce }
import lila.web.AssetManifest

import strategygames.GameLogic

trait AssetHelper { self: I18nHelper with SecurityHelper =>

  private lazy val netDomain      = env.net.domain
  private lazy val assetDomain    = env.net.assetDomain
  private lazy val assetBaseUrl   = env.net.assetBaseUrl
  private lazy val baseUrl        = env.net.baseUrl
  private lazy val socketDomains  = env.net.socketDomains
  private lazy val minifiedAssets = env.net.minifiedAssets
  lazy val vapidPublicKey         = env.push.vapidPublicKey

  lazy val sameAssetDomain = netDomain.value == assetDomain.value

  def manifest: AssetManifest

  def assetVersion = AssetVersion.current

  def assetUrl(path: String): String       = s"$assetBaseUrl/assets/$path"
  def staticAssetUrl(path: String): String = s"$assetBaseUrl/assets/_$assetVersion/$path"

  def cdnUrl(path: String) = s"$assetBaseUrl$path"

  def dbImageUrl(path: String) = s"$baseUrl/image/$path"

  def updateManifest() = if (!env.net.isProd) env.web.manifest.update()

  def cssTag(name: String)(implicit ctx: Context): Frag =
    cssTagWithTheme(name, ctx.currentBg)

  def cssTagWithTheme(name: String, theme: String): Frag =
    cssAt(s"css/${cssNameFromManifest(name, theme)}")
  def cssTagNoTheme(name: String): Frag =
    cssAt(s"css/${cssNameFromManifest(name)}.css")
  private def cssAt(path: String): Frag =
    link(href := assetUrl(path), rel := "stylesheet")
  private def cssNameFromManifest(name: String, theme: String = ""): String =
    if (!theme.isEmpty())
      s"${manifest.css(s"$name.$theme").getOrElse(s"$name.$theme")}"
    else s"${manifest.css(s"$name").getOrElse(s"$name")}"

  private def jsNameFromManifest(key: String): String = manifest.js(key).fold(key)(_.name)
  private def jsAtESM(key: String, path: String = "compiled/"): Frag = {
    script(
      deferAttr,
      src := assetUrl(s"${path}${jsNameFromManifest(key)}"),
      tpe := "module"
    )
  }
  private def staticJsAtESM(key: String, path: String = "compiled/"): Frag = {
    script(
      deferAttr,
      src := staticAssetUrl(s"${path}${jsNameFromManifest(key)}"),
      tpe := "module"
    )
  }

  // load script as common js
  def jsAtCJS(path: String): Frag = script(deferAttr, src := staticAssetUrl(path))

  def jsTag(name: String): Frag = jsAtESM(name, "javascripts/")

  // jsModule is used from app/views/ as base ui module entry point
  def jsModule(name: String, path: String = "compiled/"): Frag = jsAtESM(name, path)

  def depsTag = staticJsAtESM("deps.min.js")

  def roundTag(lib: GameLogic) = lib match {
    case GameLogic.Draughts() => jsModule("draughtsround")
    case _                    => jsModule("round")
  }
  def roundPlayStrategyTag(lib: GameLogic) = lib match {
    case GameLogic.Draughts() => "PlayStrategyDraughtsRound"
    case _                    => "PlayStrategyRound"
  }
  def roundNvuiTag(implicit ctx: Context) = ctx.blind option jsModule("round.nvui")

  def analyseTag                            = jsModule("analysisBoard")
  def analyseNvuiTag(implicit ctx: Context) = ctx.blind option jsModule("analysisBoard.nvui")

  def captchaTag        = jsModule("captcha")
  def infiniteScrollTag = jsModule("infiniteScroll")

  def chessgroundTag = staticJsAtESM("chessground.min.js", "npm/")

  def draughtsgroundTag   = jsAtCJS("javascripts/vendor/draughtsground.min.js")
  def cashTag             = staticJsAtESM("cash.min.js", "javascripts/vendor/")
  def fingerprintTag      = staticJsAtESM("fipr.js", "javascripts/")
  def tagifyTag           = staticJsAtESM("tagify.min.js", "vendor/tagify/")
  def highchartsLatestTag = staticJsAtESM("highcharts.js", "vendor/highcharts-4.2.5/")
  def highchartsMoreTag   = staticJsAtESM("highcharts-more.js", "vendor/highcharts-4.2.5/")

  def prismicJs(implicit ctx: Context): Frag =
    raw {
      isGranted(_.Prismic) ?? { // @TODO: check why lichess prismic is used here
        embedJsUnsafe("""window.prismic={endpoint:'https://lichess.prismic.io/api/v2'}""").render ++
          """<script src="//static.cdn.prismic.io/prismic.min.js"></script>"""
      }
    }

  def basicCsp(implicit req: RequestHeader): ContentSecurityPolicy = {
    val assets = if (req.secure) s"https://$assetDomain" else assetDomain.value
    val sockets = socketDomains map { socketDomain =>
      val protocol = if (req.secure) "wss://" else "ws://"
      s"$protocol$socketDomain"
    }
    ContentSecurityPolicy(
      defaultSrc = List("'self'", assets),
      connectSrc = "'self'" :: assets :: sockets ::: env.explorerEndpoint :: env.tablebaseEndpoint :: Nil,
      styleSrc = List("'self'", "'unsafe-inline'", assets),
      frameSrc = List("'self'", assets, "https://www.youtube.com", "https://player.twitch.tv"),
      workerSrc = List("'self'", assets),
      imgSrc = List("data:", "*"),
      scriptSrc = List("'self'", assets),
      baseUri = List("'none'")
    )
  }

  def defaultCsp(implicit ctx: Context): ContentSecurityPolicy = {
    val csp = basicCsp(ctx.req)
    ctx.nonce.fold(csp)(csp.withNonce(_))
  }

  def embedJsUnsafe(js: String)(implicit ctx: Context): Frag =
    raw {
      val nonce = ctx.nonce ?? { nonce =>
        s""" nonce="$nonce""""
      }
      s"""<script$nonce>$js</script>"""
    }

  def embedJsUnsafe(js: String, nonce: Nonce): Frag =
    raw {
      s"""<script nonce="$nonce">$js</script>"""
    }

  def embedJsUnsafeLoadThen(js: String)(implicit ctx: Context): Frag =
    embedJsUnsafe(s"""playstrategy.load.then(()=>{$js})""")

  def embedJsUnsafeLoadThen(js: String, nonce: Nonce): Frag =
    embedJsUnsafe(s"""playstrategy.load.then(()=>{$js})""", nonce)
}
