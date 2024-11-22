package lila.tournament

import lila.common.Heapsort.implicits._
import lila.user.User

case class Spotlight(
    headline: String,
    description: String,
    homepageHours: Option[Int] = None, // feature on homepage hours before start (max 24)
    iconFont: Option[String] = None,
    iconImg: Option[String] = None
)

object Spotlight {

  import Schedule.Freq._

  //if re-enabling original lichess ordering, will want to change botN to topN
  //implicit private val importanceOrdering = Ordering.by[Tournament, Int](_.schedule.??(_.freq.importance))
  implicit private val importanceOrdering = Ordering.by[Tournament, org.joda.time.DateTime](_.startsAt)

  def select(tours: List[Tournament], user: Option[User], max: Int): List[Tournament] =
    user.fold(tours botN max) { select(tours, _, max) }

  def select(tours: List[Tournament], user: User, max: Int): List[Tournament] =
    tours.filter { select(_, user) } botN max

  private def select(tour: Tournament, user: User): Boolean =
    !tour.isFinished &&
      tour.spotlight.fold(automatically(tour, user)) { manually(tour, _) }

  private def manually(tour: Tournament, spotlight: Spotlight): Boolean =
    spotlight.homepageHours.exists { hours =>
      tour.startsAt.minusHours(hours).isBeforeNow
    }

  private def automatically(tour: Tournament, user: User): Boolean =
    tour.schedule ?? { sched =>
      def playedSinceWeeks(weeks: Int) =
        user.perfs(tour.perfType).latest ?? {
          _.plusWeeks(weeks).isAfterNow
        }
      sched.freq match {
        case Hourly                    => canMaybeJoinLimited(tour, user) && playedSinceWeeks(2)
        case Daily                     => playedSinceWeeks(2)
        case Weekly | Weekend          => playedSinceWeeks(4)
        case Unique                    => playedSinceWeeks(4)
        case Monthly                   => playedSinceWeeks(8)
        case MedleyShield | Shield     => true
        case Marathon                  => true
        case MedleyMarathon | Yearly   => true
        case Annual | Introductory     => true
        case MSO21 | MSOGP | MSOWarmUp => true
        case ExperimentalMarathon      => false
      }
    }

  private def canMaybeJoinLimited(tour: Tournament, user: User): Boolean =
    tour.conditions.isRatingLimited &&
      tour.conditions.nbRatedGame.fold(true) { c =>
        c(user).accepted
      } &&
      tour.conditions.minRating.fold(true) { c =>
        c(user).accepted
      } &&
      tour.conditions.maxRating.fold(true)(_ maybe user)
}
