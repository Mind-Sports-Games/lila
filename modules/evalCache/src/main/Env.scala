package lila.evalCache

import strategygames.variant.Variant
import strategygames.format.FEN
import strategygames.GameLogic
import com.softwaremill.macwire._
import play.api.Configuration

import lila.common.Bus
import lila.common.config.CollName
import lila.hub.actorApi.socket.remote.{ TellSriIn, TellSriOut }
import lila.socket.Socket.Sri

@Module
final class Env(
    appConfig: Configuration,
    userRepo: lila.user.UserRepo,
    db: lila.db.Db,
    cacheApi: lila.memo.CacheApi
)(implicit
    ec: scala.concurrent.ExecutionContext,
    scheduler: akka.actor.Scheduler,
    mode: play.api.Mode
) {

  private lazy val coll = db(appConfig.get[CollName]("evalCache.collection.evalCache"))

  private lazy val truster = wire[EvalCacheTruster]

  private lazy val upgrade = wire[EvalCacheUpgrade]

  lazy val api: EvalCacheApi = wire[EvalCacheApi]

  private lazy val socketHandler = wire[EvalCacheSocketHandler]

  // remote socket support
  Bus.subscribeFun("remoteSocketIn:evalGet") { case TellSriIn(sri, _, msg) =>
    msg obj "d" foreach { d =>
      // TODO send once, let lila-ws distribute
      socketHandler.evalGet(Sri(sri), d, res => Bus.publish(TellSriOut(sri, res), "remoteSocketOut"))
    }
  }
  Bus.subscribeFun("remoteSocketIn:evalPut") { case TellSriIn(sri, Some(userId), msg) =>
    msg obj "d" foreach { d =>
      socketHandler.untrustedEvalPut(Sri(sri), userId, d)
    }
  }
  // END remote socket support

  def cli =
    new lila.common.Cli {
      def process = { case "eval-cache" :: "drop" :: variantKey :: fenParts =>
        Variant(GameLogic.Chess(), variantKey).fold(fufail[String]("Invalid variant")) { variant =>
          api.drop(variant, FEN(variant.gameLogic, fenParts mkString " ")) inject "done!"
        }
      }
    }
}
