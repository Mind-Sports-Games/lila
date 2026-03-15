package lila.web

import play.api.{ Application, Configuration, Environment, Mode, Play }
import play.core.server.{
  NettyServer,
  RealServerProcess,
  Server,
  ServerConfig,
  ServerProcess,
  ServerStartException
}
import java.io.File

object PlayServer {

  def start(args: Array[String])(makeApplication: Environment => Application): Server = {
    val process = RealServerProcess(args.toIndexedSeq)
    try {
      val config: ServerConfig = readServerConfigSettings(process)

      lila.log("boot").info {
        val java = System.getProperty("java.version")
        val mem  = Runtime.getRuntime.maxMemory() / 1024 / 1024
        s"lila ${config.mode} / java $java, memory: ${mem}MB"
      }

      val environment: Environment = Environment(config.rootDir, process.classLoader, config.mode)
      val application              = makeApplication(environment)

      Play.start(application)

      val server = NettyServer(
        config,
        application,
        stopHook = () => scala.concurrent.Future.unit,
        application.actorSystem
      )(using application.materializer)

      process.addShutdownHook {
        if (application.coordinatedShutdown.shutdownReason().isEmpty) server.stop()
      }

      server
    } catch {
      case ServerStartException(message, cause) => process.exit(message, cause)
      case e: Throwable                         => process.exit("Oops, cannot start the server.", Some(e))
    }
  }

  private def readServerConfigSettings(process: ServerProcess): ServerConfig = {
    val configuration: Configuration = {
      val rootDirArg    = process.args.headOption.map(new File(_))
      val rootDirConfig = rootDirArg.fold(Map.empty[String, AnyRef])(ServerConfig.rootDirConfig(_))
      Configuration.load(process.classLoader, process.properties, rootDirConfig, true)
    }

    val rootDir: File = {
      val path = configuration
        .getOptional[String]("play.server.dir")
        .getOrElse(throw ServerStartException("No root server path supplied"))
      val file = new File(path)
      if (!file.isDirectory) throw ServerStartException(s"Bad root server path: $path")
      file
    }

    val httpPort = configuration.getOptional[String]("play.server.http.port").flatMap(_.toIntOption).getOrElse(9663)
    val address  = configuration.getOptional[String]("play.server.http.address").getOrElse("0.0.0.0")
    val mode =
      if (configuration.getOptional[String]("play.mode").contains("prod")) Mode.Prod
      else Mode.Dev

    ServerConfig(rootDir, httpPort, address, mode, process.properties, configuration)
  }
}
