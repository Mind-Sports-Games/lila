package lila.web

import play.api.libs.ws.StandaloneWSClient
import play.api.Environment
import com.softwaremill.macwire.*
import lila.common.config.NetConfig

@Module
final class Env(environment: Environment, net: NetConfig)(implicit
    ec: scala.concurrent.ExecutionContext,
    ws: StandaloneWSClient
) {

  val manifest = wire[AssetManifest]
}
