package lila.tournament

import strategygames.variant.Variant
import strategygames.GameFamily

object TournamentMedleyUtil {

  def medleyVariantsAndIntervals(
      orderedMedleyList: List[Variant],
      gameClockSeconds: Int,
      minutes: Int,
      mNumIntervals: Int,
      mBalanced: Boolean
  ): List[(Variant, Int)] = {
    val medleyVariantsInTournament: List[Variant] = orderedMedleyList.take(mNumIntervals)
    val medleySpeedChoice = orderedMedleyList match {
      case x if isMedleyChessShieldStyle(x)    => medleyChessShieldSpeeds
      case x if isMedleyDraughtsShieldStyle(x) => medleyDraughtShieldSpeeds
      case _                                   => medleyVariantSpeeds
    }
    val medleySpeedFactors: List[Double] =
      medleyVariantsInTournament.map(v => medleySpeedChoice.get(v.key).getOrElse(1.0))
    val medleyIntervalSeconds: List[Int] =
      if (mBalanced) {
        val times: List[Int] =
          medleySpeedFactors.map(s => (s * (minutes / medleySpeedFactors.sum) * 60).toInt)
        val extra               = minutes * 60 - times.sum
        val firstLastBonus: Int = math.min(gameClockSeconds / 3, 120)
        // take time from first variant and give to last
        times.take(1).map(v => v - firstLastBonus) ::: times.drop(1).take(times.size - 2) :::
          times.drop(times.size - 1).map(v => v + extra + firstLastBonus)
      } else {
        defaultIntervalTimes(minutes, mNumIntervals)
      }

    orderedMedleyList.zipWithIndex
      .map { case (v, i) => (v, medleyIntervalSeconds.lift(i).getOrElse(0)) }
  }

  def defaultIntervalTimes(minutes: Int, mNumIntervals: Int) = {
    val intervals = List.fill(mNumIntervals)((minutes / mNumIntervals) * 60)
    intervals.updated(intervals.size - 1, intervals.last + minutes * 60 - intervals.sum)
  }

  def isMedleyChessShieldStyle(variants: List[Variant]): Boolean =
    variants.filterNot(_.exoticChessVariant).isEmpty

  def isMedleyDraughtsShieldStyle(variants: List[Variant]): Boolean =
    variants.filterNot(_.gameFamily == GameFamily.Draughts()).isEmpty &&
      variants.filter(_.fromPositionVariant).isEmpty

  def medleyVariantSpeeds: Map[String, Double] = {
    val slow    = 1.25
    val medium  = 1.1
    val quick   = 0.9
    val fastest = 0.75
    Map(
      "standard"      -> medium,
      "chess960"      -> medium,
      "kingOfTheHill" -> quick,
      "threeCheck"    -> fastest,
      "fiveCheck"     -> quick,
      "antichess"     -> quick,
      "atomic"        -> fastest,
      "horde"         -> slow,
      "racingKings"   -> fastest,
      "crazyhouse"    -> quick,
      "noCastling"    -> medium,
      "monster"       -> quick,
      "linesOfAction" -> fastest,
      "scrambledEggs" -> fastest,
      "frisian"       -> medium,
      "frysk"         -> quick,
      "international" -> slow,
      "antidraughts"  -> slow,
      "breakthrough"  -> slow,
      "russian"       -> medium,
      "brazilian"     -> medium,
      "pool"          -> medium,
      "portuguese"    -> medium,
      "english"       -> medium,
      "shogi"         -> slow,
      "xiangqi"       -> medium,
      "minishogi"     -> fastest,
      "minixiangqi"   -> quick,
      "flipello"      -> medium,
      "flipello10"    -> slow,
      "amazons"       -> medium,
      "oware"         -> slow,
      "togyzkumalak"  -> slow,
      "go9x9"         -> medium,
      "go13x13"       -> slow,
      "go19x19"       -> slow,
      "backgammon"    -> medium,
      "nackgammon"    -> medium
    )
  }

  def medleyChessShieldSpeeds: Map[String, Double] = {
    val slow   = 1.25
    val medium = 1
    val quick  = 0.75
    Map(
      "kingOfTheHill" -> medium,
      "threeCheck"    -> quick,
      "antichess"     -> medium,
      "atomic"        -> quick,
      "horde"         -> slow,
      "racingKings"   -> quick,
      "crazyhouse"    -> medium,
      "monster"       -> quick
    )
  }

  def medleyDraughtShieldSpeeds: Map[String, Double] = {
    val slow   = 1.25
    val medium = 1
    val quick  = 0.75
    Map(
      "frisian"       -> medium,
      "frysk"         -> quick,
      "international" -> slow,
      "antidraughts"  -> slow,
      "breakthrough"  -> slow,
      "russian"       -> medium,
      "brazilian"     -> medium,
      "pool"          -> medium,
      "portuguese"    -> medium,
      "english"       -> medium
    )
  }

}
