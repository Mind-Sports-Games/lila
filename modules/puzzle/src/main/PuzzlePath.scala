package lila.puzzle

import scala.concurrent.ExecutionContext

import lila.db.dsl._
import lila.user.User
import lila.common.Iso
import strategygames.variant.Variant
import strategygames.GameLogic

private object PuzzlePath {

  val sep = '|'

  case class Id(value: String) {

    val parts = value.split(sep)

    private[puzzle] def tier = PuzzleTier.from(~parts.lift(3))

    def theme = PuzzleTheme.findOrAny(~parts.lift(2)).key

    def lib       = ~parts.lift(0).flatMap(s => s.toIntOption)
    def variantId = ~parts.lift(1).flatMap(s => s.toIntOption)
    val gameLogic = GameLogic(lib)
    def variant   = Variant.orDefault(gameLogic, variantId)
  }

  implicit val pathIdIso: Iso.StringIso[Id] = lila.common.Iso.string[Id](Id.apply, _.value)
}

final private class PuzzlePathApi(
    colls: PuzzleColls
)(implicit ec: ExecutionContext) {

  import BsonHandlers._
  import PuzzlePath._

  def nextFor(
      user: User,
      variant: Variant,
      theme: PuzzleTheme.Key,
      tier: PuzzleTier,
      difficulty: PuzzleDifficulty,
      previousPaths: Set[Id],
      compromise: Int = 0
  ): Fu[Option[Id]] = {
    val actualTier =
      if (tier == PuzzleTier.Top && PuzzleDifficulty.isExtreme(difficulty)) PuzzleTier.Good
      else tier
    colls
      .path {
        _.aggregateOne() { framework =>
          import framework._
          val rating     = user.perfs.puzzle.glicko.intRating + difficulty.ratingDelta
          val ratingFlex = (100 + math.abs(1500 - rating) / 4) * compromise.atMost(4)
          Match(
            select(
              variant,
              theme,
              actualTier,
              (rating - ratingFlex) to (rating + ratingFlex)
            ) ++
              ((compromise != 5 && previousPaths.nonEmpty) ?? $doc("_id" $nin previousPaths))
          ) -> List(
            Sample(1),
            Project($id(true))
          )
        }.dmap(_.flatMap(_.getAsOpt[Id]("_id")))
      }
      .flatMap {
        case Some(path) => fuccess(path.some)
        case _ if actualTier == PuzzleTier.Top =>
          nextFor(user, variant, theme, PuzzleTier.Good, difficulty, previousPaths)
        case _ if actualTier == PuzzleTier.Good && compromise == 2 =>
          nextFor(user, variant, theme, PuzzleTier.All, difficulty, previousPaths, compromise = 1)
        case _ if compromise < 5 =>
          nextFor(user, variant, theme, actualTier, difficulty, previousPaths, compromise + 1)
        case _ => fuccess(none)
      }
  }.mon(
    _.puzzle.path.nextFor(variant.key, theme.value, tier.key, difficulty.key, previousPaths.size, compromise)
  )

  def select(variant: Variant, theme: PuzzleTheme.Key, tier: PuzzleTier, rating: Range) = $doc(
    "l" -> variant.gameLogic.id,
    "v" -> variant.id,
    "min" $lte f"${variant.gameLogic.id}${sep}${variant.id}${sep}${theme}${sep}${tier}${sep}${rating.max}%04d",
    "max" $gte f"${variant.gameLogic.id}${sep}${variant.id}${sep}${theme}${sep}${tier}${sep}${rating.min}%04d"
  )
}
