package views.html.tournament

import org.joda.time.DateTime

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.i18n.I18nKey

// A recurring series with one winner per edition — a game's shield, its yearly arena.
// Everything here derives from the list of editions, newest first.
object series {

  case class Win(userId: String, date: DateTime, url: String)

  case class Next(url: String, startsAt: DateTime)

  // contested: a shield, defended month after month, so streaks and a dare make sense; otherwise a
  // title, simply won. subject names the series: "Abalone", "Yearly Abalone".
  case class Wording(contested: Boolean, subject: String) {
    private def pick(shield: I18nKey, title: I18nKey) = if (contested) shield else title
    def holderLabel(implicit ctx: Context) =
      pick(trans.seriesCurrentHolder, trans.seriesReigningChampion).txt()
    def noHolder(implicit ctx: Context)  = pick(trans.seriesNoShieldHolder, trans.seriesNoTitleHolder).txt()
    def unitsName(implicit ctx: Context) = pick(trans.seriesShieldsOf, trans.seriesTitlesOf).txt(subject)
    def most(implicit ctx: Context)      = pick(trans.seriesMostShields, trans.seriesMostTitles).txt()
    def mostShared(implicit ctx: Context) =
      pick(trans.seriesMostShieldsShared, trans.seriesMostTitlesShared).txt()
    def inARow(n: Int)(implicit ctx: Context) =
      pick(trans.seriesShieldsInARow, trans.seriesTitlesInARow).pluralSameTxt(n)
    def count(n: Int)(implicit ctx: Context) =
      pick(trans.seriesNbShields, trans.seriesNbTitles).pluralSameTxt(n)
    def nextLabel(implicit ctx: Context) =
      if (contested) trans.seriesTakeShield.txt() else trans.seriesNextArena.txt(subject)
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

    // podium place (1 to 3) and who else sits on it; players strictly ahead decide the place
    def rankOf(userId: String): Option[(Int, List[String])] =
      tally.find(_._1 == userId).map(_._2).filter(_ > 1).flatMap { won => // one win is not a record
        val tiedWith = tally.collect { case (other, n) if n == won && other != userId => other }
        val place    = 1 + tally.count(_._2 > won)
        (place <= 3).option(place -> tiedWith)
      }
  }

  // for the page's meta description: record holders, longest reign, current holder
  def description(stats: Stats, w: Wording)(implicit ctx: Context): String = {
    val recordHolders = stats.tally.take(3).map { case (userId, n) => s"${usernameOrId(userId)} ($n)" }
    recordHolders.nonEmpty.so(" " + trans.seriesDescRecord.txt(w.most, recordHolders.mkString(", "))) +
      stats.longestReigns.headOption.filter(_ => w.contested).so { r =>
        val who = listPeople(stats.longestReigns.map(_.userId).distinct)
        " " + trans.seriesDescLongestReign.txt(who, r.count)
      } +
      stats.current.so { h =>
        " " + trans.seriesDescHolder.txt(w.holderLabel, usernameOrId(h.userId), showDate(h.date))
      }
  }

  def holderCard(stats: Stats, next: Option[Next], trophy: Frag, w: Wording)(implicit
      ctx: Context
  ): Frag =
    // with no holder and no edition coming there is nothing to show, not even the box
    if (stats.current.isEmpty && next.isEmpty) emptyFrag
    else
      div(cls := "categ-shield-holder")(
        trophy,
        stats.current.isEmpty.option(
          p(cls := "categ-shield-holder__challenge")(w.noHolder)
        ),
        stats.current.map { holder =>
          frag(
            span(cls := "categ-shield-holder__label")(w.holderLabel),
            userIdLink(holder.userId.some, cssClass = "reigning-shield-holder".some, withOnline = false),
            span(cls := "categ-shield-holder__since")(
              trans.seriesSince(raw(a(href := holder.url)(showDate(holder.date)).render)),
              stats.currentReign
                .filter(r => w.contested && r.count > 1)
                .map(r => frag(" · ", trans.seriesInARow.txt(r.count)))
            )
          )
        },
        // contested: name what the holder has built up; otherwise the record, if any
        stats.current.map { holder =>
          val name   = usernameOrId(holder.userId)
          val streak = stats.currentReign.map(_.count).filter(_ > 1).filter(_ => w.contested).map { n =>
            trans.seriesHeldShieldInARow.txt(name, n)
          }
          val rank = stats.rankOf(holder.userId).map {
            case (1, Nil)  => trans.seriesHasMost.txt(name, w.unitsName)
            case (2, Nil)  => trans.seriesHasSecondMost.txt(name, w.unitsName)
            case (_, Nil)  => trans.seriesHasThirdMost.txt(name, w.unitsName)
            case (1, tied) => trans.seriesSharesMost.txt(name, w.unitsName, listPeople(tied))
            case (2, tied) => trans.seriesSharesSecondMost.txt(name, w.unitsName, listPeople(tied))
            case (_, tied) => trans.seriesSharesThirdMost.txt(name, w.unitsName, listPeople(tied))
          }
          val feat = streak.toList ::: rank.toList
          feat.nonEmpty.option(p(cls := "categ-shield-holder__challenge")(feat.mkString(" ")))
        },
        next.map { t =>
          a(cls := "button categ-shield-holder__next", href := t.url)(
            s"${w.nextLabel} · ",
            absClientDateTime(t.startsAt)
          )
        }
      )

  // "lukas", "lukas and vincent", "lukas, vincent and primodragon", "5 other players"
  private def listPeople(userIds: List[String])(implicit ctx: Context): String =
    userIds.map(usernameOrId) match {
      case names if names.sizeIs > 3 => trans.seriesOtherPlayers.txt(names.size)
      case names if names.sizeIs > 1 => trans.seriesAnd.txt(names.init.mkString(", "), names.last)
      case names                     => names.mkString
    }

  def record(stats: Stats, w: Wording)(implicit ctx: Context): Option[Frag] =
    longestReign(stats, w) orElse mostWins(stats, w)

  private def mostWins(stats: Stats, w: Wording)(implicit ctx: Context): Option[Frag] =
    stats.tally.headOption.map(_._2).filter(_ > 1).map { max =>
      val holders = stats.tally.filter(_._2 == max).map(_._1)
      div(cls := "shield-record")(
        span(cls := "shield-record__label")(
          if (holders.sizeIs > 1) w.mostShared else w.most,
          " — ",
          max
        ),
        ul(cls := "shield-record__list")(
          holders.map(userId => li(span(cls := "shield-record__value")(userIdLink(userId.some, withOnline = false))))
        )
      )
    }

  private def longestReign(stats: Stats, w: Wording)(implicit ctx: Context): Option[Frag] =
    stats.longestReigns.headOption.filter(_.count >= 3).map { first =>
      div(cls := "shield-record")(
        span(cls := "shield-record__label")(
          if (stats.longestReigns.sizeIs > 1) trans.seriesLongestReignShared.txt()
          else trans.seriesLongestReign.txt(),
          " — ",
          w.inARow(first.count)
        ),
        ul(cls := "shield-record__list")(
          stats.longestReigns.map { r =>
            li(
              span(cls := "shield-record__value")(userIdLink(r.userId.some, withOnline = false)),
              span(cls := "shield-record__span")(
                showDate(r.from),
                " – ",
                r.until.fold[Frag](em(trans.seriesStillHolding()))(d => frag(showDate(d)))
              )
            )
          }
        )
      )
    }

  def podium(stats: Stats, w: Wording)(implicit ctx: Context): Option[Frag] =
    (stats.tally.sizeIs > 1).option(
      frag(
        h2(cls := "shield-section")(w.most),
        ol(cls := "shield-podium")(
          stats.tally.take(10).map { case (userId, n) =>
            // players on the same count share a place, and so a medal: 1, 1, 3
            val place = 1 + stats.tally.count(_._2 > n)
            li(cls := (place <= 3).option(s"podium podium--$place"))(
              span(cls := "rank")(place),
              userIdLink(userId.some, withOnline = false),
              span(cls := "count")(w.count(n))
            )
          }
        )
      )
    )
}
