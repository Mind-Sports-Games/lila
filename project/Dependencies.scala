import play.sbt.PlayImport._
import sbt._, Keys._

object Dependencies {

  val jitpack = "jitpack".at("https://jitpack.io")
  val lilaMaven = "lila-maven" at "https://raw.githubusercontent.com/Mind-Sports-Games/lila-maven/master"
  val lichessMaven = "lichess-maven" at "https://raw.githubusercontent.com/lichess-org/lila-maven/master"
  val sonashots = "sonashots".at("https://oss.sonatype.org/content/repositories/snapshots")
  val localMaven = sys.env
    .get("LILA_MAVEN_RESOLVERS")
    .map(_.split(",").zipWithIndex.map { case (x, i) => s"local-maven-$i" at x })
    .map(_.toSeq)
    .getOrElse(Seq())

  object scalalib {
    val version = "11.9.5"
    val org     = "com.github.lichess-org.scalalib"
    val core     = org %% "scalalib-core"      % version
    val model    = org %% "scalalib-model"     % version
    val playJson = org %% "scalalib-play-json" % version
    val lila     = org %% "scalalib-lila"      % version
    def bundle   = Seq(core, model, playJson, lila)
  }
  val hasher          = "com.roundeights"                 %% "hasher"                          % "1.3.1"
  val jodaTime        = "joda-time"                        % "joda-time"                       % "2.10.10"
  val compression     = "org.lichess"                     %% "compression"                     % "3.0"
  val strategyGames   = "org.playstrategy"                %% "strategygames"                   % "10.2.1-pstrat202-lw.20260117.1"
  val maxmind         = "com.maxmind.geoip2"               % "geoip2"                          % "4.2.0"
  val prismic         = "io.prismic"                      %% "scala-kit"                       % "1.2.19_lila-3.2"
  val scrimage        = "com.sksamuel.scrimage"            % "scrimage-core"                   % "4.3.0"
  val scaffeine       = "com.github.blemale"              %% "scaffeine"                       % "5.2.1" % "compile"
  val googleOAuth     = "com.google.auth"                  % "google-auth-library-oauth2-http" % "0.25.5"
  val scalaUri        = "io.lemonlabs"                    %% "scala-uri"                       % "4.0.3"
  val scalatags       = "com.lihaoyi"                     %% "scalatags"                       % "0.13.1"
  val lettuce         = "io.lettuce"                       % "lettuce-core"                    % "6.1.2.RELEASE"
  val epoll           = "io.netty"                         % "netty-transport-native-epoll"    % "4.1.58.Final" classifier "linux-x86_64"
  val scalatest       = "org.scalatest"                   %% "scalatest"                       % "3.2.18" % Test
  val uaparser        = "org.uaparser"                    %% "uap-scala"                       % "0.21.0"
  val apacheText      = "org.apache.commons"               % "commons-text"                    % "1.12.0"
  val cats            = "org.typelevel"                   %% "cats-core"                       % "2.13.0"
  val alleycats       = "org.typelevel"                   %% "alleycats-core"                  % "2.13.0"
  val catsMtl         = "org.typelevel"                   %% "cats-mtl"                        % "1.6.0"
  val kittens         = "org.typelevel"                   %% "kittens"                         % "3.5.0"
  val bloomFilter     = "com.github.alexandrnikitin"      %% "bloom-filter"                    % "0.13.1_lila-1"
  val jacksonDatabind = "com.fasterxml.jackson.core"       % "jackson-databind"                % "2.10.0"

  val munit      = "org.scalameta"  %% "munit"            % "1.2.1"  % Test
  val scalacheck = "org.scalacheck" %% "scalacheck"       % "1.19.0" % Test
  val munitCheck = "org.scalameta"  %% "munit-scalacheck" % "1.2.0"  % Test

  object tests {
    val bundle = Seq(munit)
  }

  object flexmark {
    val version = "0.50.50"
    val bundle =
      ("com.vladsch.flexmark" % "flexmark" % version) ::
        List("formatter", "ext-tables", "ext-autolink", "ext-gfm-strikethrough").map { ext =>
          "com.vladsch.flexmark" % s"flexmark-$ext" % version
        }
  }

  object macwire {
    val version = "2.6.7"
    val macros  = "com.softwaremill.macwire" %% "macros"  % version % "provided"
    val util    = "com.softwaremill.macwire" %% "util"    % version % "provided"
    val tagging = "com.softwaremill.common"  %% "tagging" % "2.3.5"
    def bundle  = Seq(macros, util, tagging)
  }

  object reactivemongo {
    val version = "1.1.0-RC19"

    val driver = "org.reactivemongo" %% "reactivemongo"               % version
    val stream = "org.reactivemongo" %% "reactivemongo-pekkostream"   % version
    val kamon  = "org.reactivemongo" %% "reactivemongo-kamon"         % version
    def bundle = Seq(driver, stream)
  }

  object play {
    val version = "3.0.6"
    val json    = "org.playframework" %% "play-json"         % version
    val api     = "org.playframework" %% "play"              % version
    val server  = "org.playframework" %% "play-server"       % version
    val netty   = "org.playframework" %% "play-netty-server" % version
    val logback = "org.playframework" %% "play-logback"      % version
    val mailer  = "org.playframework" %% "play-mailer"       % "10.1.0"
  }

  object playWs {
    val version = "3.0.6"
    val ahc     = "org.playframework" %% "play-ahc-ws-standalone"  % version
    val json    = "org.playframework" %% "play-ws-standalone-json" % version
    val bundle  = Seq(ahc, json)
  }

  object kamon {
    val version    = "2.8.1"
    val core       = "io.kamon" %% "kamon-core"           % version
    val influxdb   = "io.kamon" %% "kamon-influxdb"       % version
    val metrics    = "io.kamon" %% "kamon-system-metrics" % version
    val prometheus = "io.kamon" %% "kamon-prometheus"     % version
  }
  object pekko {
    val version    = "1.1.3"
    val actor      = "org.apache.pekko" %% "pekko-actor"       % version
    val actorTyped = "org.apache.pekko" %% "pekko-actor-typed" % version
    val stream     = "org.apache.pekko" %% "pekko-stream"      % version
    val slf4j      = "org.apache.pekko" %% "pekko-slf4j"       % version
    val testkit    = "org.apache.pekko" %% "pekko-testkit"     % version % Test
    def bundle     = List(actor, actorTyped, stream, slf4j)
  }
}
