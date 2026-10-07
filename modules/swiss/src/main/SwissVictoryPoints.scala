package lila.swiss

import strategygames.{ Player as PlayerIndex, Status }

import lila.game.Game

object SwissVictoryPoints {

  val max = 100

  val maxRounds = 12

  // bbpPairings reads points in tenths, so ten victory points is one TRF point
  val perTrfPoint = 10

  private val forWinnerByScoreDifference =
    Vector(50, 55, 59, 63, 66, 70, 73, 75, 78, 80, 82, 84, 86, 87, 89, 90, 91, 92, 93, 94, 95, 96, 96, 97, 97, 98,
      98, 99, 99, 100)

  def table: List[(String, Int)] =
    forWinnerByScoreDifference.toList.zipWithIndex.map { case (winner, difference) =>
      (if (difference == forWinnerByScoreDifference.size - 1) s"$difference+" else difference.toString, winner)
    }

  def forWinner(scoreDifference: Int): Int = forWinnerByScoreDifference.lift(scoreDifference) | max

  def forP1(game: Game): Int =
    if (game.status == Status.VariantEnd) {
      val difference = game.history.score.p1 - game.history.score.p2
      if (difference >= 0) forWinner(difference) else max - forWinner(-difference)
    } else game.winnerPlayerIndex.fold(max / 2)(_.fold(max, 0))

  def forPlayer(p1VictoryPoints: Int, playerIndex: PlayerIndex): Int =
    playerIndex.fold(p1VictoryPoints, max - p1VictoryPoints)
}
