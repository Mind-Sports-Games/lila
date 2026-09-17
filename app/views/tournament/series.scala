package views.html.tournament

import org.joda.time.DateTime

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.tournament.Tournament

// A recurring series with one winner per edition — a game's shield, its yearly arena.
// Everything here derives from the list of editions, newest first.
object series {

  case class Win(userId: String, date: DateTime, tourId: String)

  case class Wording(
      holderLabel: String, // "Current holder", "Reigning champion"
      unit: String,        // what a win is: "shield", "title"
      unitsName: String,   // what is counted on the podium: "Abalone shields", "Yearly Abalone titles"
      arenaName: String,   // the next edition: "shield arena", "Yearly Abalone Arena"
      takeLabel: String,   // the button: "Take the shield", "Take the title"
      // a shield is defended month after month, so streaks and a dare make sense;
      // a yearly title is simply won, so only the count of titles is shown
      contested: Boolean = true
  ) {
    def units = unit + "s"
  }

  // consecutive editions won by the same player; the newest reign may still be running
  case class Reign(userId: String, count: Int, from: DateTime, until: Option[DateTime])

  case class Stats(wins: List[Win]) {
    val current: Option[Win] = wins.headOption

    // most wins, ties broken by the earlier first win
    val tally: List[(String, Int)] = wins
      .groupBy(_.userId)
      .toList
      .map { case (userId, won) => (userId, won.size, won.map(_.date.getMillis).min) }
      .sortBy { case (_, n, first) => (-n, first) }
      .map { case (userId, n, _) => (userId, n) }

    val reigns: List[Reign] = wins.reverse.foldLeft(List.empty[Reign]) {
      case (Reign(owner, n, from, _) :: rest, w) if owner == w.userId =>
        Reign(owner, n + 1, from, none) :: rest
      case (prev :: rest, w) =>
        Reign(w.userId, 1, w.date, none) :: prev.copy(until = w.date.some) :: rest
      case (Nil, w) => List(Reign(w.userId, 1, w.date, none))
    }
    val currentReign: Option[Reign] = reigns.headOption

    // every reign sharing the record, oldest first; a single edition is not a reign
    val longestReigns: List[Reign] = reigns.map(_.count).maxOption.filter(_ > 1).so { max =>
      reigns.filter(_.count == max).sortBy(_.from.getMillis)
    }

    // podium position ("the most", "the second most") and who else sits on it;
    // players strictly ahead decide the position
    def rankOf(userId: String): Option[(String, List[String])] =
      tally.find(_._1 == userId).map(_._2).filter(_ > 1).flatMap { won => // one win is not a record
        val tiedWith = tally.collect { case (other, n) if n == won && other != userId => other }
        tally.count(_._2 > won) match {
          case 0 => Some("the most" -> tiedWith)
          case 1 => Some("the second most" -> tiedWith)
          case 2 => Some("the third most" -> tiedWith)
          case _ => None
        }
      }
  }

  // for the page's meta description: record holders, longest reign, current holder
  def description(stats: Stats, w: Wording)(implicit ctx: Context): String = {
    val recordHolders = stats.tally.take(3).map { case (userId, n) => s"${usernameOrId(userId)} ($n)" }
    (if (recordHolders.nonEmpty) s" Most ${w.units}: ${recordHolders.mkString(", ")}." else "") +
      stats.longestReigns.headOption.filter(_ => w.contested).so { r =>
        val who = stats.longestReigns.map(x => usernameOrId(x.userId)).distinct
        s" Longest reign: ${who.mkString(" and ")}, ${r.count} in a row."
      } +
      stats.current.so(h => s" ${w.holderLabel}: ${usernameOrId(h.userId)} since ${showDate(h.date)}.")
  }

  def holderCard(stats: Stats, next: Option[Tournament], trophy: Frag, w: Wording)(implicit
      ctx: Context
  ): Frag =
    div(cls := "categ-shield-holder")(
      trophy,
      stats.current.map { holder =>
        frag(
          span(cls := "categ-shield-holder__label")(w.holderLabel),
          userIdLink(holder.userId.some, cssClass = "reigning-shield-holder".some, withOnline = false),
          span(cls := "categ-shield-holder__since")(
            "since ",
            a(href := routes.Tournament.show(holder.tourId))(showDate(holder.date)),
            stats.currentReign.filter(r => w.contested && r.count > 1).map(r => frag(" · ", r.count, " in a row"))
          )
        )
      },
      // contested: dare the reader by naming what the holder has built up, then point at the next arena;
      // otherwise just state the record, if any
      stats.current.map { holder =>
        val name   = usernameOrId(holder.userId)
        val streak = stats.currentReign.map(_.count).filter(_ > 1).filter(_ => w.contested)
        // "has the most X of anyone" / "shares the most X with lukas and vincent" / "has the second most X"
        val rank = stats.rankOf(holder.userId).map {
          case (ord, Nil) if ord == "the most" => s"has $ord ${w.unitsName} of anyone"
          case (ord, Nil)                      => s"has $ord ${w.unitsName}"
          case (ord, tied)                     => s"shares $ord ${w.unitsName} with ${listPeople(tied)}"
        }
        val feat = (streak, rank) match {
          case (Some(n), Some(r)) => s"$name has held the ${w.unit} for $n editions in a row and $r.".some
          case (Some(n), None)    => s"$name has held the ${w.unit} for $n editions in a row.".some
          case (None, Some(r))    => s"$name $r.".some
          case (None, None)       => w.contested.option(s"$name took the ${w.unit} on ${showDate(holder.date)}.")
        }
        val dare = w.contested.option(
          next.fold(s"The next ${w.arenaName} will be scheduled soon.")(_ =>
            s"Take it from them in the next ${w.arenaName}."
          )
        )
        (feat.toList ::: dare.toList).some.filter(_.nonEmpty).map { parts =>
          p(cls := "categ-shield-holder__challenge")(parts.mkString(" "))
        }
      },
      next.map { t =>
        a(cls := "button categ-shield-holder__next", href := routes.Tournament.show(t.id))(
          s"${if (w.contested) w.takeLabel else s"Next ${w.arenaName}"} · ",
          absClientDateTime(t.startsAt)
        )
      }
    )

  // "lukas", "lukas and vincent", "lukas, vincent and primodragon", "5 other players"
  private def listPeople(userIds: List[String]): String =
    userIds.map(usernameOrId) match {
      case names if names.sizeIs > 3 => s"${names.size} other players"
      case names if names.sizeIs > 1 => s"${names.init.mkString(", ")} and ${names.last}"
      case names                     => names.mkString
    }

  def longestReign(stats: Stats, w: Wording)(implicit ctx: Context): Option[Frag] =
    stats.longestReigns.headOption.map { first =>
      div(cls := "shield-record")(
        span(cls := "shield-record__label")(
          if (stats.longestReigns.sizeIs > 1) "Longest reign, shared" else "Longest reign",
          " — ",
          first.count,
          s" ${w.units} in a row"
        ),
        ul(cls := "shield-record__list")(
          stats.longestReigns.map { r =>
            li(
              span(cls := "shield-record__value")(userIdLink(r.userId.some, withOnline = false)),
              span(cls := "shield-record__span")(
                showDate(r.from),
                " – ",
                r.until.fold[Frag](em("still holding"))(d => frag(showDate(d)))
              )
            )
          }
        )
      )
    }

  def podium(stats: Stats, w: Wording)(implicit ctx: Context): Option[Frag] =
    (stats.tally.sizeIs > 1).option(
      frag(
        h2(cls := "shield-section")(s"Most ${w.units}"),
        ol(cls := "shield-podium")(
          stats.tally.take(10).zipWithIndex.map { case ((userId, n), i) =>
            li(cls := (i < 3).option(s"podium podium--${i + 1}"))(
              span(cls := "rank")(i + 1),
              userIdLink(userId.some, withOnline = false),
              span(cls := "count")(n, " ", if (n == 1) w.unit else w.units)
            )
          }
        )
      )
    )
}
