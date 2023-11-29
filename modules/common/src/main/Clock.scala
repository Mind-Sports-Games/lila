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
  ): Option[(Boolean, Boolean, Boolean, Int, Int, Option[Int], Option[Int])] =
    c match {
      case fc: StratClock.Config => {
        StratClock.Config.unapply(fc).map(t => (false, false, false, t._1, t._2, None, None))
      }
      case bc: StratClock.BronsteinConfig => {
        StratClock.BronsteinConfig.unapply(bc).map(t => (false, true, false, t._1, t._2, None, None))
      }
      case udc: StratClock.SimpleDelayConfig => {
        StratClock.SimpleDelayConfig.unapply(udc).map(t => (false, false, true, t._1, t._2, None, None))
      }
      case bc: ByoyomiClock.Config => {
        ByoyomiClock.Config
          .unapply(bc)
          .map(t => (true, false, false, t._1, t._2, Some(t._3), Some(t._4)))
      }
    }

  // Yes, I know this is kinda gross. :'(
  def clockConfigFromValues(
      useByoyomi: Boolean,
      useBronsteinDelay: Boolean,
      useSimpleDelay: Boolean,
      limit: Int,
      increment: Int,
      byoyomi: Option[Int],
      periods: Option[Int]
  ): ClockConfig =
    (useByoyomi, useBronsteinDelay, useSimpleDelay, byoyomi, periods) match {
      case (true, false, false, Some(byoyomi), Some(periods)) =>
        ByoyomiClock.Config(limit, increment, byoyomi, periods)
      case (false, true, false, _, _) =>
        StratClock.BronsteinConfig(limit, increment)
      case (false, false, true, _, _) =>
        StratClock.SimpleDelayConfig(limit, increment)
      case _ =>
        StratClock.Config(limit, increment)
    }

  def formatLimit(l: Int) = // Assumes seconds
    StratClock.Config(l, 0).limitString + {
      if (l <= 60) " minute" else " minutes"
    }

  def clockConfigMappingsMinutes(clockTimes: Seq[Double], byoyomiLimits: Seq[Int]): Mapping[ClockConfig] =
    clockConfigMappingsSeconds(clockTimes.map(_ * 60d).map(_.toInt), byoyomiLimits)

  def clockConfigMappingsSeconds(clockTimes: Seq[Int], byoyomiLimits: Seq[Int]): Mapping[ClockConfig] = {
    val clockTimeChoices = options(clockTimes, format = formatLimit)

    mapping[ClockConfig, Boolean, Boolean, Boolean, Int, Int, Option[Int], Option[Int]](
      "useByoyomi"        -> boolean,
      "useBronsteinDelay" -> boolean,
      "useSimpleDelay"    -> boolean,
      "limit"             -> numberIn(clockTimeChoices.pp("clockTimeChoices")),
      "increment"         -> number(min = 0, max = 120),
      "byoyomi"           -> optional(number.verifying(byoyomiLimits.contains _)),
      "periods"           -> optional(number(min = 0, max = 5))
    )(clockConfigFromValues)(valuesFromClockConfig)
  }

  def clockConfigMappingsFromMinutes(clockTimes: Seq[Int], byoyomiLimits: Seq[Int]): Mapping[ClockConfig] =
    clockConfigMappingsMinutes(clockTimes.map(_.toDouble), byoyomiLimits)

  def optionsMinutes(it: Iterable[Int], format: Int => String): Options[Int] =
    it map (d => d -> format(d))

  def clockTimeChoicesFromMinutes(clockTimes: Seq[Double]) =
    optionsMinutes(clockTimes.map(_ * 60).map(_.toInt), formatLimit)

  def clockTimeChoicesFromSeconds(clockTimes: Seq[Int]) =
    optionsMinutes(clockTimes, format = formatLimit)

}
