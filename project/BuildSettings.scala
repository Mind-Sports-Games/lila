import play.sbt.PlayImport._
import sbt._, Keys._
import bloop.integrations.sbt.BloopKeys.bloopGenerate

object BuildSettings {

  import Dependencies._

  val lilaVersion        = "3.2"
  val globalScalaVersion = "3.7.4"

  def buildSettings =
    Defaults.coreDefaultSettings ++ Seq(
      version      := lilaVersion,
      organization := "org.lichess",
      resolvers ++= Seq(jitpack, lilaMaven, lichessMaven, sonashots) ++ Resolver.sonatypeOssRepos(
        "snapshots"
      ) ++ localMaven,
      scalaVersion := globalScalaVersion,
      scalacOptions ++= compilerOptions,
      scalacOptions := scalacOptions.value.distinct,
      // No bloop project for tests
      // Test / bloopGenerate := None,
      // disable publishing doc and sources
      Compile / doc / sources                := Seq.empty,
      Compile / packageDoc / publishArtifact := false,
      Compile / packageSrc / publishArtifact := false,
      Compile / run / fork                   := true,
      Compile / run / javaOptions ++= Seq("-Xms64m", "-Xmx512m"),
      // strategygames initialises its variants in a cycle: chess.variant.Variant lists Standard,
      // and Standard's pieces call back into Variant.symmetricRank. A single thread walks that
      // cycle re-entrantly, which the JVM allows, but two suites entering it at once each hold the
      // class initialisation monitor the other is waiting on, and the run hangs for good — with no
      // output, since the deadlock is in the JVM rather than in any test. Walk it once here, before
      // any suite starts: the classes end up initialised, so no later access can lock, and suites
      // keep running in parallel. Variant.all covers every game logic, not just the chess cycle.
      Test / testOptions += Tests.Setup { (loader: ClassLoader) =>
        try {
          val variant = loader.loadClass("strategygames.variant.Variant$")
          val _       = variant.getMethod("all").invoke(variant.getField("MODULE$").get(null))
        } catch { case _: ClassNotFoundException => () }
      }
    )

  lazy val defaultLibs: Seq[ModuleID] =
    akka.bundle ++ macwire.bundle ++ scalalib.bundle ++ Seq(
      cats,
      alleycats,
      play.api,
      strategyGames,
      kittens,
      jodaTime
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
    "-no-indent",
    "-deprecation",
    "-feature",
    "-language:postfixOps",
    "-language:implicitConversions",
    "-release:21",
    "-Wimplausible-patterns",
    "-Wunused:all",
    // Warnings as errors!
    "-Xfatal-warnings"
  )

  val srcMain = Seq(
    Compile / scalaSource := (Compile / sourceDirectory).value,
    Test / scalaSource    := (Test / sourceDirectory).value
  )

  def projectToRef(p: Project): ProjectReference = LocalProject(p.id)
}
