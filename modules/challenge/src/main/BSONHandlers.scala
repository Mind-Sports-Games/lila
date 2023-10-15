package lila.challenge

import reactivemongo.api.bson._

import strategygames.GameLogic
import strategygames.variant.Variant
import lila.db.BSON
import lila.db.BSON.{ Reader, Writer }
import lila.db.dsl._
import scala.util.Success

import cats.implicits._

private object BSONHandlers {

  import Challenge._

  implicit val PlayerIndexChoiceBSONHandler = BSONIntegerHandler.as[PlayerIndexChoice](
    {
      case 1 => PlayerIndexChoice.P1
      case 2 => PlayerIndexChoice.P2
      case _ => PlayerIndexChoice.Random
    },
    {
      case PlayerIndexChoice.P1     => 1
      case PlayerIndexChoice.P2     => 2
      case PlayerIndexChoice.Random => 0
    }
  )
  // NOTE: the following works because all of our clock type representations are different enough
  //       from each other that we can distinguish between them by the existence of their attributes
  //       New clock types will need to continue this pattern.
  implicit val TimeControlBSONHandler = new BSON[TimeControl] {
    def reads(r: Reader) = // TODO: need bronstein here too
      (r.intO("l"), r.intO("i"), r.intO("b"), r.intO("p"))
        .mapN((limit, inc, byoyomi, periods) =>
          TimeControl.Clock(strategygames.ByoyomiClock.Config(limit, inc, byoyomi, periods))
        )
        .getOrElse(
          (r.intO("l"), r.intO("i"))
            .mapN { (limit, inc) =>
              TimeControl.Clock(strategygames.Clock.Config(limit, inc))
            }
            .orElse(
              r intO "d" map TimeControl.Correspondence.apply
            )
            .getOrElse(TimeControl.Unlimited)
        )
    def writes(w: Writer, t: TimeControl) = // TODO: need to handle Bronstein here too
      t match {
        case TimeControl.Clock(strategygames.Clock.Config(l, i))          => $doc("l" -> l, "i" -> i)
        case TimeControl.Clock(strategygames.Clock.BronsteinConfig(l, d)) => $doc("l" -> l, "d" -> d)
        case TimeControl.Clock(strategygames.ByoyomiClock.Config(l, i, b, p)) =>
          $doc("l" -> l, "i" -> i, "b" -> b, "p" -> p)
        case TimeControl.Correspondence(d) => $doc("d" -> d)
        case TimeControl.Unlimited         => $empty
      }
  }

  implicit val VariantBSONHandler = new BSON[Variant] {
    def reads(r: Reader) = Variant(GameLogic(r.intD("gl")), r.int("v")) match {
      case Some(v) => v
      case None    => sys.error(s"No such variant: ${r.intD("v")} for gamelogic: ${r.intD("gl")}")
    }
    def writes(w: Writer, v: Variant) = $doc("gl" -> v.gameLogic.id, "v" -> v.id)
  }

  implicit val StatusBSONHandler = tryHandler[Status](
    { case BSONInteger(v) => Status(v) toTry s"No such status: $v" },
    x => BSONInteger(x.id)
  )
  implicit val RatingBSONHandler = new BSON[Rating] {
    def reads(r: Reader) = Rating(r.int("i"), r.boolD("p"))
    def writes(w: Writer, r: Rating) =
      $doc(
        "i" -> r.int,
        "p" -> w.boolO(r.provisional)
      )
  }
  implicit val RegisteredBSONHandler = new BSON[Challenger.Registered] {
    def reads(r: Reader) = Challenger.Registered(r.str("id"), r.get[Rating]("r"))
    def writes(w: Writer, r: Challenger.Registered) =
      $doc(
        "id" -> r.id,
        "r"  -> r.rating
      )
  }
  implicit val AnonymousBSONHandler = new BSON[Challenger.Anonymous] {
    def reads(r: Reader) = Challenger.Anonymous(r.str("s"))
    def writes(w: Writer, a: Challenger.Anonymous) =
      $doc(
        "s" -> a.secret
      )
  }
  implicit val DeclineReasonBSONHandler = tryHandler[DeclineReason](
    { case BSONString(k) => Success(Challenge.DeclineReason(k)) },
    r => BSONString(r.key)
  )
  implicit val ChallengerBSONHandler = new BSON[Challenger] {
    def reads(r: Reader) =
      if (r contains "id") RegisteredBSONHandler reads r
      else if (r contains "s") AnonymousBSONHandler reads r
      else Challenger.Open
    def writes(w: Writer, c: Challenger) =
      c match {
        case a: Challenger.Registered => RegisteredBSONHandler.writes(w, a)
        case a: Challenger.Anonymous  => AnonymousBSONHandler.writes(w, a)
        case _                        => $empty
      }
  }

  implicit val ChallengeBSONHandler = Macros.handler[Challenge]
}
