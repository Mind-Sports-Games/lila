package lila.app

import akka.actor.{ ActorSystem, CoordinatedShutdown }
import com.softwaremill.macwire.*
import play.api.inject.DefaultApplicationLifecycle
import play.api.http.FileMimeTypes
import play.api.inject.ApplicationLifecycle
import play.api.libs.crypto.DefaultCookieSigner
import play.api.libs.ws.StandaloneWSClient
import play.api.mvc.*
import play.api.mvc.request.*
import play.api.routing.Router
import play.api.{ BuiltInComponents, Configuration, Environment }

object Lila {

  def main(args: Array[String]): Unit =
    lila.web.PlayServer.start(args) { env =>
      LilaComponents(
        env,
        DefaultApplicationLifecycle(),
        Configuration.load(env)
      ).application
    }
}

final class LilaComponents(
    val environment: Environment,
    val applicationLifecycle: ApplicationLifecycle,
    val configuration: Configuration
) extends BuiltInComponents {

  val controllerComponents: ControllerComponents = DefaultControllerComponents(
    defaultActionBuilder,
    playBodyParsers,
    fileMimeTypes,
    executionContext
  )

  implicit val ec: scala.concurrent.ExecutionContextExecutor =
    scala.concurrent.ExecutionContext.getClass
      .getDeclaredMethod("opportunistic")
      .invoke(scala.concurrent.ExecutionContext)
      .asInstanceOf[scala.concurrent.ExecutionContextExecutor]

  lila.log("boot").info {
    val java             = System.getProperty("java.version")
    val mem              = Runtime.getRuntime.maxMemory() / 1024 / 1024
    val appVersionCommit = ~configuration.getOptional[String]("app.version.commit")
    val appVersionDate   = ~configuration.getOptional[String]("app.version.date")
    s"lila ${environment.mode} $appVersionCommit $appVersionDate / java $java, memory: ${mem}MB"
  }

  import _root_.controllers.*

  // we want to use the legacy session cookie baker
  // for compatibility with lila-ws
  lazy val cookieBaker = LegacySessionCookieBaker(httpConfiguration.session, cookieSigner)

  override lazy val requestFactory: RequestFactory = {
    val cookieSigner = DefaultCookieSigner(httpConfiguration.secret)
    DefaultRequestFactory(
      DefaultCookieHeaderEncoding(httpConfiguration.cookies),
      cookieBaker,
      LegacyFlashCookieBaker(httpConfiguration.flash, httpConfiguration.secret, cookieSigner)
    )
  }

  implicit def system: ActorSystem = actorSystem

  implicit lazy val httpClient: StandaloneWSClient = {
    import play.shaded.ahc.org.asynchttpclient.DefaultAsyncHttpClient
    import play.api.libs.ws.WSConfigParser
    import play.api.libs.ws.ahc.{ AhcConfigBuilder, AhcWSClientConfigParser, StandaloneAhcWSClient }
    new StandaloneAhcWSClient(
      new DefaultAsyncHttpClient(
        new AhcConfigBuilder(
          new AhcWSClientConfigParser(
            new WSConfigParser(configuration.underlying, environment.classLoader).parse(),
            configuration.underlying,
            environment.classLoader
          ).parse()
        ).modifyUnderlying(_.setIoThreadsCount(8)).build()
      )
    )
  }

  def httpFilters: Seq[play.api.mvc.EssentialFilter] = Seq(wire[lila.app.http.HttpFilter])

  // dev assets
  implicit def mimeTypes: FileMimeTypes = fileMimeTypes
  lazy val devAssetsController          = wire[ExternalAssets]

  lazy val shutdown = CoordinatedShutdown(system)

  lazy val boot: lila.app.EnvBoot = wire[lila.app.EnvBoot]
  lazy val env: lila.app.Env      = boot.env

  lazy val account: Account               = wire[Account]
  lazy val analyse: Analyse               = wire[Analyse]
  lazy val api: Api                       = wire[Api]
  lazy val appeal: Appeal                 = wire[Appeal]
  lazy val auth: Auth                     = wire[Auth]
  lazy val blog: Blog                     = wire[Blog]
  lazy val playApi: PlayApi               = wire[PlayApi]
  lazy val challenge: Challenge           = wire[Challenge]
  lazy val coach: Coach                   = wire[Coach]
  lazy val clas: Clas                     = wire[Clas]
  lazy val coordinate: Coordinate         = wire[Coordinate]
  lazy val dasher: Dasher                 = wire[Dasher]
  lazy val dev: Dev                       = wire[Dev]
  lazy val editor: Editor                 = wire[Editor]
  lazy val event: Event                   = wire[Event]
  lazy val `export`: Export               = wire[Export]
  lazy val fishnet: Fishnet               = wire[Fishnet]
  lazy val forumCateg: ForumCateg         = wire[ForumCateg]
  lazy val forumPost: ForumPost           = wire[ForumPost]
  lazy val forumTopic: ForumTopic         = wire[ForumTopic]
  lazy val game: Game                     = wire[Game]
  lazy val i18n: I18n                     = wire[I18n]
  lazy val importer: Importer             = wire[Importer]
  lazy val insight: Insight               = wire[Insight]
  lazy val irwin: Irwin                   = wire[Irwin]
  lazy val learn: Learn                   = wire[Learn]
  lazy val library: Library               = wire[Library]
  lazy val lobby: Lobby                   = wire[Lobby]
  lazy val main: Main                     = wire[Main]
  lazy val memory: Memory                 = wire[Memory]
  lazy val msg: Msg                       = wire[Msg]
  lazy val mod: Mod                       = wire[Mod]
  lazy val gameMod: GameMod               = wire[GameMod]
  lazy val notifyC: Notify                = wire[Notify]
  lazy val oAuthApp: OAuthApp             = wire[OAuthApp]
  lazy val oAuthToken: OAuthToken         = wire[OAuthToken]
  lazy val options: Options               = wire[Options]
  lazy val page: Page                     = wire[Page]
  lazy val plan: Plan                     = wire[Plan]
  lazy val practice: Practice             = wire[Practice]
  lazy val pref: Pref                     = wire[Pref]
  lazy val prismic: Prismic               = wire[Prismic]
  lazy val push: Push                     = wire[Push]
  lazy val puzzle: Puzzle                 = wire[Puzzle]
  lazy val relation: Relation             = wire[Relation]
  lazy val relay: RelayRound              = wire[RelayRound]
  lazy val relayTour: RelayTour           = wire[RelayTour]
  lazy val report: Report                 = wire[Report]
  lazy val round: Round                   = wire[Round]
  lazy val search: Search                 = wire[Search]
  lazy val setup: Setup                   = wire[Setup]
  lazy val simul: Simul                   = wire[Simul]
  lazy val stat: Stat                     = wire[Stat]
  lazy val streamer: Streamer             = wire[Streamer]
  lazy val study: Study                   = wire[Study]
  lazy val swiss: Swiss                   = wire[Swiss]
  lazy val team: Team                     = wire[Team]
  lazy val timeline: Timeline             = wire[Timeline]
  lazy val tournament: Tournament         = wire[Tournament]
  lazy val tournamentCrud: TournamentCrud = wire[TournamentCrud]
  lazy val tv: Tv                         = wire[Tv]
  lazy val user: User                     = wire[User]
  lazy val userAnalysis: UserAnalysis     = wire[UserAnalysis]
  lazy val userTournament: UserTournament = wire[UserTournament]
  lazy val video: Video                   = wire[Video]
  lazy val dgt: DgtCtrl                   = wire[DgtCtrl]
  lazy val storm: Storm                   = wire[Storm]
  lazy val racer: Racer                   = wire[Racer]
  lazy val bulkPairing: BulkPairing       = wire[BulkPairing]

  // eagerly wire up all controllers
  val router: Router = wire[_root_.router.router.Routes]

  if (configuration.get[Boolean]("kamon.enabled")) {
    lila.log("boot").info("Kamon is enabled")
    kamon.Kamon.loadModules()
  }
}
