package lila.plan

import lila.db.dsl._
import lila.user.UserRepo

import org.joda.time.DateTime

final private class Expiration(
    userRepo: UserRepo,
    patronColl: Coll,
    notifier: PlanNotifier
)(implicit ec: scala.concurrent.ExecutionContext) {

  import BsonHandlers._
  import PatronHandlers._

  def run: Funit =
    getExpired flatMap {
      patrons =>
        Future.sequence(patrons.map { patron =>
          patronColl.update.one($id(patron.id), patron.removeStripe.removePayPal) >>
            disableUserPlanOf(patron).andDo(logger.info(s"Expired $patron"))
        }).void
    }

  private def disableUserPlanOf(patron: Patron): Funit =
    userRepo byId patron.userId flatMap {
      _ so { user =>
        userRepo.setPlan(user, user.plan.disable).andDo(notifier.onExpire(user))
      }
    }

  private def getExpired =
    patronColl.list[Patron](
      $doc(
        "expiresAt" $lt DateTime.now,
        "lifetime" $ne true
      ),
      50
    )
}
