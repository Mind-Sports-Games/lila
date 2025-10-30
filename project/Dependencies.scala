import play.sbt.PlayImport._
import sbt._, Keys._

object Dependencies {

  val lilaMaven = "lila-maven" at "https://raw.githubusercontent.com/Mind-Sports-Games/lila-maven/master"
  val localMaven = sys.env
    .get("LILA_MAVEN_RESOLVERS")
    .map(_.split(",").zipWithIndex.map { case (x, i) => s"local-maven-$i" at x })
    .map(_.toSeq)
    .getOrElse(Seq())

  val scalalib        = "com.github.ornicar"         %% "scalalib"                        % "7.0.2"
  val hasher          = "com.roundeights"            %% "hasher"                          % "1.2.1"
  val jodaTime        = "joda-time"                   % "joda-time"                       % "2.10.10"
  val compression     = "org.lichess"                %% "compression"                     % "1.6"
  val strategyGames   = "org.playstrategy"           %% "strategygames"                   % "10.2.1-pstrat188"
  val maxmind         = "com.sanoma.cda"             %% "maxmind-geoip2-scala"            % "1.3.1-THIB"
  val prismic         = "io.prismic"                 %% "scala-kit"                       % "1.2.19-THIB213"
  val scrimage        = "com.sksamuel.scrimage"       % "scrimage-core"                   % "4.3.0"
  val scaffeine       = "com.github.blemale"         %% "scaffeine"                       % "5.2.1" % "compile"
  val googleOAuth     = "com.google.auth"             % "google-auth-library-oauth2-http" % "0.25.5"
  val scalaUri        = "io.lemonlabs"               %% "scala-uri"                       % "3.2.0"
  val scalatags       = "com.lihaoyi"                %% "scalatags"                       % "0.9.4"
  val lettuce         = "io.lettuce"                  % "lettuce-core"                    % "6.1.2.RELEASE"
  val epoll           = "io.netty"                    % "netty-transport-native-epoll"    % "4.1.58.Final" classifier "linux-x86_64"
  val autoconfig      = "io.methvin.play"            %% "autoconfig-macros"               % "0.3.2" % "provided"
  val scalatest       = "org.scalatest"              %% "scalatest"                       % "3.1.0" % Test
  val uaparser        = "org.uaparser"               %% "uap-scala"                       % "0.13.0"
  val apacheText      = "org.apache.commons"          % "commons-text"                    % "1.12.0"
  val bloomFilter     = "com.github.alexandrnikitin" %% "bloom-filter"                    % "0.13.1"
  val jacksonDatabind = "com.fasterxml.jackson.core"  % "jackson-databind"                % "2.10.0"

  object specs2 {
    val version = "4.18.0"
    val core    = "org.specs2" %% "specs2-core" % version % Test
    val cats    = "org.specs2" %% "specs2-cats" % version % Test
    val bundle  = Seq(core, cats)
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
    val version = "2.3.7"
    val macros  = "com.softwaremill.macwire" %% "macros"  % version % "provided"
    val util    = "com.softwaremill.macwire" %% "util"    % version % "provided"
    val tagging = "com.softwaremill.common"  %% "tagging" % "2.3.5"
    def bundle  = Seq(macros, util, tagging)
  }

  object reactivemongo {
    val version = "1.1.0-RC15"

    val driver = "org.reactivemongo" %% "reactivemongo"                            % version
    val stream = "org.reactivemongo" %% "reactivemongo-akkastream"                 % version
    val epoll  = "org.reactivemongo"  % "reactivemongo-shaded-native-linux-x86-64" % version
    val kamon  = "org.reactivemongo" %% "reactivemongo-kamon"                      % "1.0.8"
    def bundle = Seq(driver, stream)
  }

  object play {
    val version  = "2.8.16-lila_1.17"
    val api      = "com.typesafe.play" %% "play"           % version
    val json     = "com.typesafe.play" %% "play-json"      % "2.9.3"
    val jsonJoda = "com.typesafe.play" %% "play-json-joda" % "2.9.2"
    val logback  = "com.typesafe.play" %% "play-logback"   % "1.2.13"
    val mailer   = "com.typesafe.play" %% "play-mailer"    % "8.0.1"
  }

  object playWs {
    val version = "2.2.0-M1"
    val ahc     = "com.typesafe.play" %% "play-ahc-ws-standalone"  % version
    val json    = "com.typesafe.play" %% "play-ws-standalone-json" % version
    val bundle  = Seq(ahc, json)
  }

  object kamon {
    val version    = "2.1.18"
    val core       = "io.kamon" %% "kamon-core"           % version
    val influxdb   = "io.kamon" %% "kamon-influxdb"       % version
    val metrics    = "io.kamon" %% "kamon-system-metrics" % version
    val prometheus = "io.kamon" %% "kamon-prometheus"     % version
  }
  object akka {
    val version    = "2.6.8"
    val akka       = "com.typesafe.akka" %% "akka-actor"       % version
    val akkaTyped  = "com.typesafe.akka" %% "akka-actor-typed" % version
    val akkaStream = "com.typesafe.akka" %% "akka-stream"      % version
    val akkaSlf4j  = "com.typesafe.akka" %% "akka-slf4j"       % version
    val testkit    = "com.typesafe.akka" %% "akka-testkit"     % version % Test
    def bundle     = List(akka, akkaTyped, akkaStream, akkaSlf4j)
  }
}
