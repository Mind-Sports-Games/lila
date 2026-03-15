package lila.app
package templating

import lila.app.ui.ScalatagsTemplate._
import lila.common.extensions.*

object Environment
    extends lila.PackageObject
    with StringHelper
    with AssetHelper
    with DateHelper
    with NumberHelper
    with PaginatorHelper
    with FormHelper
    with SetupHelper
    with AiHelper
    with GameHelper
    with UserHelper
    with ForumHelper
    with I18nHelper
    with SecurityHelper
    with TeamHelper
    with TournamentHelper
    with FlashHelper
    with ChessgroundHelper {

  // Provide Zero instances needed for `.so` and `.option` extension methods in Scala 3 templates
  // Using implicit val (not given) so they're importable via `import Environment._`
  import alleycats.Zero
  implicit val zeroString: Zero[String]                              = Zero("")
  implicit def zeroOption[A]: Zero[Option[A]]                       = Zero(None)
  implicit def zeroList[A]: Zero[List[A]]                           = Zero(Nil)
  implicit def zeroVector[A]: Zero[Vector[A]]                       = Zero(Vector.empty)
  implicit def zeroSet[A]: Zero[Set[A]]                             = Zero(Set.empty)
  implicit def zeroMap[K, V]: Zero[Map[K, V]]                      = Zero(Map.empty)
  implicit val zeroInt: Zero[Int]                                    = Zero(0)
  implicit val zeroBool: Zero[Boolean]                               = Zero(false)
  implicit val zeroFuUnit: Zero[scala.concurrent.Future[Unit]]      = Zero(scala.concurrent.Future.unit)

  // #TODO holy shit fix me
  // requires injecting all the templates!!
  private var envVar: Option[Env] = None
  def setEnv(e: Env) = { envVar = Some(e) }
  def env: Env = envVar.get

  type FormWithCaptcha = (play.api.data.Form[?], lila.common.Captcha)

  def netConfig           = env.net
  def netBaseUrl          = env.net.baseUrl.value
  def contactEmailInClear = env.net.email.value
  def manifest            = env.web.manifest

  def apiVersion = lila.api.Mobile.Api.currentVersion

  def explorerEndpoint  = env.explorerEndpoint
  def tablebaseEndpoint = env.tablebaseEndpoint

  def isChatPanicEnabled = env.chat.panic.enabled

  def blockingReportScores: (Int, Int, Int) = (
    env.report.api.maxScores.dmap(_.highest).awaitOrElse(50.millis, "nbReports", 0),
    env.report.scoreThresholdsSetting.get().mid,
    env.report.scoreThresholdsSetting.get().high
  )

  val spinner: Frag = raw(
    """<div class="spinner"><svg viewBox="0 0 40 40"><circle cx=20 cy=20 r=18 fill="none"></circle></svg></div>"""
  )
}
