package lila.fishnet

import reactivemongo.api.bson.*
import scala.concurrent.duration.*

import lila.common.IpAddress
import lila.db.dsl.*

final private class FishnetLimiter(
    analysisColl: Coll,
    requesterApi: lila.analyse.RequesterApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  import FishnetLimiter.*

  def apply(sender: Work.Sender, ignoreConcurrentCheck: Boolean, ownGame: Boolean): Fu[Option[Decline]] = {
    val checked =
      if (ignoreConcurrentCheck) perDayCheck(sender)
      else
        concurrentCheck(sender) flatMap {
          case declined @ Some(_) => fuccess(declined)
          case None               => perDayCheck(sender)
        }
    checked flatMap {
      case Some(decline) =>
        logger.info(s"Declined analysis request by ${sender.userId}: ${decline.reason}")
        fuccess(decline.some)
      case None => requesterApi.add(sender.userId, ownGame) inject none
    }
  }

  private val RequestLimitPerIP = new lila.memo.RateLimit[IpAddress](
    credits = 60,
    duration = 20 hours,
    key = "request_analysis.ip"
  )

  private def concurrentCheck(sender: Work.Sender): Fu[Option[Decline]] =
    sender match {
      case Work.Sender(_, _, mod, system) if mod || system => fuccess(none)
      case Work.Sender(userId, ip, _, _)                   =>
        analysisColl
          .exists(
            $or(
              $doc("sender.ip"     -> ip),
              $doc("sender.userId" -> userId)
            )
          )
          .map(_.option(Decline.Concurrent))
    }

  private val maxPerDay  = 35
  private val maxPerWeek = 160

  private def perDayCheck(sender: Work.Sender): Fu[Option[Decline]] =
    sender match {
      case Work.Sender(_, _, mod, system) if mod || system => fuccess(none)
      case Work.Sender(userId, ip, _, _)                   =>
        def perUser: Fu[Option[Decline]] =
          requesterApi.countTodayAndThisWeek(userId) map { case (daily, weekly) =>
            val dailyMax = if (weekly < maxPerWeek * 2 / 3) maxPerDay else maxPerDay * 2 / 3
            if (weekly >= maxPerWeek) Decline.Weekly(weekly, maxPerWeek).some
            else if (daily >= dailyMax) Decline.Daily(daily, dailyMax).some
            else none
          }
        ip.fold(perUser) { ipAddress =>
          RequestLimitPerIP(ipAddress, cost = 1)(perUser)(fuccess(Decline.IpLimit.some))
        }
    }
}

private object FishnetLimiter {

  sealed abstract class Decline(val reason: String)

  object Decline {
    case object Concurrent extends Decline("One of your analysis requests is still in the queue")
    case object IpLimit    extends Decline("Too many analysis requests from your IP address")
    case class Daily(daily: Int, max: Int)
        extends Decline(s"Daily analysis request limit reached ($daily/$max)")
    case class Weekly(weekly: Int, max: Int)
        extends Decline(s"Weekly analysis request limit reached ($weekly/$max)")
  }
}
