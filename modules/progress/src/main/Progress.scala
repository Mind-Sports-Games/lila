package lila.progress

import org.joda.time.DateTime

import lila.rating.PerfType
import lila.user.User

case class Progress(
    _id: User.ID,
    steps: Map[String, DateTime] = Map.empty,
    badges: Map[String, BadgeAward] = Map.empty,
    variants: Set[String] = Set.empty
) {

  def userId = _id

  def has(step: Step): Boolean = steps contains step.key

  def remaining: List[Step] = Step.all.filterNot(has)

  def remainingIn(group: StepGroup): List[Step] = Step.ofGroup(group).filterNot(has)

  def nextStep: Option[Step] = remaining.headOption

  def nbCoreDone: Int    = Step.core.count(has)
  def nbExploreDone: Int = Step.explore.count(has)

  def isOnboarded: Boolean      = Step.core.forall(has)
  def isFullyOnboarded: Boolean = Step.all.forall(has)

  def onboardedLevel: Int =
    if (isFullyOnboarded) 2
    else if (isOnboarded) 1
    else 0

  def award(badge: Badge): Option[BadgeAward] = badges get badge.key

  def awardLevel(badge: Badge): Int = award(badge).fold(0)(_.level)

  def awarded: List[(Badge, BadgeAward)] =
    Badge.all.flatMap(badge => award(badge).map(badge -> _))

  def hasPlayed(variantKey: String): Boolean = variants contains variantKey

  def nbVariantsPlayed: Int = Progress.variantKeys.count(variants.contains)

  def hasPlayedAllVariants: Boolean = Progress.variantKeys.forall(variants.contains)
}

object Progress {

  val variantKeys: List[String] = PerfType.allVariant.map(_.key)

  def nbVariants = variantKeys.size
}
