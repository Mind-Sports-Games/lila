package lila.bot

import org.joda.time.DateTime
import strategygames.ClockConfig
import strategygames.variant.Variant

import lila.common.LightUser
import lila.game.Source
import lila.user.User

case class BotStream(
    _id: BotStream.ID,
    name: String,
    matchups: List[BotStream.Matchup],
    clock: ClockConfig,
    priority: Boolean,
    active: Boolean,
    until: Option[DateTime],
    createdBy: User.ID,
    createdAt: DateTime,
    updatedAt: DateTime
) {

  def id = _id

  def isExpired = until.exists(_.isBeforeNow)

  def source = if (priority) Source.BotVsBotPriority else Source.BotVsBotStream

  lazy val games: List[BotVsBotGame] =
    matchups.flatMap { m =>
      BotVsBotConfig.gamesForMatchup(m.p1User, m.p2User, m.variants, clock)
    }

  def shouldRun = active && !isExpired && games.nonEmpty

  def botIds: Set[User.ID] = matchups.flatMap(m => List(m.p1, m.p2)).toSet

  def sameDefinition(other: BotStream) =
    name == other.name && matchups == other.matchups && clock == other.clock && priority == other.priority
}

object BotStream {

  type ID = String

  case class Matchup(p1: User.ID, p2: User.ID, variants: List[Variant]) {
    def p1User = BotVsBotConfig.psBot(p1) | LightUser.fallback(p1)
    def p2User = BotVsBotConfig.psBot(p2) | LightUser.fallback(p2)
  }

  def makeId = lila.common.ThreadLocalRandom.nextString(8)
}

case class BotStreamStatus(
    currentGame: Option[lila.game.Game.ID],
    cyclePosition: Int,
    cycleLength: Int,
    missingBots: Set[User.ID]
)
