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

  def startingFenMcMahon(variant: Option[Variant], scoreDiff: Int): Option[FEN] = {
    variant.flatMap { v =>
      v.gameFamily match {
        case GameFamily.Go() => {
          val goHandicap = calcGoMcMahonHandicap(v.toGo.boardSize.height, scoreDiff)
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

  def playerInputRatings(inputPlayerRatingsInput: String): Map[String, Int] =
    inputPlayerRatingsInput.linesIterator.flatMap {
      _.trim.toLowerCase.split(' ').map(_.trim) match {
        case Array(u, r) =>
          r match {
            case psRating(grade) if grade.toInt >= 600 && grade.toInt <= 2900 =>
              Map(u -> r.toInt)
            case goKyuRating(grade) if grade.toInt > 0 && grade.toInt <= 60 =>
              Map(u -> convertGoRating(grade.toInt, KyuRating))
            case goDanRating(grade) if grade.toInt > 0 && grade.toInt <= 7 =>
              Map(u -> convertGoRating(grade.toInt, DanRating))
            case _ => None
          }
        case _ => None
      }
    }.toMap

  def playerRatingFromInput(inputRating: String): Option[Int] = {
    inputRating match {
      case psRating(grade) if grade.toInt >= 600 && grade.toInt <= 2900 =>
        Some(inputRating.toInt)
      case goKyuRating(grade) if grade.toInt > 0 && grade.toInt <= 60 =>
        Some(convertGoRating(grade.toInt, KyuRating))
      case goDanRating(grade) if grade.toInt > 0 && grade.toInt <= 7 =>
        Some(convertGoRating(grade.toInt, DanRating))
      case _ => None
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
      if (!(diffLeft >= ratingIncrease)) return rank
      else computeRankDiff(r + ratingIncrease, diffLeft - ratingIncrease, rank + 1)
    }

    computeRankDiff(rating, diff, 0)
  }

  private def convertGoRating(goRating: Int, ratingType: GoRatingType): Int =
    ratingType match {
      case KyuRating =>
        goRating match {
          case y if y > 18 => Math.max(1593 - y * 33, 600) //lowest rating is 600 on site
          case y if y > 4  => 1900 - y * 50
          case y if y > 0  => 2100 - y * 100
          case _           => 1500
        }
      case DanRating => Math.min(Math.max(goRating * 100 + 2000, 2100), 2700)
    }

  val goKyuRating = s"^([0-9]+)k$$".r
  val goDanRating = s"^([1-7]+)d$$".r
  val psRating    = s"^([0-9]+)$$".r

  def goRatingDisplay(rating: Int): String = {
    if (rating >= 2100) {
      (mcMahonScoreFromRating(rating).toInt + 1).toString() + "d"
    } else {
      (mcMahonScoreFromRating(rating).toInt * -1).toString() + "k"
    }
  }

  def mcMahonScoreFromRating(rating: Int): Double = {
    rating match {
      case x if x >= 2800 => 7.0
      case x if x >= 2700 => 6.0
      case x if x >= 2600 => 5.0
      case x if x >= 2500 => 4.0
      case x if x >= 2400 => 3.0
      case x if x >= 2300 => 2.0
      case x if x >= 2200 => 1.0
      case x if x >= 2100 => 0.0
      case x if x >= 2000 => -1.0
      case x if x >= 1900 => -2.0
      case x if x >= 1800 => -3.0
      case x if x >= 1700 => -4.0
      case x if x >= 1650 => -5.0
      case x if x >= 1600 => -6.0
      case x if x >= 1550 => -7.0
      case x if x >= 1500 => -8.0
      case x if x >= 1450 => -9.0
      case x if x >= 1400 => -10.0
      case x if x >= 1350 => -11.0
      case x if x >= 1300 => -12.0
      case x if x >= 1250 => -13.0
      case x if x >= 1200 => -14.0
      case x if x >= 1150 => -15.0
      case x if x >= 1100 => -16.0
      case x if x >= 1050 => -17.0
      case x if x >= 1000 => -18.0
      case x if x >= 966  => -19.0
      case x if x >= 933  => -20.0
      case x if x >= 900  => -21.0
      case x if x >= 867  => -22.0
      case x if x >= 834  => -23.0
      case x if x >= 801  => -24.0
      case x if x >= 768  => -25.0
      case x if x >= 735  => -26.0
      case x if x >= 702  => -27.0
      case x if x >= 669  => -28.0
      case x if x >= 636  => -29.0
      case x if x < 636   => -30.0
      case _              => 0.0
    }
  }

  private def calcGoMcMahonHandicap(size: Int, scoreDiff: Int): GoHandicap = {
    size match {
      case 9 =>
        scoreDiff match {
          case 0 => GoHandicap(55, 0)
          case 1 => GoHandicap(55, 0)
          case 2 => GoHandicap(0, 0)
          case 3 => GoHandicap(55, 2)
          case x if x % 3 == 1 => GoHandicap(35, ((x + 2) / 3))
          case x if x % 3 == 2 => GoHandicap(15, ((x + 1) / 3))
          case x if x % 3 == 0 => GoHandicap(55, ((x + 3) / 3))
        }
      case 13 =>
        scoreDiff match {
          case 0 => GoHandicap(75, 0)
          case 1 => GoHandicap(75, 0)
          case 2 => GoHandicap(0, 0)
          case 3 => GoHandicap(75, 2)
          case x if x % 2 == 0 => GoHandicap(35, (x / 2))
          case x if x % 2 == 1 => GoHandicap(75, ((x + 1) / 2))
        }
      case 19 =>
        scoreDiff match {
          case 0 => GoHandicap(75, 0)
          case 1 => GoHandicap(75, 0)
          case 2 => GoHandicap(0, 0)
          case _ => GoHandicap(75, scoreDiff - 1)
        }
    }
  }
}

case class GoHandicap(komi: Int, stones: Int) //komi is 10x to be int

sealed trait GoRatingType
case object KyuRating extends GoRatingType
case object DanRating extends GoRatingType
