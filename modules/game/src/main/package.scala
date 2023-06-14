package lila

package object game extends PackageObject {

  type PgnMoves    = Vector[String]
  type RatingDiffs = strategygames.Player.Map[Int]

  private[game] def logger = lila.log("game")
}
