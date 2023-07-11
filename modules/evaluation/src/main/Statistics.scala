package lila.evaluation

import strategygames.{ Centis, Stats }
import lila.common.Maths

object Statistics {

  case class IntAvgSd(avg: Int, sd: Int) {
    override def toString = s"$avg ± $sd"
    def /(div: Int)       = IntAvgSd(avg / div, sd / div)
  }

  def intAvgSd(values: List[Int]) = IntAvgSd(
    avg = listAverage(values).toInt,
    sd = listDeviation(values).toInt
  )

  // Coefficient of Variance
  def coefVariation(a: List[Int]): Option[Float] = {
    val s = Stats(a)
    s.stdDev.map { _ / s.mean }
  }

  // ups all values by 0.1s as to avoid very high variation on bullet games
  // where all ply times are low and drops the first ply because it's always 0
  def plyTimeCoefVariation(a: List[Centis]): Option[Float] =
    coefVariation(a.drop(1).map(_.centis + 10))

  def plyTimeCoefVariationNoDrop(a: List[Centis]): Option[Float] =
    coefVariation(a.map(_.centis + 10))

  def plyTimeCoefVariation(pov: lila.game.Pov): Option[Float] =
    for {
      mt   <- plyTimes(pov)
      coef <- plyTimeCoefVariation(mt)
    } yield coef

  def plyTimes(pov: lila.game.Pov): Option[List[Centis]] =
    pov.game.plyTimes(pov.playerIndex)

  def cvIndicatesHighlyFlatTimes(c: Float) =
    c < 0.25

  def cvIndicatesHighlyFlatTimesForStreaks(c: Float) =
    c < 0.14

  def cvIndicatesModeratelyFlatTimes(c: Float) =
    c < 0.4

  private val instantaneous = Centis(0)

  def slidingMoveTimesCvs(pov: lila.game.Pov): Option[Iterator[Float]] =
    plyTimes(pov) ?? {
      _.iterator
        .sliding(14)
        .map(_.toList.sorted.drop(1).dropRight(1))
        .filter(_.count(instantaneous ==) < 4)
        .flatMap(plyTimeCoefVariationNoDrop)
        .some
    }

  def moderatelyConsistentPlyTimes(pov: lila.game.Pov): Boolean =
    plyTimeCoefVariation(pov) ?? { cvIndicatesModeratelyFlatTimes(_) }

  private val fastPly = Centis(50)
  def noFastPlies(pov: lila.game.Pov): Boolean = {
    val plyTimes = ~pov.game.plyTimes(pov.playerIndex)
    plyTimes.count(fastPly >) <= (plyTimes.size / 20) + 2
  }

  def listAverage[T: Numeric](x: List[T]) = ~Maths.mean(x)

  def listDeviation[T: Numeric](x: List[T]) = ~Stats(x).stdDev
}
