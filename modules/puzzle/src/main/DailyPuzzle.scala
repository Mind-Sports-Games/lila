package lila.puzzle

import akka.pattern.ask
import org.joda.time.DateTime
import Puzzle.BSONFields as F

import lila.db.dsl.*
import lila.memo.CacheApi.*

final private[puzzle] class DailyPuzzle(
    colls: PuzzleColls,
    pathApi: PuzzlePathApi,
    renderer: lila.hub.actors.Renderer,
    cacheApi: lila.memo.CacheApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  import BsonHandlers.*

  private val cache =
    cacheApi.unit[Option[DailyPuzzle.WithHtml]] {
      _.refreshAfterWrite(1 minutes)
        .buildAsyncFuture(_ => find)
    }

  def get: Fu[Option[DailyPuzzle.WithHtml]] = cache.getUnit

  private val variantCache =
    cacheApi[String, Option[DailyPuzzle.WithHtml]](32, "puzzle.daily.variant") {
      _.refreshAfterWrite(5 minutes)
        .buildAsyncFuture(findLastForVariantKey)
    }

  def getForVariant(variant: strategygames.variant.Variant): Fu[Option[DailyPuzzle.WithHtml]] =
    variantCache.get(variant.key)

  private def findLastForVariantKey(key: String): Fu[Option[DailyPuzzle.WithHtml]] =
    strategygames.variant.Variant.all.find(_.key == key).so { variant =>
        colls.puzzle {
          _.find($doc(F.day `$exists` true, F.lib -> variant.gameLogic.id, F.variant -> variant.id))
            .sort($doc(F.day -> -1))
            .one[Puzzle]
        } flatMap { _ so makeDaily }
    }

  private def find: Fu[Option[DailyPuzzle.WithHtml]] =
    (findCurrent.orElse(findNew)) recover { case e: Exception =>
      logger.error("find daily", e)
      none
    } flatMap { _ so makeDaily }

  private def makeDaily(puzzle: Puzzle): Fu[Option[DailyPuzzle.WithHtml]] = {
    given akka.util.Timeout = makeTimeout.short
    (renderer.actor ? DailyPuzzle.Render(puzzle, puzzle.fenAfterInitialMove, puzzle.line.head.uci))
      .mapTo[String] map { html =>
      DailyPuzzle.WithHtml(puzzle, html).some
    }
  } recover { case e: Exception =>
    logger.warn("make daily", e)
    none
  }

  private def findCurrent =
    colls.puzzle {
      _.find($doc(F.day `$gt` DateTime.now.minusDays(1)))
        .one[Puzzle]
    }

  private def findNew: Fu[Option[Puzzle]] =
    colls
      .path {
        _.aggregateWith[Bdoc]() { framework =>
          import framework.*
          List(
            Match(
              pathApi.select(Puzzle.randomVariant, PuzzleTheme.short.key, PuzzleTier.Top, 100 to 1900)
            ),
            Sample(3),
            Project($doc("ids" -> true, "_id" -> false)),
            UnwindField("ids"),
            PipelineOperator(
              $doc(
                "$lookup" -> $doc(
                  "from"     -> colls.puzzle.name.value,
                  "as"       -> "puzzle",
                  "let"      -> $doc("id" -> "$ids"),
                  "pipeline" -> $arr(
                    $doc(
                      "$match" -> $doc(
                        "$expr" -> $doc(
                          $doc("$eq" -> $arr("$_id", "$$id"))
                        )
                      )
                    ),
                    $doc(
                      "$match" -> $doc(
                        Puzzle.BSONFields.day `$exists` false
                      )
                    )
                  )
                )
              )
            ),
            UnwindField("puzzle"),
            ReplaceRootField("puzzle"),
            AddFields($doc("dayScore" -> $doc("$multiply" -> $arr("$plays", "$vote")))),
            Sort(Descending("dayScore")),
            Limit(1)
          )
        }
          .collect[List](maxDocs = 1)
          .dmap(_.headOption)
      }
      .flatMap { docOpt =>
        docOpt.flatMap(PuzzleBSONReader.readOpt) so { puzzle =>
          colls.puzzle {
            _.update.one($id(puzzle.id), $set(F.day -> DateTime.now))
          } inject puzzle.some
        }
      }
}

object DailyPuzzle {
  type Try = () => Fu[Option[DailyPuzzle.WithHtml]]

  case class WithHtml(puzzle: Puzzle, html: String)

  case class Render(puzzle: Puzzle, fen: strategygames.format.FEN, lastMove: String)
}
