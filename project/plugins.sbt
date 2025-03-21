resolvers += Resolver.url(
  "lila-maven-sbt",
  url("https://raw.githubusercontent.com/ornicar/lila-maven/master")
)(Resolver.ivyStylePatterns)
addSbtPlugin("com.typesafe.play" % "sbt-plugin"           % "2.8.16-lila_1.16")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"         % "2.4.2")
addSbtPlugin("ch.epfl.scala"     % "sbt-bloop"            % "1.4.8")
addSbtPlugin("net.vonbuchholtz"  % "sbt-dependency-check" % "5.1.0")
addSbtPlugin("com.timushev.sbt"  % "sbt-updates"          % "0.6.3")
