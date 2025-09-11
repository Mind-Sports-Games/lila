package views.html.library

import lila.i18n.{ I18nKeys => trans }
import strategygames.variant.Variant
import strategygames.GameLogic
import org.joda.time.DateTime
import org.joda.time.Days
import org.joda.time.format.DateTimeFormat

object bits {
  def transformData(data: List[(String, String, Long)]): List[(String, String, Long)] =
    data.map { case (ym, lib_var, count) => (ym, getVariantKey(lib_var), count) }

  private def variantKey(variant: Variant) = s"${variant.gameFamily.id}_${variant.id}"
  private def getVariantKey(lib_var: String) =
    lib_var.split('_') match {
      case Array(lib, id) => {
        val variant = Variant.orDefault(GameLogic(lib.toInt), id.toInt)
        variantKey(variant)
      }
      case _ => "0_1" //standard chess
    }

  def totalVariants(data: List[(String, String, Long)]): Int =
    data.map(d => getVariantKey(d._2)).distinct.size

  def totalGames(data: List[(String, String, Long)]): Int = data.map(_._3).sum.toInt

  def totalGamesForVariant(data: List[(String, String, Long)], variant: Variant): Int =
    data.filter(d => getVariantKey(d._2) == variantKey(variant)).map(_._3).sum.toInt

  def firstGamePlayedForVariant(data: List[(String, String, Long)], variant: Variant): Option[DateTime] =
    data
      .filter(d => getVariantKey(d._2) == variantKey(variant))
      .map(_._1)
      .sorted
      .headOption
      .map(dateFormat.parseDateTime)

  def gamePerDayForVariant(data: List[(String, String, Long)], variant: Variant): Option[Double] =
    firstGamePlayedForVariant(data, variant).map { first =>
      val days = Days.daysBetween(first, DateTime.now).getDays
      if (days > 0) totalGamesForVariant(data, variant).toDouble / days.toDouble
      else totalGamesForVariant(data, variant).toDouble
    }

  def gamesPerDay(data: List[(String, String, Long)], variant: Variant): String =
    gamePerDayForVariant(data, variant) match {
      case Some(gpd) => f"$gpd%.2f"
      case None      => "N/A"
    }

  def totalGamesLastFullMonth(data: List[(String, String, Long)]): Int =
    data.filter(_._1 == lastFullMonth).map(_._3.toInt).sum

  def totalGamesLastFullMonthForVariant(data: List[(String, String, Long)], variant: Variant): Int =
    data
      .filter(d => d._1 == lastFullMonth && getVariantKey(d._2) == variantKey(variant))
      .map(_._3.toInt)
      .sum

  private val dateFormat        = DateTimeFormat.forPattern("yyyy-MM")
  private val releaseDateFormat = DateTimeFormat.forPattern("yyyy-MM-dd")
  val lastFullMonth: String     = DateTime.now.minusMonths(1).withDayOfMonth(1).toString(dateFormat)

  def releaseDateDisplay(data: List[(String, String, Long)], variant: Variant) =
    firstGamePlayedForVariant(data, variant)
      .map(_.toString(releaseDateFormat))
      .getOrElse("N/A")

  def studyLink(variant: Variant): Option[String] = {
    variant.key match {
      case "abalone"       => Some("AbaloneS")
      case "linesOfAction" => Some("LinesOfA")
      case _               => None
    }
  }

  val i18nKeys =
    List(
      trans.players,
      trans.cumulative
    ).map(_.key)
}
