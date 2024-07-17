package lila.game
import strategygames.{ GameFamily }
import strategygames.variant.Variant
import strategygames.format.FEN

object Handicaps {

  def startingFen(variant: Option[Variant], p1Rating: Int, p2Rating: Int): Option[FEN] = {
    variant.flatMap { v =>
      v.gameFamily match {
        case GameFamily.Go() => {
          val goHandicap = calcGoHandicap(v.toGo.boardSize.height, p1Rating, p2Rating)
          Some(
            FEN(
              v.gameLogic,
              v.toGo.fenFromSetupConfig(goHandicap.stones, goHandicap.komi).value
            )
          )
        }
        case _ => None
      }
    }
  }

  private def calcGoHandicap(size: Int, p1Rating: Int, p2Rating: Int): GoHandicap = {
    val ratingDiff = Math.abs(p1Rating - p2Rating)
    val rankDiff   = goRankDiff(Math.min(p1Rating, p2Rating), ratingDiff)
    size match {
      case 9 => // stone per 6 ranks?
        rankDiff match {
          case 0 => GoHandicap(55, 0)
          case 1 => GoHandicap(40, 0)
          case 2 => GoHandicap(25, 0)
          case 3 => GoHandicap(10, 0)
          case x if (x - 4) % 6 == 0 => GoHandicap(85, (((x - 4) / 6) + 2))
          case x if (x - 4) % 6 == 1 => GoHandicap(70, (((x - 5) / 6) + 2))
          case x if (x - 4) % 6 == 2 => GoHandicap(55, (((x - 6) / 6) + 2))
          case x if (x - 4) % 6 == 3 => GoHandicap(40, (((x - 7) / 6) + 2))
          case x if (x - 4) % 6 == 4 => GoHandicap(25, (((x - 8) / 6) + 2))
          case x if (x - 4) % 6 == 5 => GoHandicap(10, (((x - 9) / 6) + 2))
        }
      case 13 =>
        rankDiff match {
          case 0 => GoHandicap(60, 0)
          case 1 => GoHandicap(20, 0)
          case 2 => GoHandicap(80, 2)
          case 3 => GoHandicap(40, 2)
          case 4 => GoHandicap(0, 2)
          case 5 => GoHandicap(60, 3)
          case 6 => GoHandicap(20, 3)
          //repeat 2-6 each increase, stone per 2.5 ranks (from Paul)
          case x if (x - 2) % 5 == 0 => GoHandicap(80, (((x - 2) / 5) + 1) * 2)
          case x if (x - 2) % 5 == 1 => GoHandicap(40, (((x - 3) / 5) + 1) * 2)
          case x if (x - 2) % 5 == 2 => GoHandicap(0, (((x - 4) / 5) + 1) * 2)
          case x if (x - 2) % 5 == 3 => GoHandicap(60, (((x - 5) / 5) + 1) * 2 + 1)
          case x if (x - 2) % 5 == 4 => GoHandicap(20, (((x - 6) / 5) + 1) * 2 + 1)
        }
      case 19 => GoHandicap(75, rankDiff)
    }
  }

  private def goRankDiff(rating: Int, diff: Int): Int = {
    def computeRankDiff(r: Int, diffLeft: Int, rank: Int): Int = {
      if (diffLeft <= 0) return rank
      else
        r match {
          case x if x < 1000 => computeRankDiff(r + 33, diffLeft - 33, rank + 1)
          case x if x < 1700 => computeRankDiff(r + 50, diffLeft - 50, rank + 1)
          case _             => computeRankDiff(r + 100, diffLeft - 100, rank + 1)
        }
    }

    computeRankDiff(rating, diff, 0)
  }

}

case class GoHandicap(komi: Int, stones: Int) //komi is 10x to be int
