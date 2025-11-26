package lila.puzzle

import org.joda.time.DateTime
import reactivemongo.api.bson.BSONNull
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

import strategygames.variant.Variant
import strategygames.GameLogic

import lila.db.dsl._
import lila.memo.CacheApi
import lila.user.User

case class PuzzleDashboard(
    global: PuzzleDashboard.Results,
    byTheme: Map[PuzzleTheme.Key, PuzzleDashboard.Results],
    byVariant: Map[Variant, PuzzleDashboard.Results],
    byVariantAndTheme: Map[(Variant, PuzzleTheme.Key), PuzzleDashboard.Results]
) {

  import PuzzleDashboard._

  def strongAndWeakThemesByVariant
      : Map[Variant, (List[(PuzzleTheme.Key, Results)], List[(PuzzleTheme.Key, Results)])] =
    byVariantAndTheme
      .groupBy { case ((variant, _), _) => variant }
      .view
      .mapValues { themeMap =>
        val themes = themeMap.map { case ((_, theme), results) => (theme, results) }.toList
        val strong = themes
          .filter { case (_, r) => r.firstWins >= 3 && r.performance > global.performance }
          .sortBy { case (_, r) => (-r.performance, -r.nb) }
          .takeRight(topThemesNb)
          .reverse
        val weak = themes
          .filter { case (_, r) => r.failed >= 3 && r.performance < global.performance }
          .sortBy { case (_, r) => (r.performance, -r.nb) }
          .take(topThemesNb)
        (weak, strong)
      }
      .toMap

  def weakThemes(variant: Variant): List[(PuzzleTheme.Key, Results)] =
    strongAndWeakThemesByVariant.get(variant).map(_._1).getOrElse(Nil)

  def strongThemes(variant: Variant): List[(PuzzleTheme.Key, Results)] =
    strongAndWeakThemesByVariant.get(variant).map(_._2).getOrElse(Nil)

  def byThemeForVariant(variant: Variant): List[(PuzzleTheme.Key, PuzzleDashboard.Results)] =
    byVariantAndTheme.collect {
      case ((v, theme), results) if v.key == variant.key => theme -> results
    }.toList

  def mostPlayed = byTheme.toList.sortBy(-_._2.nb).take(9)

  def mostPlayedThemes(variant: Variant): List[(PuzzleTheme.Key, PuzzleDashboard.Results)] =
    byThemeForVariant(variant).sortBy(-_._2.nb).take(9)

  def mostPlayedVariant = byVariant.toList.sortBy(-_._2.nb).take(9)
}

object PuzzleDashboard {

  type Days = Int

  val dayChoices = List(1, 2, 3, 7, 10, 14, 21, 30, 60, 90)

  def getclosestDay(n: Int): Option[Days] = dayChoices.minByOption(day => math.abs(day - n))

  val topThemesNb = 8

  case class Results(nb: Int, wins: Int, fixed: Int, puzzleRatingAvg: Int) {

    def firstWins = wins - fixed
    def unfixed   = nb - wins
    def failed    = fixed + unfixed

    def winPercent      = if (nb == 0) 0 else wins * 100 / nb
    def fixedPercent    = if (nb == 0) 0 else fixed * 100 / nb
    def firstWinPercent = if (nb == 0) 0 else firstWins * 100 / nb

    lazy val performance =
      if (nb == 0) puzzleRatingAvg else (puzzleRatingAvg - 500 + math.round(1000 * (firstWins.toFloat / nb)))

    def clear   = nb >= 6 && firstWins >= 2 && failed >= 2
    def unclear = !clear

    def canReplay = unfixed > 0
  }

  val irrelevantThemes = List(
    PuzzleTheme.oneMove,
    PuzzleTheme.short,
    PuzzleTheme.long,
    PuzzleTheme.veryLong,
    PuzzleTheme.mateIn1,
    PuzzleTheme.mateIn2,
    PuzzleTheme.mateIn3,
    PuzzleTheme.mateIn4,
    PuzzleTheme.mateIn5,
    PuzzleTheme.equality,
    PuzzleTheme.advantage,
    PuzzleTheme.crushing,
    PuzzleTheme.master,
    PuzzleTheme.masterVsMaster
  ).map(_.key)

  val relevantThemes = PuzzleTheme.all collect {
    case t if !irrelevantThemes.contains(t.key) => t.key
  }
}

final class PuzzleDashboardApi(
    colls: PuzzleColls,
    cacheApi: CacheApi
)(implicit ec: ExecutionContext) {

  import PuzzleDashboard._

  def apply(u: User, days: Days): Fu[Option[PuzzleDashboard]] = cache.get(u.id -> days)

  private val cache =
    cacheApi[(User.ID, Days), Option[PuzzleDashboard]](1024, "puzzle.dashboard") {
      _.expireAfterWrite(10 seconds).buildAsyncFuture { case (userId, days) =>
        compute(userId, days)
      }
    }

  //TODO maybe remove bytheme query and data as no longer required?
  private def compute(userId: User.ID, days: Days): Fu[Option[PuzzleDashboard]] =
    colls.round {
      _.aggregateOne() { framework =>
        import framework._
        val resultsGroup = List(
          "nb"     -> SumAll,
          "wins"   -> Sum(countField("w")),
          "fixes"  -> Sum(countField("f")),
          "rating" -> AvgField("puzzle.rating")
        )
        Match($doc("u" -> userId, "d" $gt DateTime.now.minusDays(days))) -> List(
          Sort(Descending("d")),
          Limit(10_000),
          PipelineOperator(
            PuzzleRound.puzzleLookup(
              colls,
              List(
                $doc("$project" -> $doc("themes" -> true, "rating" -> "$glicko.r", "v" -> true, "l" -> true))
              )
            )
          ),
          Unwind("puzzle"),
          Facet(
            List(
              "global" -> List(Group(BSONNull)(resultsGroup: _*)),
              "byTheme" -> List(
                Unwind("puzzle.themes"),
                Match(relevantThemesSelect),
                GroupField("puzzle.themes")(resultsGroup: _*)
              ),
              "byVariant" -> List(
                Group(
                  $doc(
                    "variant" -> "$puzzle.v",
                    "lib"     -> "$puzzle.l"
                  )
                )(resultsGroup: _*)
              ),
              "byVariantAndTheme" -> List(
                Unwind("puzzle.themes"),
                //Match(relevantThemesSelect), //With few puzzles per variant, we keep all themes
                Group(
                  $doc(
                    "variant" -> "$puzzle.v",
                    "lib"     -> "$puzzle.l",
                    "theme"   -> "$puzzle.themes"
                  )
                )(resultsGroup: _*)
              )
            )
          )
        )
      }
        .map { r =>
          for {
            result     <- r
            globalDocs <- result.getAsOpt[List[Bdoc]]("global")
            globalDoc  <- globalDocs.headOption
            global     <- readResults(globalDoc)
            themeDocs  <- result.getAsOpt[List[Bdoc]]("byTheme")
            byTheme = for {
              doc      <- themeDocs
              themeStr <- doc.string("_id")
              theme    <- PuzzleTheme find themeStr
              results  <- readResults(doc)
            } yield theme.key -> results
            variantDocs <- result.getAsOpt[List[Bdoc]]("byVariant")
            byVariant = for {
              doc       <- variantDocs
              idDoc     <- doc.getAsOpt[Bdoc]("_id")
              variantId <- idDoc.int("variant")
              lib       <- idDoc.int("lib")
              variant = Variant.orDefault(GameLogic(lib), variantId)
              results <- readResults(doc)
            } yield variant -> results
            variantAndThemeDocs <- result.getAsOpt[List[Bdoc]]("byVariantAndTheme")
            byVariantAndTheme = for {
              doc       <- variantAndThemeDocs
              idDoc     <- doc.getAsOpt[Bdoc]("_id")
              variantId <- idDoc.int("variant")
              lib       <- idDoc.int("lib")
              themeStr  <- idDoc.string("theme")
              variant = Variant.orDefault(GameLogic(lib), variantId)
              theme   <- PuzzleTheme find themeStr
              results <- readResults(doc)
            } yield (variant, theme.key) -> results
          } yield PuzzleDashboard(
            global = global,
            byTheme = byTheme.toMap,
            byVariant = byVariant.toMap,
            byVariantAndTheme = byVariantAndTheme.toMap
          )
        }
        .dmap(_.filter(_.global.nb > 0))
    }

  private def countField(field: String) = $doc("$cond" -> $arr("$" + field, 1, 0))

  private def readResults(doc: Bdoc) = for {
    nb     <- doc.int("nb")
    wins   <- doc.int("wins")
    fixes  <- doc.int("fixes")
    rating <- doc.double("rating")
  } yield Results(nb, wins, fixes, rating.toInt)

  val relevantThemesSelect = $doc(
    "puzzle.themes" $nin irrelevantThemes.map(_.value)
  )
}
