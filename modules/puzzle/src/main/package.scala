package lila

import lila.rating.Glicko

package object puzzle extends PackageObject {

  private[puzzle] def logger = lila.log("puzzle")
}

package puzzle {

  // no path matched the player's rating for this variant and theme — puzzles for it may not be
  // generated yet. A selection problem, not a failure: callers show a page rather than a 500.
  case class NoPuzzleAvailable(variantKey: String, theme: String)
      extends Exception(s"No puzzle available for variant $variantKey, theme $theme")

  case class Result(win: Boolean) extends AnyVal {
    def loss   = !win
    def glicko = if (win) Glicko.Result.Win else Glicko.Result.Loss
  }
}
