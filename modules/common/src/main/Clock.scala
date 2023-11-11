package lila.common


import play.api.data._
import play.api.data.Forms._

import strategygames.{ ByoyomiClock, Clock => StratClock, ClockConfig }
import lila.common.Form._

// Some helpers for dealing with clocks
object Clock {

  // Yes, I know this is kinda gross. :'(
  // TODO: this is basically a case classes "unapply" and "apply" method
  def valuesFromClockConfig(
      c: ClockConfig
  ): Option[(Boolean, Boolean, Boolean, Double, Int, Option[Int], Option[Int])] =
    c match {
      case fc: StratClock.Config => {
        StratClock.Config.unapply(fc).map(t => (false, false, false, t._1 / 60d, t._2, None, None))
      }
      case bc: StratClock.BronsteinConfig => {
        StratClock.BronsteinConfig.unapply(bc).map(t => (false, true, false, t._1 / 60d, t._2, None, None))
      }
      case udc: StratClock.UsDelayConfig => {
        StratClock.UsDelayConfig.unapply(udc).map(t => (false, false, true, t._1 / 60d, t._2, None, None))
      }
      case bc: ByoyomiClock.Config => {
        ByoyomiClock.Config
          .unapply(bc)
          .map(t => (true, false, false, t._1 / 60d, t._2, Some(t._3), Some(t._4)))
      }
    }

  // Yes, I know this is kinda gross. :'(
  def clockConfigFromValues(
      useByoyomi: Boolean,
      useBronsteinDelay: Boolean,
      useSimpleDelay: Boolean,
      limit: Double,
      increment: Int,
      byoyomi: Option[Int],
      periods: Option[Int]
  ): ClockConfig = // TODO: deal with Bronstein as well
    (useByoyomi, useBronsteinDelay, useSimpleDelay, byoyomi, periods) match {
      case (true, false, false, Some(byoyomi), Some(periods)) =>
        ByoyomiClock.Config((limit * 60).toInt, increment, byoyomi, periods)
      case (false, true, false, _, _) =>
        StratClock.BronsteinConfig((limit * 60).toInt, increment)
      case (false, false, true, _, _) =>
        StratClock.UsDelayConfig((limit * 60).toInt, increment)
      case _ =>
        StratClock.Config((limit * 60).toInt, increment)
    }

    private def formatLimit(l: Double) =
      StratClock.Config(l * 60 toInt, 0).limitString + {
        if (l <= 1) " minute" else " minutes"
      }

    def clockConfigMappings(clockTimes: Seq[Double], byoyomiLimits: Seq[Int]): Mapping[ClockConfig] = {
        val clockTimeChoices = optionsDouble(clockTimes, formatLimit)
        mapping[ClockConfig, Boolean, Boolean, Boolean, Double, Int, Option[Int], Option[Int]](
          "useByoyomi"        -> boolean,
          "useBronsteinDelay" -> boolean,
          "useSimpleDelay"    -> boolean,
          "limit"             -> numberInDouble(clockTimeChoices),
          "increment"         -> number(min = 0, max = 120),
          "byoyomi"           -> optional(number.verifying(byoyomiLimits.contains _)),
          "periods"           -> optional(number(min = 0, max = 5))
        )(clockConfigFromValues)(valuesFromClockConfig)
    }

    def clockConfigMappingsFromMinutes(clockTimes: Seq[Int], byoyomiLimits: Seq[Int]): Mapping[ClockConfig] = 
      clockConfigMappings(clockTimes.map(_ * 60d), byoyomiLimits)
}
