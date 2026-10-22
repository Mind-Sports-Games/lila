package lila.game

import strategygames.variant.Variant

// The games the analysis board can run an engine for in the browser. The frontend reads it from each
// variant's json (clientEval), and the library pages from here, so there is one list.
object BrowserEngine {

  private val without: Set[String] = Set(
    "monster",
    "linesOfAction",
    "scrambledEggs",
    "international",
    "antidraughts",
    "breakthrough",
    "russian",
    "brazilian",
    "pool",
    "portuguese",
    "english",
    "fromPositionDraughts",
    "frisian",
    "frysk",
    "dameo",
    "amazons",
    // Fairy-Stockfish starts this board from a5, so its evaluations are off
    "minibreakthroughtroyka",
    "antiflipello",
    "octagonflipello",
    "oware",
    "togyzkumalak",
    "bestemshe",
    "go9x9",
    "go13x13",
    "go19x19",
    "backgammon",
    "hyper",
    "nackgammon",
    "abalone",
    "grandabalone",
    "entropy",
    "gomoku",
    "pente",
    "renju"
  )

  def has(variant: Variant): Boolean = !without(variant.key)
}
