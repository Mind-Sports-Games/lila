package views.html.library

import lila.i18n.{ I18nKeys => trans }
import strategygames.variant.Variant
import strategygames.GameLogic

object bits {
  def transformData(data: List[(String, String, Long)]): List[(String, String, Long)] =
    data.map { case (ym, lib_var, count) => (ym, getVaraintKey(lib_var), count) }

  private def getVaraintKey(lib_var: String) =
    lib_var.split('_') match {
      case Array(lib, id) => {
        val variant = Variant.orDefault(GameLogic(lib.toInt), id.toInt)
        s"${variant.gameFamily.id}_${variant.id}"
      }
      case _ => "0_1" //standard chess
    }

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
