package lila.common

import scala.util.matching.Regex

object LameName {

  def username(name: String): Boolean =
    usernameRegex.find(name.replaceIf('_', "")) || containsTitleRegex.matches(name) || containsUsRegex
      .matches(name)

  def tournament(name: String): Boolean = tournamentRegex find name

  def team(name: String): Boolean =
    teamRegex.find(name.replaceIf('_', "")) || containsUsAnywhereRegex.matches(name)

  private val titlePattern = "W*(?:[NCFI1L]|I?G)"
  private val containsTitleRegex = (
    "^"
      + "(?i:" + titlePattern + "M[^a-z].*)|"                  // title at start, separated by non-letter
      + "(?:(?i:" + titlePattern + ")m[^a-z].*)|"              // title at start with lowercase m, not followed by lowercase letter
      + "(?:" + titlePattern + "M.*)|"                         // uppercase title at start
      + "(?i:.*[^a-z]" + titlePattern + "M)|"                  // title at end, separated by non-letter
      + "(?i:.*[^a-z]" + titlePattern + "M[^a-z].*)|"          // title in middle, surrounded by non-letters
      + "(?:.*[^A-Z]" + titlePattern + "M(?:[A-Z]?[^A-Z].*)?)" // uppercase title not preceeded by uppercase letter,
      + "$"                                                    //   either at end or followed by at most one uppercase letter and then something else
  ).r

  private val baseWords = List(
    "1488",
    "8814",
    "admin",
    "administrator",
    "anus",
    "asshole",
    "bastard",
    "biden",
    "bitch",
    "butthole",
    "buttsex",
    "cancer",
    "cheat",
    "coon",
    "corona",
    "covid",
    "cuck",
    "cunniling",
    "cunt",
    "cyka",
    "dick",
    "douche",
    "fag",
    "fart",
    "feces",
    "fuck",
    "golam",
    "hitler",
    "idiot",
    "jerk",
    "kanker",
    "kunt",
    "moderator",
    "mongool",
    "nazi",
    "nigg",
    "paedo",
    "pedo",
    "penis",
    "pidar",
    "pidr",
    "piss",
    "poon",
    "poop",
    "poxyu",
    "pussy",
    "putin",
    "resign",
    "retard",
    "shit",
    "slut",
    "suicid",
    "trump",
    "vagin",
    "wanker",
    "whore",
    "xyula",
    "xyulo",
    "xyuta"
  )

  private val organisations = List(
    "playstrat",
    "lichess",
    "mindsportsolympiad",
    "lidraughts",
    "lishogi",
    "pychess",
    "backgammonhub"
  )

  private val usernameRegex = lameWords(baseWords)

  private val tournamentRegex = lameWords(baseWords)

  private val teamRegex = lameWords(baseWords)

  private val containsUsRegex = lameWords(organisations, ".*")

  private val containsUsAnywhereRegex = (
    "^"
      + organisations.map("(?i:.*" + _.toString + ".*)?").mkString("|")
      + "$"
  ).r

  private def lameWords(list: List[String], interspersed: String = "+"): Regex = {
    val extras = Map(
      'a' -> "4",
      'e' -> "38",
      'g' -> "q9",
      'i' -> "l1",
      'l' -> "I1",
      'o' -> "08",
      's' -> "5",
      'u' -> "v",
      'z' -> "2"
    )

    val subs: Map[Char, String] = {
      (('a' to 'z' map { c =>
        c -> s"[$c${c.toUpper}${~extras.get(c)}]"
      }) ++ Seq('0' -> "[0O]", '1' -> "[1Il]", '8' -> "[8B]"))
    }.toMap

    list
      .map {
        _.map(l => subs.getOrElse(l, l.toString)).iterator.map(l => s"$l$interspersed").mkString
      }
      .mkString("|")
      .r
  }
}
