package lila.puzzle

import org.joda.time.DateTime
import org.joda.time.Days

import lila.db.dsl._
import lila.user.{ Perfs, User }
import strategygames.variant.Variant

final private class PuzzleTrustApi(colls: PuzzleColls)(implicit ec: scala.concurrent.ExecutionContext) {

  import BsonHandlers._
  import Puzzle.{ BSONFields => F }

  def vote(user: User, round: PuzzleRound, vote: Boolean): Fu[Option[Int]] = {
    val w = base(user, round) + {
      // more trust when vote != win
      if (vote == round.win) -2 else 2
    }
    // distrust provisional ratings and distant ratings
    (w > 0) ?? {
      colls.puzzle { coll =>
        coll.one[Bdoc](
          $id(round.id.puzzleId.value),
          $doc(F.variant -> true, F.lib -> true, s"${F.glicko}.r" -> true)
        ) flatMap {
          _ ?? { doc: Bdoc =>
            {
              for {
                variantId    <- doc.getAsOpt[Int](F.variant)
                libId        <- doc.getAsOpt[Int](F.lib)
                puzzleRating <- doc.getAsOpt[Double](s"${F.glicko}.r")
                variant = Variant.orDefault(strategygames.GameLogic(libId), variantId)
                userPerf = Perfs
                  .puzzleLens(variant)
                  .map(_.get(user.perfs))
                  .getOrElse(user.perfs.puzzle_standard)
                userRating = userPerf.glicko.establishedIntRating.getOrElse(1500)
              } yield (math.abs(puzzleRating - userRating) > 300) ?? -4
            }.fold(fuccess(-2))(fuccess(_))
          }
        }
      }
    }.dmap(w +)
  }.dmap(_.some.filter(0 <))

  def theme(user: User, round: PuzzleRound, theme: PuzzleTheme.Key, vote: Boolean): Fu[Option[Int]] =
    fuccess(base(user, round))
      .dmap(_.some.filter(0 <))

  private def base(user: User, round: PuzzleRound): Int = {
    seniorityBonus(user) +
      ratingBonus(user) +
      titleBonus(user) +
      patronBonus(user) +
      modBonus(user) +
      lameBonus(user)
  }.toInt

  // 0 days = 0
  // 1 month = 1
  // 1 year = 3.46
  // 2 years = 4.89
  private def seniorityBonus(user: User) =
    math.sqrt(Days.daysBetween(user.createdAt, DateTime.now).getDays.toDouble / 30) atMost 5

  private def titleBonus(user: User) = user.hasTitle ?? 20

  // 1000 = 0
  // 1500 = 0
  // 1800 = 1
  // 3000 = 5
  private def ratingBonus(user: User) = user.perfs.standard.glicko.establishedIntRating.?? { rating =>
    (rating - 1500) / 300
  } atLeast 0

  private def patronBonus(user: User) = (~user.planMonths * 5) atMost 15

  private def modBonus(user: User) =
    if (user.roles.exists(_ contains "ROLE_PUZZLE_CURATOR")) 100
    else if (user.isAdmin) 50
    else if (user.isVerified) 30
    else 0

  private def lameBonus(user: User) =
    if (user.lameOrTroll) -30 else 0
}
