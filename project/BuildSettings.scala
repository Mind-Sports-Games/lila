import play.sbt.PlayImport._
import sbt._, Keys._
import bloop.integrations.sbt.BloopKeys.bloopGenerate

object BuildSettings {

  import Dependencies._

  val lilaVersion        = "3.2"
  val globalScalaVersion = "3.7.4"

  val useEpoll = sys.props.get("epoll").fold(false)(_.toBoolean)
  if (useEpoll) println("--- epoll build ---")

  def buildSettings =
    Defaults.coreDefaultSettings ++ Seq(
      version      := lilaVersion,
      organization := "org.lichess",
      resolvers ++= Seq(jitpack, lilaMaven, lichessMaven, sonashots) ++ Resolver.sonatypeOssRepos("snapshots") ++ localMaven,
      scalaVersion := globalScalaVersion,
      scalacOptions ++= compilerOptions,
      // No bloop project for tests
      // Test / bloopGenerate := None,
      // disable publishing doc and sources
      Compile / doc / sources                := Seq.empty,
      Compile / packageDoc / publishArtifact := false,
      Compile / packageSrc / publishArtifact := false,
      Compile / run / fork                   := true,
      javaOptions ++= Seq("-Xms64m", "-Xmx256m")
    )

  lazy val defaultLibs: Seq[ModuleID] =
    pekko.bundle ++ macwire.bundle ++ scalalib.bundle ++ Seq(
      cats,
      alleycats,
      play.api,
      strategyGames,
      kittens,
      jodaTime,
    )

  def smallModule(
      name: String,
      deps: Seq[sbt.ClasspathDep[sbt.ProjectReference]],
      libs: Seq[ModuleID]
  ) =
    Project(
      name,
      file("modules/" + name)
    ).dependsOn(deps: _*)
      .settings(
        libraryDependencies ++= libs,
        buildSettings,
        srcMain
      )

  def module(
      name: String,
      deps: Seq[sbt.ClasspathDep[sbt.ProjectReference]],
      libs: Seq[ModuleID]
  ) =
    smallModule(name, deps, defaultLibs ++ libs)

  val compilerOptions = Seq(
    "-indent",
    "-feature",
    "-language:postfixOps",
    "-language:implicitConversions",
    "-release:21",
    "-Wimplausible-patterns",
    "-Wunused:all"
  )

  val srcMain = Seq(
    Compile / scalaSource := (Compile / sourceDirectory).value,
    Test / scalaSource    := (Test / sourceDirectory).value
  )

  def projectToRef(p: Project): ProjectReference = LocalProject(p.id)
}
