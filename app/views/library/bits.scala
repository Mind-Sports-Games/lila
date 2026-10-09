package views.html.library

import lila.i18n.{ I18nKeys as trans, VariantKeys }
import lila.app.ui.ScalatagsTemplate.*
import play.api.i18n.Lang
import play.api.mvc.Call
import strategygames.variant.Variant
import strategygames.GameLogic
import org.joda.time.DateTime
import org.joda.time.Days
import org.joda.time.format.DateTimeFormat
import lila.game.{ MonthlyGameData, WinRatePercentages }

object bits {
  def transformData(data: List[MonthlyGameData]): List[(String, String, Long)] =
    data.map { case MonthlyGameData(yearMonth, libVar, count) => (yearMonth, getVariantKey(libVar), count) }

  private def variantKey(variant: Variant)   = s"${variant.gameFamily.id}_${variant.id}"
  private def getVariantKey(lib_var: String) =
    lib_var.split('_') match {
      case Array(lib, id) => {
        val variant = Variant.orDefault(GameLogic(lib.toInt), id.toInt)
        variantKey(variant)
      }
      case _ => "0_1" // standard chess
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
      .getOrElse(DateTime.now.toString(dateFormat))

  def studyLink(variant: Variant): Option[String] = {
    variant.key match {
      case "abalone"       => Some("AbaloneS")
      case "linesOfAction" => Some("LinesOfA")
      case "dameo"         => Some("DameoTut")
      case _               => None
    }
  }

  val msoTeamId = "mind-sports-olympiad"

  val msoGrandPrixName = "MSO (GP|Grand Prix|2021)"
  case class MsoEvent(
      // the game's page in the results database, which lists every edition
      gameCode: String,
      eventCode: Option[String],
      worldChampionship: Boolean,
      studyId: Option[String],
      // the same game's other events at the MSO: (code, name as the results database titles it)
      others: List[(String, String)] = Nil
  ) {
    def resultsUrl = eventCode.map(resultsUrlOf)
    def allEditionsUrl = s"https://msodb.playstrategy.org/Report/GameMedals?gameCode=$gameCode"
  }

  // the edition the results links point at: msodb is only advisory while an MSO is running, and a link
  // with its year is stable once that MSO is over. Bump it when the next MSO has finished.
  val msoResultsYear = 2026

  def resultsUrlOf(code: String) =
    s"https://msodb.playstrategy.org/Report/EventResults?year=$msoResultsYear&eventCode=$code"

  private def mso(
      gameCode: String,
      code: String,
      worldChampionship: Boolean = false,
      study: Option[String] = None,
      others: List[(String, String)] = Nil
  ) = Some(MsoEvent(gameCode, Some(code), worldChampionship, study, others))

  // games the MSO has not run an online tournament for yet: their page links no online tournaments
  private val noMsoOnline = Set("entropy")

  def hasMsoOnline(variant: Variant): Boolean = !noMsoOnline(variant.key)

  def msoEvent(variant: Variant): Option[MsoEvent] =
    variant.key match {
      case "abalone"            => mso("AB", "ABOC", worldChampionship = true, Some("JG7Zf7mE"))
      case "linesOfAction"      => mso("LO", "LOWC", worldChampionship = true, Some("IyudHHhm"))
      case "amazons"            => mso("AMZ", "AMZOC", study = Some("m8N1ERm6"))
      case "entropy"            => mso("EN", "ENWC", worldChampionship = true, Some("GqB7F4xh"))
      case "breakthroughtroyka" => mso("BT", "BTOC", study = Some("EkVLAnIi"))
      case "international"      => mso("DR", "DRDA", study = Some("CTu6f0Mp"))
      case "oware"              => mso("OW", "OWOC", study = Some("C7hHSwaw"))
      case "flipello"           => mso("OT", "OTOC", study = Some("PuMKTeH2"))
      // played at the MSO but not recorded yet; their games are not in a study yet
      case "togyzkumalak" => mso("TO", "TOOC")
      case "shogi"        => mso("SH", "SHOC")
      case "xiangqi"      => mso("CC", "CCOC")
      case "backgammon"   =>
        mso(
          "BA",
          "BAWIC",
          worldChampionship = true,
          others = List(
            "BAWCI" -> "World Individual Championship (Intermediates)",
            "BAOC"  -> "6x7pt",
            "BAMB"  -> "6x3pt",
            "BAWO"  -> "7x1pt"
          )
        )
      case "hyper"      => mso("BA", "BAHY")
      case "nackgammon" => mso("BA", "BANG")
      case _            => None
    }

  // an edition of the MSO Grand Prix, arena or swiss, for the library page
  case class GrandPrixEdition(name: String, url: Call, startsAt: DateTime)

  def grandPrixEditions(arenas: List[lila.tournament.Tournament], swisses: List[lila.swiss.Swiss])(implicit
      lang: Lang
  ): List[GrandPrixEdition] =
    (arenas.map(t => GrandPrixEdition(t.name(full = false), routes.Tournament.show(t.id), t.startsAt)) :::
      swisses.map(s => GrandPrixEdition(s.name, routes.Swiss.show(s.id.value), s.startsAt)))
      .sortBy(-_.startsAt.getMillis)

  // derived variants name the game they come from, so search engines know which page is "Xiangqi"
  def parentVariant(variant: Variant): Option[Variant] =
    Variant.byKey.get(variant.key match {
      case "minixiangqi"                                     => "xiangqi"
      case "minishogi"                                       => "shogi"
      case "go9x9" | "go13x13"                               => "go19x19"
      case "flipello10" | "octagonflipello" | "antiflipello" => "flipello"
      case "hyper" | "nackgammon"                            => "backgammon"
      case "grandabalone"                                    => "abalone"
      case "minibreakthroughtroyka"                          => "breakthroughtroyka"
      case "bestemshe"                                       => "togyzkumalak"
      case "frysk"                                           => "frisian"
      case "scrambledEggs"                                   => "linesOfAction"
      case "antichess" | "atomic" | "chess960" | "crazyhouse" | "fiveCheck" | "horde" | "kingOfTheHill" |
          "monster" | "noCastling" | "racingKings" | "threeCheck" =>
        "standard"
      case "antidraughts" | "breakthrough" | "frisian" => "international"
      case _                                                 => ""
    })

  // the smaller-board or reduced variants of a game, so its hub links them and not only the other way round
  def childVariants(variant: Variant): List[Variant] =
    Variant.all.filter(v => parentVariant(v).exists(_.key == variant.key))

  // Games a player of one is likely to like, as sets: each page lists the others of its sets. Unlike a
  // parent, nothing derives from anything here, so the rules pages do not call these "variants of".
  private val relatedSets: List[List[String]] = List(
    List("international", "english", "brazilian", "portuguese"),
    List("russian", "pool"),
    List("dameo", "frisian"),
    List("antidraughts", "breakthrough"),
    List("fiveCheck", "threeCheck"),
    List("oware", "togyzkumalak")
  )

  def relatedVariants(variant: Variant): List[Variant] =
    relatedSets
      .filter(_.contains(variant.key))
      .flatten
      .distinct
      .filterNot(_ == variant.key)
      .flatMap(Variant.byKey.get)

  // A game's URL carries its engine key - flipello, standard, go19x19 - so the name people
  // actually type 404s. These redirect onto the one canonical URL instead.
  private val urlAliases: Map[String, String] = Map(
    "othello"               -> "flipello",
    "reversi"               -> "flipello",
    "grandothello"          -> "flipello10",
    "grandreversi"          -> "flipello10",
    "antiothello"           -> "antiflipello",
    "antireversi"           -> "antiflipello",
    "octagonothello"        -> "octagonflipello",
    "octagonreversi"        -> "octagonflipello",
    "chess"                 -> "standard",
    "go"                    -> "go19x19",
    "baduk"                 -> "go19x19",
    "weiqi"                 -> "go19x19",
    "chinesechess"          -> "xiangqi",
    "japanesechess"         -> "shogi",
    "loa"                   -> "linesOfAction",
    "mancala"               -> "oware",
    "awari"                 -> "oware",
    "awale"                 -> "oware",
    "ayo"                   -> "oware",
    "toguzkumalak"          -> "togyzkumalak",
    "togyzqumalaq"          -> "togyzkumalak",
    // "checkers" is the 8x8 game; "draughts" is what the lobby's own pool pairs you into
    "checkers"              -> "english",
    "draughts"              -> "international",
    "americancheckers"      -> "english",
    "americandraughts"      -> "english",
    "englishcheckers"       -> "english",
    "englishdraughts"       -> "english",
    "internationaldraughts" -> "international",
    "internationalcheckers" -> "international",
    "polishdraughts"        -> "international",
    "russiancheckers"       -> "russian",
    "russiandraughts"       -> "russian",
    "braziliancheckers"     -> "brazilian",
    "braziliandraughts"     -> "brazilian",
    // named Spanish, keyed portuguese: both names have to resolve
    "spanish"               -> "portuguese",
    "spanishdraughts"       -> "portuguese",
    "spanishcheckers"       -> "portuguese",
    "portuguesedraughts"    -> "portuguese",
    "portuguesecheckers"    -> "portuguese",
    "poolcheckers"          -> "pool",
    "frisiandraughts"       -> "frisian"
  )

  private lazy val keysByLowerCase: Map[String, String] =
    Variant.all.map(v => v.key.toLowerCase -> v.key).toMap

  // The canonical key for something that is not one: a real key in the wrong case
  // (linesofaction), or a name the game is better known by (othello). None when the key is
  // already canonical, so a redirect can never loop.
  def canonicalVariantKey(key: String): Option[String] = {
    val lower = key.toLowerCase
    keysByLowerCase.get(lower).filter(_ != key) orElse urlAliases.get(lower)
  }

  // variants named by a bare adjective ("Russian", "Atomic") are searched with their family word
  private val adjectiveNames = Set(
    "crazyhouse",
    "kingOfTheHill",
    "threeCheck",
    "fiveCheck",
    "atomic",
    "horde",
    "racingKings",
    "noCastling",
    "monster",
    "international",
    "frisian",
    "frysk",
    "breakthrough",
    "russian",
    "brazilian",
    "pool",
    "portuguese",
    "english"
  )

  // "Russian Draughts", "Atomic Chess", "Othello"
  def searchName(variant: Variant)(implicit lang: Lang) =
    if (adjectiveNames(variant.key))
      s"${VariantKeys.variantName(variant)} ${VariantKeys.gameFamilyName(variant.gameFamily)}"
    else VariantKeys.variantName(variant)

  // "Shogi (Japanese chess)", "Abalone (board game)", "Backgammon"
  def nameWithAlias(variant: Variant)(implicit lang: Lang) =
    VariantKeys.variantAlias(variant).fold(searchName(variant)) { alias =>
      s"${searchName(variant)} ($alias)"
    }

  // "Play Shogi online free — Japanese chess"
  def pageTitle(variant: Variant)(implicit lang: Lang) =
    VariantKeys.variantAlias(variant).foldLeft(
      trans.playVariantOnlineFreeTitle.txt(searchName(variant))
    )(_ + " — " + _)

  // the variant's own objective, punctuated: "Capture more discs than your opponent."
  def objectiveSentence(variant: Variant)(implicit lang: Lang) = {
    val objective = VariantKeys.variantTitle(variant)
    // ja and zh end a sentence with their own stop; appending an ASCII one reads as a typo
    val stop = if (objective.lastOption.exists(".。．！？!?".contains)) "" else "."
    s"$objective$stop"
  }

  def pageDescription(variant: Variant)(implicit lang: Lang) =
    s"${trans.playVariantOnlineFreeDescription.txt(nameWithAlias(variant))} ${objectiveSentence(variant)}"

  // the two pseudo variants are playable but have neither a library hub nor a rules page
  def hasLibraryPages(variant: Variant) =
    !Set("fromPosition", "draughtsFromPosition")(variant.key)

  // "Shogi rules — how to play Shogi (Japanese chess)"
  def rulesTitle(variant: Variant)(implicit lang: Lang) =
    trans.variantRulesTitle.txt(searchName(variant), nameWithAlias(variant))

  def rulesDescription(variant: Variant)(implicit lang: Lang) =
    trans.variantRulesDescription.txt(nameWithAlias(variant), searchName(variant))

  // no browser engine: this is noClientEvalVariants in ui/ceval/src/util.ts, to keep in step with it
  private val noBrowserEngine = Set(
    "monster", "linesOfAction", "scrambledEggs", "dameo", "amazons", "minibreakthroughtroyka",
    "antiflipello", "octagonflipello", "oware", "togyzkumalak", "bestemshe", "go9x9", "go13x13", "go19x19",
    "backgammon", "hyper", "nackgammon", "abalone", "grandabalone", "entropy"
  )

  def editorDescription(variant: Variant)(implicit lang: Lang) =
    trans.variantEditorDescription.txt(nameWithAlias(variant), searchName(variant))

  // the engine behind the analysis board, when it has one (the same Fairy-Stockfish build serves both)
  def analysisEngine(variant: Variant): Option[String] =
    if (!variant.hasFishnet || noBrowserEngine(variant.key)) None
    else if (variant.gameLogic == GameLogic.Chess()) Some("Stockfish")
    else Some("Fairy-Stockfish")

  // an engine that only analyses finished games, off the analysis board
  def serverEngine(variant: Variant): Option[String] =
    if (variant.hasFishnet && variant.gameLogic == GameLogic.Backgammon()) Some("GNU Backgammon (gnubg)")
    else None

  // "Atomic Chess analysis board — free engine & solver"; no engine claim for the games without one
  def analysisTitle(variant: Variant)(implicit lang: Lang) =
    (if (analysisEngine(variant).isDefined) trans.variantAnalysisTitle
     else trans.variantAnalysisTitleNoEngine)
      .txt(searchName(variant))

  def analysisDescription(variant: Variant)(implicit lang: Lang) =
    analysisEngine(variant) match {
      case Some(engine) =>
        trans.variantAnalysisDescription.txt(nameWithAlias(variant), searchName(variant), engine)
      case None =>
        serverEngine(variant).fold(
          trans.variantAnalysisDescriptionNoEngine.txt(nameWithAlias(variant), searchName(variant))
        ) { engine =>
          trans.variantAnalysisDescriptionServerEngine
            .txt(nameWithAlias(variant), searchName(variant), engine)
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
