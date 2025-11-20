package views.html.library

import lila.i18n.{ I18nKeys => trans }
import lila.app.ui.ScalatagsTemplate._
import strategygames.variant.Variant
import strategygames.GameLogic
import org.joda.time.DateTime
import org.joda.time.Days
import org.joda.time.format.DateTimeFormat
import lila.game.{ MonthlyGameData, WinRatePercentages }

object bits {
  def transformData(data: List[MonthlyGameData]): List[(String, String, Long)] =
    data.map { case MonthlyGameData(yearMonth, libVar, count) => (yearMonth, getVariantKey(libVar), count) }

  private def variantKey(variant: Variant) = s"${variant.gameFamily.id}_${variant.id}"
  private def getVariantKey(lib_var: String) =
    lib_var.split('_') match {
      case Array(lib, id) => {
        val variant = Variant.orDefault(GameLogic(lib.toInt), id.toInt)
        variantKey(variant)
      }
      case _ => "0_1" //standard chess
    }

  def totalVariants(data: List[MonthlyGameData]): Int =
    data.map(d => getVariantKey(d.libVar)).distinct.size

  def totalGames(data: List[MonthlyGameData]): Int = data.map(_.count).sum.toInt

  def totalGamesForVariant(data: List[MonthlyGameData], variant: Variant): Int =
    data.filter(d => getVariantKey(d.libVar) == variantKey(variant)).map(_.count).sum.toInt

  def firstGamePlayedForVariant(data: List[MonthlyGameData], variant: Variant): Option[DateTime] =
    data
      .filter(d => getVariantKey(d.libVar) == variantKey(variant))
      .map(_.yearMonth)
      .sorted
      .headOption
      .map(dateFormat.parseDateTime)

  def gamePerDayForVariant(data: List[MonthlyGameData], variant: Variant): Option[Double] =
    firstGamePlayedForVariant(data, variant).map { first =>
      val days = Days.daysBetween(first, DateTime.now).getDays
      if (days > 0) totalGamesForVariant(data, variant).toDouble / days.toDouble
      else totalGamesForVariant(data, variant).toDouble
    }

  def gamesPerDay(data: List[MonthlyGameData], variant: Variant): String =
    gamePerDayForVariant(data, variant) match {
      case Some(gpd) => f"$gpd%.2f"
      case None      => "N/A"
    }

  def totalGamesLastFullMonth(data: List[MonthlyGameData]): Int =
    data.filter(_.yearMonth == lastFullMonth).map(_.count.toInt).sum

  def totalGamesLastFullMonthForVariant(data: List[MonthlyGameData], variant: Variant): Int =
    data
      .filter(d => d.yearMonth == lastFullMonth && getVariantKey(d.libVar) == variantKey(variant))
      .map(_.count.toInt)
      .sum

  private val dateFormat    = DateTimeFormat.forPattern("yyyy-MM")
  val lastFullMonth: String = DateTime.now.minusMonths(1).withDayOfMonth(1).toString(dateFormat)

  def releaseDateDisplay(data: List[MonthlyGameData], variant: Variant) =
    firstGamePlayedForVariant(data, variant)
      .map(_.toString(dateFormat))
      .getOrElse("N/A")

  def studyLink(variant: Variant): Option[String] = {
    variant.key match {
      case "abalone"       => Some("AbaloneS")
      case "linesOfAction" => Some("LinesOfA")
      case _               => None
    }
  }

  def winRatePlayer1(variant: Variant, winRates: List[WinRatePercentages]): String =
    winRates
      .filter(w => getVariantKey(w.libVar) == variantKey(variant))
      .headOption
      .map(_.p1)
      .getOrElse(0)
      .toString() + "%"

  def winRatePlayer2(variant: Variant, winRates: List[WinRatePercentages]): String =
    winRates
      .filter(w => getVariantKey(w.libVar) == variantKey(variant))
      .headOption
      .map(_.p2)
      .getOrElse(0)
      .toString() + "%"

  def winRateDraws(variant: Variant, winRates: List[WinRatePercentages]): String =
    winRates
      .filter(w => getVariantKey(w.libVar) == variantKey(variant))
      .headOption
      .map(_.draw)
      .getOrElse(0)
      .toString() + "%"

  def statsRow(term: String, value: String, className: String = "") =
    div(cls := s"library-stats-row $className")(
      div(cls := "library-stats-term")(term),
      div(cls := "library-stats-value")(value)
    )

  val i18nKeys =
    List(
      trans.players,
      trans.cumulative
    ).map(_.key)
}
