package lila.puzzle

import org.joda.time.DateTime
import reactivemongo.api.bson.BSONNull
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.util.chaining._

import strategygames.variant.Variant

import lila.db.dsl._
import lila.memo.CacheApi
import lila.user.User

case class PuzzleReplay(
    days: PuzzleDashboard.Days,
    theme: PuzzleTheme.Key,
    nb: Int,
    remaining: Vector[Puzzle.Id]
) {

  def i = nb - remaining.size

  def step = copy(remaining = remaining drop 1)
}

final class PuzzleReplayApi(
    colls: PuzzleColls,
    cacheApi: CacheApi
)(implicit ec: ExecutionContext) {

  import BsonHandlers._

  private val maxPuzzles = 100

  private val replays = cacheApi.notLoading[(User.ID, Variant), PuzzleReplay](512, "puzzle.replay")(
    _.expireAfterWrite(1 hour).buildAsync()
  )

  def apply(
      user: User,
      maybeDays: Option[PuzzleDashboard.Days],
      variant: Variant,
      theme: PuzzleTheme.Key
  ): Fu[Option[(Puzzle, PuzzleReplay)]] =
    maybeDays map { days =>
      replays.getFuture((user.id, variant), _ => createReplayFor(user, days, variant, theme)) flatMap {
        current =>
          if (current.days == days && current.theme == theme && current.remaining.nonEmpty) fuccess(current)
          else createReplayFor(user, days, variant, theme) tap { replays.put((user.id, variant), _) }
      } flatMap { replay =>
        replay.remaining.headOption ?? { id =>
          colls.puzzle(_.byId[Puzzle](id.value)) map2 (_ -> replay)
        }
      }
    } getOrElse fuccess(None)

  def onComplete(
      round: PuzzleRound,
      days: PuzzleDashboard.Days,
      variant: Variant,
      theme: PuzzleTheme.Key
  ): Funit =
    replays.getIfPresent((round.userId, variant)) ?? {
      _ map { replay =>
        if (replay.days == days && replay.theme == theme)
          replays.put((round.userId, variant), fuccess(replay.step))
      }
    }

  private def createReplayFor(
      user: User,
      days: PuzzleDashboard.Days,
      variant: Variant,
      theme: PuzzleTheme.Key
  ): Fu[PuzzleReplay] =
    colls
      .round {
        _.aggregateOne() { framework =>
          import framework._
          Match(
            $doc(
              "u" -> user.id,
              "d" $gt DateTime.now.minusDays(days),
              "w" $ne true
            )
          ) -> List(
            Sort(Ascending("d")),
            PipelineOperator(
              $doc(
                "$lookup" -> $doc(
                  "from" -> colls.puzzle.name.value,
                  "as"   -> "puzzle",
                  "let" -> $doc(
                    "pid" -> $doc("$arrayElemAt" -> $arr($doc("$split" -> $arr("$_id", ":")), 1))
                  ),
                  "pipeline" -> $arr(
                    $doc(
                      "$match" -> $doc(
                        "$expr" -> {
                          val baseMatch = $doc("$eq" -> $arr("$_id", "$$pid"))
                          if (theme == PuzzleTheme.mix.key)
                            $doc(
                              "$and" -> $arr(
                                baseMatch,
                                $doc("$eq" -> $arr("$l", variant.gameLogic.id)),
                                $doc("$eq" -> $arr("$v", variant.id))
                              )
                            )
                          else
                            $doc(
                              "$and" -> $arr(
                                baseMatch,
                                $doc("$in" -> $arr(theme, "$themes")),
                                $doc("$eq" -> $arr("$l", variant.gameLogic.id)),
                                $doc("$eq" -> $arr("$v", variant.id))
                              )
                            )
                        }
                      )
                    ),
                    $doc("$limit"   -> maxPuzzles),
                    $doc("$project" -> $doc("_id" -> true))
                  )
                )
              )
            ),
            Unwind("puzzle"),
            Group(BSONNull)("ids" -> PushField("puzzle._id"))
          )
        }
      }
      .map {
        ~_.flatMap(_.getAsOpt[Vector[Puzzle.Id]]("ids"))
      } map { ids =>
      PuzzleReplay(days, theme, ids.size, ids)
    }

  private val puzzleLookup =
    $doc(
      "$lookup" -> $doc(
        "from" -> colls.puzzle.name.value,
        "as"   -> "puzzle",
        "let" -> $doc(
          "pid" -> $doc("$arrayElemAt" -> $arr($doc("$split" -> $arr("$_id", ":")), 1))
        ),
        "pipeline" -> $arr(
          $doc(
            "$match" -> $doc(
              "$expr" -> $doc(
                $doc("$eq" -> $arr("$_id", "$$pid"))
              )
            )
          ),
          $doc("$project" -> $doc("_id" -> true))
        )
      )
    )
}
