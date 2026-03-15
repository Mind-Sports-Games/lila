package lila.security

import com.github.blemale.scaffeine.LoadingCache
import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.model.CityResponse
import lila.common.autoconfig.{ AutoConfig, ConfigName }
import java.io.File
import java.net.InetAddress
import scala.concurrent.duration._
import scala.util.Try

import lila.common.IpAddress
import play.api.ConfigLoader

final class GeoIP(config: GeoIP.Config) {

  private lazy val reader: Option[DatabaseReader] =
    config.file.nonEmpty so {
      try {
        val dbFile = new File(config.file)
        if (dbFile.exists()) {
          val r = new DatabaseReader.Builder(dbFile).build()
          logger.info("MaxMindIpGeo is enabled")
          r.some
        } else {
          logger.info(s"MaxMindIpGeo is disabled: file not found ${config.file}")
          none
        }
      } catch {
        case e: Exception =>
          logger.info(s"MaxMindIpGeo is disabled: $e")
          none
      }
    }

  private val cache: LoadingCache[IpAddress, Option[Location]] =
    lila.memo.CacheApi.scaffeineNoScheduler
      .expireAfterAccess(config.cacheTtl)
      .build(compute)

  private def compute(ip: IpAddress): Option[Location] =
    reader.flatMap { db =>
      Try {
        val inet = InetAddress.getByName(ip.value)
        val city = db.city(inet)
        Location(city)
      }.toOption
    }

  def apply(ip: IpAddress): Option[Location] = cache get ip

  def orUnknown(ip: IpAddress): Location = apply(ip) | Location.unknown
}

object GeoIP {
  case class Config(
      file: String,
      @ConfigName("cache_ttl") cacheTtl: FiniteDuration
  )
  implicit val configLoader: ConfigLoader[Config] = AutoConfig.loader[Config]
}

case class Location(
    country: String,
    region: Option[String],
    city: Option[String]
) {

  def shortCountry: String = ~country.split(',').headOption

  override def toString = List(shortCountry.some, region, city).flatten mkString " > "
}

object Location {

  val unknown = Location("Solar System", none, none)

  val tor = Location("Tor exit node", none, none)

  def apply(city: CityResponse): Location = {
    val countryName = Option(city.getCountry).flatMap(c => Option(c.getName))
    val regionName  = Option(city.getMostSpecificSubdivision).flatMap(s => Option(s.getName))
    val cityName    = Option(city.getCity).flatMap(c => Option(c.getName))
    Location(countryName | unknown.country, regionName, cityName)
  }
}
