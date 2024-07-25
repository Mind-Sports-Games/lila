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
      case 19 =>
        rankDiff match {
          case 0 => GoHandicap(75, 0)
          case _ => GoHandicap(5, rankDiff)
        }
    }
  }

  private def goRankDiff(rating: Int, diff: Int): Int = {
    def computeRankDiff(r: Int, diffLeft: Int, rank: Int): Int = {
      val ratingIncrease = if (r < 1000) 33 else if (r < 1700) 50 else 100
      if (diffLeft <= ratingIncrease) return rank
      else computeRankDiff(r + ratingIncrease, diffLeft - ratingIncrease, rank + 1)
    }

    computeRankDiff(rating, diff, 0)
  }

  def convertGoRating(goRating: String): Int =
    goRating match {
      case x if x.matches("""\d+k""") =>
        x.dropRight(1).toInt match {
          case y if y > 18 => Math.max(1593 - y * 33, 600) //lowest rating is 600 on site
          case y if y > 4  => 1900 - y * 50
          case y if y > 0  => 2100 - y * 100
          case _           => 1500                         // our default but shouldn't get here
        }
      case x if x.matches("""\dd""") => Math.min(Math.max(x.dropRight(1).toInt * 100 + 2000, 2100), 2700)
    }

}

case class GoHandicap(komi: Int, stones: Int) //komi is 10x to be int
