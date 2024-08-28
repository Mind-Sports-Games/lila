package lila.web

import lila.common.config.NetConfig
import play.api.Environment
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.JsonBodyReadables._
import play.api.libs.json.{ JsObject, JsString, JsValue, Json }
import play.api.Mode

import java.nio.file.{ Files, Path, Paths }
import java.time.Instant

case class SplitAsset(name: String, imports: List[String])
case class AssetMaps(js: Map[String, SplitAsset], css: Map[String, String])

final class AssetManifest(val environment: Environment, net: NetConfig)(implicit
    ec: scala.concurrent.ExecutionContext,
    ws: StandaloneWSClient
) {

  private var lastModified: Instant = Instant.MIN
  private var maps: AssetMaps       = AssetMaps(Map.empty, Map.empty)
  private val filename              = s"manifest.${if (net.minifiedAssets) "prod" else "dev"}.json"
  private val logger                = lila.log("assetManifest")

  def js(key: String): Option[SplitAsset]    = maps.js.get(key)
  def css(key: String): Option[String]       = maps.css.get(key)
  def deps(keys: List[String]): List[String] = keys.flatMap { key => js(key).??(_.imports) }.distinct
  def lastUpdate: Instant                    = lastModified

  def update(): Unit =
    if (environment.mode == Mode.Prod || net.externalManifest)
      fetchManifestJson(filename).foreach {
        _.foreach { manifestJson =>
          maps = readMaps(manifestJson)
          lastModified = Instant.now()
        }
      }
    else {
      val pathname = environment.getFile(s"public/compiled/$filename").toPath
      try {
        val current = Files.getLastModifiedTime(pathname).toInstant
        if (current.isAfter(lastModified)) {
          maps = readMaps(Json.parse(Files.newInputStream(pathname)))
          lastModified = current
        }
      } catch {
        case e: Throwable => logger.error(s"Error reading $pathname", e)
      }
    }

  private val keyRe = """^(?!common\.)(\S+)\.([A-Z0-9]{8})\.(?:js|css)""".r
  private def keyOf(fullName: String): String =
    fullName match {
      case keyRe(k, _) => k
      case _           => fullName
    }

  private def closure(
      name: String,
      jsMap: Map[String, SplitAsset],
      visited: Set[String] = Set.empty
  ): List[String] = {
    val k = keyOf(name)
    jsMap.get(k) match {
      case Some(asset) if !visited.contains(k) =>
        asset.imports.flatMap { importName =>
          importName :: closure(importName, jsMap, visited + name)
        }
      case _ => Nil
    }
  }
  // throws an Exception if JsValue is not as expected
  private def readMaps(manifest: JsValue): AssetMaps = {
    val js = (manifest \ "js")
      .as[JsObject]
      .value
      .map {
        case (k, value) => {
          val name    = (value \ "hash").asOpt[String].fold(s"$k.js")(h => s"$k.$h.js")
          val imports = (value \ "imports").asOpt[List[String]].getOrElse(Nil)
          (k, SplitAsset(name, imports))
        }
      }
      .toMap

    val css = (manifest \ "css")
      .as[JsObject]
      .value
      .map {
        case (k, asset) => {
          val hash = (asset \ "hash").as[String]
          (k, s"$k.$hash.css")
        }
      }
      .toMap

    AssetMaps(
      js.map { case (k, asset) =>
        k -> {
          if (asset.imports.nonEmpty) {
            asset.copy(imports = closure(asset.name, js).distinct)
          } else
            asset
        }
      },
      css
    )
  }

  private def fetchManifestJson(filename: String) = {
    val resource = s"${net.assetBaseUrl}/assets/compiled/$filename"
    ws.url(resource)
      .get()
      .map {
        case res if res.status == 200 =>
          res.body[JsValue].some
        case res => {
          logger.error(s"${res.status} fetching $resource")
          none
        }
      }
      .recoverWith { case e: Exception =>
        logger.error(s"fetching $resource", e)
        fuccess(none)
      }
  }

  update()
}

object AssetManifest {
  def apply(environment: Environment, net: NetConfig)(implicit
      ec: scala.concurrent.ExecutionContext,
      ws: StandaloneWSClient
  ): AssetManifest = new AssetManifest(environment, net)
}
