package lila.progress

import org.joda.time.DateTime

sealed abstract class BadgeGroup(val key: String)

object BadgeGroup {
  case object Onboarding   extends BadgeGroup("onboarding")
  case object Achievements extends BadgeGroup("achievements")

  val all: List[BadgeGroup] = List(Onboarding, Achievements)
}

sealed abstract class Badge(val key: String, val group: BadgeGroup, val maxLevel: Int)

object Badge {
  case object Onboarded   extends Badge("onboarded", BadgeGroup.Onboarding, maxLevel = 2)
  case object AllVariants extends Badge("allVariants", BadgeGroup.Achievements, maxLevel = 1)

  val all: List[Badge] = List(Onboarded, AllVariants)

  val byKey: Map[String, Badge] = all.map(b => b.key -> b).toMap
}

case class BadgeAward(level: Int, at: DateTime)

object ProgressIcon {

  // glyphs in public/font/playstrategy.woff(2)
  val onboarded: Char      = '\ue947'
  val fullyOnboarded: Char = '\ue948'
  val allVariants: Char    = '\ue949'

  def apply(badge: Badge, level: Int): Char =
    badge match {
      case Badge.Onboarded if level >= 2 => fullyOnboarded
      case Badge.Onboarded               => onboarded
      case Badge.AllVariants             => allVariants
    }
}
