package lila.puzzle

sealed abstract class PuzzleTier(val key: String) {

  def stepDown = PuzzleTier.stepDown(this)

  override def toString = key
}

object PuzzleTier {

  case object Top  extends PuzzleTier("top")
  case object Good extends PuzzleTier("good")
  case object All  extends PuzzleTier("all")

  def stepDown(tier: PuzzleTier): Option[PuzzleTier] =
    if tier == Top then Good.some
    else if tier == Good then All.some
    else none

  def from(tier: String) =
    if tier == Top.key then Top
    else if tier == Good.key then Good
    else All
}
