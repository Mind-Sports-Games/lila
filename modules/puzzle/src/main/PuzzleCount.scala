package lila.puzzle

import scala.concurrent.duration._

import strategygames.variant.Variant
import lila.db.dsl._
import lila.memo.CacheApi

final private class PuzzleCountApi(
    colls: PuzzleColls,
    cacheApi: CacheApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  def countsByTheme: Fu[Map[PuzzleTheme.Key, Int]] =
    byThemeCache get {}

  def byTheme(theme: PuzzleTheme.Key): Fu[Int] =
    countsByTheme dmap { _.getOrElse(theme, 0) }

  def countByVariant: Fu[Map[String, Map[PuzzleTheme.Key, Int]]] =
    byVariantThemeCache get {}

  def countByThemeForVariant(variant: Variant): Fu[Map[PuzzleTheme.Key, Int]] =
    countByVariant dmap { _.getOrElse(s"${variant.gameLogic.id}_${variant.id}", Map.empty) }

  def byVariantTheme(variant: Variant, theme: PuzzleTheme.Key): Fu[Int] =
    countByThemeForVariant(variant) dmap { _.getOrElse(theme, 0) }

  private val mixCounts = colls
    .puzzle {
      _.aggregateList(Int.MaxValue) { framework =>
        import framework._
        import Puzzle.BSONFields._
        Project(
          $doc(
            variant -> true,
            lib     -> true
          )
        ) -> List(
          Group(
            $doc(
              "variant" -> s"$$v",
              "lib"     -> s"$$l"
            )
          )("nb" -> SumAll)
        )
      }
    }
    .map { docs =>
      docs.flatMap { doc =>
        for {
          idDoc     <- doc.getAsOpt[Bdoc]("_id")
          variantId <- idDoc.int("variant")
          libId     <- idDoc.int("lib")
          count     <- doc.int("nb")
        } yield (s"${libId}_${variantId}", count)
      }.toMap
    }

  private val byVariantThemeCache =
    cacheApi.unit[Map[String, Map[PuzzleTheme.Key, Int]]] {
      _.refreshAfterWrite(20 minutes)
        .buildAsyncFuture { _ =>
          import Puzzle.BSONFields._
          for {
            themeDocs <- colls.puzzle {
              _.aggregateList(Int.MaxValue) { framework =>
                import framework._
                Project(
                  $doc(
                    themes  -> true,
                    variant -> true,
                    lib     -> true
                  )
                ) -> List(
                  Unwind(themes),
                  Group(
                    $doc(
                      "variant" -> s"$$v",
                      "lib"     -> s"$$l",
                      "theme"   -> s"$$themes"
                    )
                  )("nb" -> SumAll)
                )
              }
            }
            mixCountsMap <- mixCounts
          } yield {
            themeDocs
              .flatMap { doc =>
                for {
                  idDoc     <- doc.getAsOpt[Bdoc]("_id")
                  variantId <- idDoc.int("variant")
                  themeKey  <- idDoc.string("theme")
                  libId     <- idDoc.int("lib")
                  count     <- doc.int("nb")
                } yield (s"${libId}_${variantId}", PuzzleTheme.Key(themeKey) -> count)
              }
              .groupBy(_._1)
              .view
              .mapValues { pairs =>
                val themeMap = pairs.map(_._2).toMap
                val total    = mixCountsMap.getOrElse(pairs.head._1, 0)
                themeMap + (PuzzleTheme.mix.key -> total)
              }
              .toMap
          }
        }
    }

  private val byThemeCache =
    cacheApi.unit[Map[PuzzleTheme.Key, Int]] {
      _.refreshAfterWrite(20 minutes)
        .buildAsyncFuture { _ =>
          import Puzzle.BSONFields._
          colls.puzzle {
            _.aggregateList(Int.MaxValue) { framework =>
              import framework._
              Project($doc(themes -> true)) -> List(
                Unwind(themes),
                GroupField(themes)("nb" -> SumAll)
              )
            }.map {
              _.flatMap { obj =>
                for {
                  key   <- obj string "_id"
                  count <- obj int "nb"
                } yield PuzzleTheme.Key(key) -> count
              }.toMap
            }.flatMap { themed =>
              colls.puzzle(_.countAll) map { all =>
                themed + (PuzzleTheme.mix.key -> all.toInt)
              }
            }
          }
        }
    }
}
