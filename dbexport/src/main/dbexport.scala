import cats.syntax.all._
import com.monovore.decline._
import lila.game.{ GameConfig, GameRepo }
import io.methvin.play.autoconfig._

object HelloWorld
    extends CommandApp(
      name = "hello-world",
      header = "Says hello!",
      main = {

        val appConfig: Configuration = ???
        val config                   = appConfig.get[GameConfig]("game")(AutoConfig.loader)
        val gameRepo                 = new GameRepo(db(config.gameColl))
        val userOpt =
          Opts.option[String]("target", help = "Person to greet.").withDefault("world")

        val quietOpt = Opts.flag("quiet", help = "Whether to be quiet.").orFalse

        (userOpt, quietOpt).mapN { (user, quiet) =>
          if (quiet) println("...")
          else println(s"Hello $user!")
        }
      }
    )
