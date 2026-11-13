package lila.progress

import org.joda.time.DateTime
import reactivemongo.api.bson.*

import lila.db.BSON
import lila.memo.CacheApi.beafedAsync
import lila.db.dsl.*
import lila.notify.{ GenericLink, Notification, NotifyApi }
import lila.user.User

final class ProgressApi(
    coll: Coll,
    cacheApi: lila.memo.CacheApi,
    notifyApi: NotifyApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  implicit private val awardHandler: BSONDocumentHandler[BadgeAward] = Macros.handler[BadgeAward]

  implicit private val progressHandler: BSON[Progress] = new BSON[Progress] {
    def reads(r: BSON.Reader) =
      Progress(
        _id = r.str("_id"),
        steps = r.getD[Map[String, DateTime]]("steps", Map.empty),
        badges = r.getD[Map[String, BadgeAward]]("badges", Map.empty),
        variants = r.getD[Set[String]]("variants", Set.empty)
      )
    def writes(w: BSON.Writer, p: Progress) =
      $doc(
        "_id"      -> p._id,
        "steps"    -> p.steps,
        "badges"   -> p.badges,
        "variants" -> p.variants
      )
  }

  private val cache = cacheApi[User.ID, Progress](16384, "progress") {
    _.expireAfterAccess(20 minutes)
      .buildAsyncFuture(fetch)
  }

  private def fetch(userId: User.ID): Fu[Progress] =
    coll.byId[Progress](userId).dmap(_ | Progress(userId))

  def get(userId: User.ID): Fu[Progress] = cache.get(userId)

  def tick(userId: User.ID, step: Step): Funit =
    get(userId) flatMap { progress =>
      (!progress.has(step)) so {
        writeStep(userId, step) flatMap { _ so awardOnboarded(userId) }
      }
    }

  def tickAll(userId: User.ID, steps: List[Step]): Funit =
    steps.foldLeft(funit)((acc, step) => acc >> tick(userId, step))

  def playedVariant(userId: User.ID, variantKey: String): Funit =
    (Progress.variantKeys contains variantKey) so get(userId).flatMap { progress =>
      (!progress.hasPlayed(variantKey)) so {
        upsert($id(userId), $addToSet("variants" -> variantKey))
          .void
          .andDo(cache.invalidate(userId)) >> awardAllVariants(userId)
      }
    }

  private def writeStep(userId: User.ID, step: Step): Fu[Boolean] = {
    val field = s"steps.${step.key}"
    upsert($id(userId) ++ $doc(field -> $exists(false)), $set(field -> DateTime.now))
      .map(_.n > 0)
      .recover(lila.db.recoverDuplicateKey(_ => false))
      .andDo(cache.invalidate(userId))
  }

  private def upsert(selector: Bdoc, modifier: Bdoc) =
    coll.update
      .one(selector, modifier, upsert = true)
      .recoverWith { case wr: reactivemongo.api.commands.WriteResult if lila.db.isDuplicateKey(wr) =>
        coll.update.one(selector, modifier, upsert = true)
      }

  private def awardOnboarded(userId: User.ID): Funit =
    get(userId) flatMap { progress =>
      val level = progress.onboardedLevel
      (level > progress.awardLevel(Badge.Onboarded)) so award(userId, Badge.Onboarded, level)
    }

  private def awardAllVariants(userId: User.ID): Funit =
    get(userId) flatMap { progress =>
      (progress.hasPlayedAllVariants && progress.award(Badge.AllVariants).isEmpty) so
        award(userId, Badge.AllVariants, 1)
    }

  private def award(userId: User.ID, badge: Badge, level: Int): Funit = {
    val field = s"badges.${badge.key}"
    coll.update
      .one(
        $id(userId) ++ $doc(s"$field.level" -> $doc("$not" -> $doc("$gte" -> level))),
        $set(field -> BadgeAward(level, DateTime.now))
      )
      .flatMap { res =>
        cache.invalidate(userId)
        (res.nModified > 0) so notifyAward(userId, badge, level)
      }
  }

  private def notifyAward(userId: User.ID, badge: Badge, level: Int): Funit =
    notifyApi.addNotification(
      Notification.make(
        Notification.Notifies(userId),
        GenericLink(
          url = s"/@/$userId",
          title = s"Badge earned: ${ProgressApi.badgeName(badge, level)}".some,
          text = ProgressApi.badgeText(badge, level).some,
          icon = ProgressIcon(badge, level).toString
        )
      )
    )
}

private object ProgressApi {

  def badgeName(badge: Badge, level: Int): String =
    (badge, level) match {
      case (Badge.Onboarded, 1) => "Onboarded"
      case (Badge.Onboarded, _) => "Fully onboarded"
      case (Badge.AllVariants, _) => "Every variant played"
    }

  def badgeText(badge: Badge, level: Int): String =
    (badge, level) match {
      case (Badge.Onboarded, 1) => "You've completed the core steps. Keep exploring PlayStrategy!"
      case (Badge.Onboarded, _) => "You've explored everything on your checklist."
      case (Badge.AllVariants, _) => "You've played a game in every variant on PlayStrategy."
    }
}
