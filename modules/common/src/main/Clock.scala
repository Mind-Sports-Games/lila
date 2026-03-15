package lila.common

import play.api.data._
import play.api.data.Forms._

import strategygames.{ ByoyomiClock, Clock => StratClock, ClockConfig }
import lila.common.Form._

// Some helpers for dealing with clocks
object Clock:

  // Extract values from ClockConfig for form binding
  def valuesFromClockConfig(
      c: ClockConfig
  ): Option[(Boolean, Boolean, Boolean, Int, Int, Int, Option[Int], Option[Int])] =
    c match
      case fc: StratClock.Config =>
        Some((false, false, false, fc.limitSeconds, fc.incrementSeconds, 0, None, None))
      case bc: StratClock.BronsteinConfig =>
        Some((false, true, false, bc.limitSeconds, 0, bc.delaySeconds, None, None))
      case udc: StratClock.SimpleDelayConfig =>
        Some((false, false, true, udc.limitSeconds, 0, udc.delaySeconds, None, None))
      case bc: ByoyomiClock.Config =>
        Some((true, false, false, bc.limitSeconds, bc.incrementSeconds, 0, Some(bc.byoyomiSeconds), Some(bc.periods)))

  def clockConfigFromValues(
      useByoyomi: Boolean,
      useBronsteinDelay: Boolean,
      useSimpleDelay: Boolean,
      limit: Int,
      increment: Int,
      delay: Int,
      byoyomi: Option[Int],
      periods: Option[Int]
  ): ClockConfig =
    (useByoyomi, useBronsteinDelay, useSimpleDelay, byoyomi, periods) match
      case (true, false, false, Some(byoyomi), Some(periods)) =>
        ByoyomiClock.Config(limit, increment, byoyomi, periods)
      case (false, true, false, _, _) =>
        StratClock.BronsteinConfig(limit, delay)
      case (false, false, true, _, _) =>
        StratClock.SimpleDelayConfig(limit, delay)
      case _ =>
        StratClock.Config(limit, increment)

  def formatLimit(l: Int) = // Assumes seconds
    StratClock.Config(l, 0).limitString + {
      if l <= 60 then " minute" else " minutes"
    }

  def clockConfigMappingsMinutes(clockTimes: Seq[Double], byoyomiLimits: Seq[Int]): Mapping[ClockConfig] =
    clockConfigMappingsSeconds(clockTimes.map(_ * 60d).map(_.toInt), byoyomiLimits)

  def clockConfigMappingsSeconds(clockTimes: Seq[Int], byoyomiLimits: Seq[Int]): Mapping[ClockConfig] =
    val clockTimeChoices = options(clockTimes, format = formatLimit)

    mapping[ClockConfig, Boolean, Boolean, Boolean, Int, Int, Int, Option[Int], Option[Int]](
      "useByoyomi"        -> boolean,
      "useBronsteinDelay" -> boolean,
      "useSimpleDelay"    -> boolean,
      "limit"             -> numberIn(clockTimeChoices),
      "increment"         -> number(min = 0, max = 120),
      "delay"             -> number(min = 0, max = 120),
      "byoyomi"           -> optional(number.verifying(byoyomiLimits.contains)),
      "periods"           -> optional(number(min = 0, max = 5))
    )(clockConfigFromValues)(valuesFromClockConfig)

  def clockConfigMappingsFromMinutes(clockTimes: Seq[Int], byoyomiLimits: Seq[Int]): Mapping[ClockConfig] =
    clockConfigMappingsMinutes(clockTimes.map(_.toDouble), byoyomiLimits)

  def optionsMinutes(it: Iterable[Int], format: Int => String): Options[Int] =
    it.map(d => d -> format(d))

  def clockTimeChoicesFromMinutes(clockTimes: Seq[Double]) =
    optionsMinutes(clockTimes.map(_ * 60).map(_.toInt), formatLimit)

  def clockTimeChoicesFromSeconds(clockTimes: Seq[Int]) =
    optionsMinutes(clockTimes, format = formatLimit)
