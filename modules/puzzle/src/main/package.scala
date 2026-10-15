package lila

import lila.rating.Glicko

package object puzzle extends PackageObject {

  private[puzzle] def logger = lila.log("puzzle")
}

package puzzle {

  // no path for the player's rating in this variant and theme: callers show a page, not a 500
  case class NoPuzzleAvailable(variantKey: String, theme: String)
      extends Exception(s"No puzzle available for variant $variantKey, theme $theme")

  case class Result(win: Boolean) extends AnyVal {
    def loss   = !win
    def glicko = if (win) Glicko.Result.Win else Glicko.Result.Loss
  }
}
