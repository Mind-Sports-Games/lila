package lila.game

import play.api.i18n.Lang
import strategygames.variant.Variant
import strategygames.{ GameLogic, Player as PlayerIndex }

import lila.i18n.I18nKeys as trans

// What to call a player in a sentence about them: who won, who resigned, whose turn it is. Kept apart
// from strategygames' variant.playerNames, which those sentences used to read directly, because that
// map is also the playerIndex's wire name — it appears in round socket paths and as the playerName a
// client labels its board with — and so has to stay a single word.
// When adding a name here, also edit ui/@types/playstrategy/index.d.ts:declare type PlayerName.
object PlayerName {

  def apply(variant: Variant, p: PlayerIndex): String =
    if (variant.gameLogic == GameLogic.Entropy())
      p.fold("Second player as Order", "First player as Order")
    else variant.playerNames(p)

  def translated(variant: Variant, p: PlayerIndex)(implicit lang: Lang): String =
    apply(variant, p) match {
      case "White" => trans.white.txt()
      case "Black" => trans.black.txt()
      // Xiangqi add back in when adding red as a colour for Xiangqi
      // case "Red"   => trans.red.txt()
      case "Sente"   => trans.sente.txt()
      case "Gote"    => trans.gote.txt()
      case s: String => s
    }
}
