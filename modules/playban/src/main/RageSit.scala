package lila.playban

import strategygames.{ Player => PlayerIndex, Speed }
import scala.math.{ log10, sqrt }

import lila.game.Game

case class RageSit(counter: Int) extends AnyVal {
  def isBad      = counter <= -40
  def isVeryBad  = counter <= -80
  def isTerrible = counter <= -160

  def goneWeight: Float =
    if (!isBad) 1f
    else (1 - 0.7 * sqrt(log10(-(counter / 10) - 3))).toFloat atLeast 0.1f

  def counterView = counter / 10
}

object RageSit {
  val empty = RageSit(0)

  sealed trait Update
  case object Noop       extends Update
  case object Reset      extends Update
  case class Inc(v: Int) extends Update

  def imbalanceInc(game: Game, loser: PlayerIndex) =
    Inc {
      {
        game.board.materialImbalance match {
          case _ if game.variant.materialImbalanceVariant => 0
          case a if a >= 4                                => 1
          case a if a <= -4                               => -1
          case _                                          => 0
        }
      } * {
        if (loser.p1) 1 else -1
      } * {
        if (game.speed <= Speed.Bullet) 5
        else if (game.speed == Speed.Blitz) 10
        else 15
      }
    }

  def redeem(game: Game): Inc =
    Inc {
      game.speed match {
        case s if s < Speed.Bullet => 0
        case Speed.Bullet          => lila.common.ThreadLocalRandom.nextInt(2)
        case Speed.Blitz           => 1
        case _                     => 2
      }
    }
}

case class SittingDetected(game: Game, userId: String)
