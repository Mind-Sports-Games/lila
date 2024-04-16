package lila.app
package templating

import play.api.i18n.Lang
import play.api.libs.json.Json

import controllers.routes
import lila.app.ui.ScalatagsTemplate._
import lila.rating.PerfType
import strategygames.Speed
import lila.tournament.{ Schedule, Tournament }
import lila.user.User

trait TournamentHelper { self: I18nHelper with DateHelper with UserHelper =>

  def netBaseUrl: String

  def tournamentJsData(tour: Tournament, version: Int, user: Option[User]) = {

    val data = Json.obj(
      "tournament" -> Json.obj("id" -> tour.id),
      "version"    -> version
    )
    Json stringify {
      user.fold(data) { u =>
        data ++ Json.obj("username" -> u.username)
      }
    }
  }

  def tournamentLink(tour: Tournament)(implicit lang: Lang): Frag =
    a(
      dataIcon := "g",
      cls := (if (tour.isScheduled) "text is-gold" else "text"),
      href := routes.Tournament.show(tour.id).url
    )(tour.name())

  def tournamentLink(tourId: String)(implicit lang: Lang): Frag =
    a(
      dataIcon := "g",
      cls := "text",
      href := routes.Tournament.show(tourId).url
    )(tournamentIdToName(tourId))

  def tournamentIdToName(id: String)(implicit lang: Lang) =
    env.tournament.getTourName get id getOrElse "Tournament"

  object scheduledTournamentNameShortHtml {
    private def icon(c: Char) = s"""<span data-icon="$c"></span>"""
    private val replacements = List(
      "PlayStrategy " -> "",
      "Marathon"      -> icon('\\'),
      "HyperBullet"   -> s"H${icon(Speed.Bullet.perfIcon)}",
      "SuperBlitz"    -> s"S${icon(Speed.Blitz.perfIcon)}",
      "Grand Prix"    -> "GP",
      " PREMIER"      -> "",
      " -"            -> ""
    ) ++ PerfType.leaderboardable
      .filterNot(PerfType.translated.contains)
      .map { pt =>
        pt.trans(lila.i18n.defaultLang) -> icon(pt.iconChar)
      }
      .sortBy(-_._1.length) ++
      List(
        "Chess"    -> icon(strategygames.chess.variant.Standard.perfIcon),
        "Draughts" -> icon(strategygames.draughts.variant.Standard.perfIcon)
      )

    def apply(name: String): Frag =
      raw {
        if (name.contains("Medley Shield"))
          name.split(" ").dropRight(1).mkString(" ").replace(" Medley Shield", icon('5'))
        else if (name.contains("End of Year"))
          (name.split(" ").dropRight(2) ++ name.split(" ").takeRight(1)).toList.mkString(" ")
        else
          //old replacements
          replacements.foldLeft(name) { case (n, (from, to)) =>
            n.replace(from, to)
          }
      }
  }

  def tournamentIconChar(tour: Tournament): String =
    tour.schedule.map(_.freq) match {
      case Some(Schedule.Freq.Marathon | Schedule.Freq.ExperimentalMarathon) => "\\"
      case _                                                                 => tour.spotlight.flatMap(_.iconFont) | tour.iconChar.toString
    }
}
