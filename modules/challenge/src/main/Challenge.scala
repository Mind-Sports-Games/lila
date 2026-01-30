package lila.challenge

import strategygames.format.FEN
import strategygames.variant.Variant
import strategygames.chess.variant.Chess960
import strategygames.{ P2, Player => PlayerIndex, GameFamily, GameLogic, Mode, Speed, P1 }

import org.joda.time.DateTime
import scala.util.Random

import lila.game.{ Game, PerfPicker }
import lila.i18n.{ I18nKey, I18nKeys }
import play.api.i18n.Lang
import lila.rating.PerfType
import lila.user.User

case class Challenge(
    _id: String,
    status: Challenge.Status,
    variant: Variant,
    initialFen: Option[FEN],
    timeControl: Challenge.TimeControl,
    mode: Mode,
    playerIndexChoice: Challenge.PlayerIndexChoice,
    finalPlayerIndex: PlayerIndex,
    challenger: Challenge.Challenger,
    destUser: Option[Challenge.Challenger.Registered],
    rematchOf: Option[Game.ID],
    createdAt: DateTime,
    seenAt: Option[DateTime], // None for open challenges, so they don't sweep
    expiresAt: DateTime,
    open: Option[Boolean] = None,
    name: Option[String] = None,
    declineReason: Option[Challenge.DeclineReason] = None,
    multiMatch: Option[Boolean] = None,
    backgammonPoints: Option[Int] = None
) {

  import Challenge._

  def id = _id

  def challengerUser =
    challenger match {
      case u: Challenger.Registered => u.some
      case _                        => none
    }
  def challengerUserId = challengerUser.map(_.id)
  def challengerIsAnon =
    challenger match {
      case _: Challenger.Anonymous => true
      case _                       => false
    }
  def challengerIsOpen =
    challenger match {
      case Challenger.Open => true
      case _               => false
    }
  def destUserId = destUser.map(_.id)

  def userIds = List(challengerUserId, destUserId).flatten

  def daysPerTurn =
    timeControl match {
      case TimeControl.Correspondence(d) => d.some
      case _                             => none
    }
  def unlimited = timeControl == TimeControl.Unlimited

  def clock =
    timeControl match {
      case c: TimeControl.Clock => c.some
      case _                    => none
    }

  def hasClock = clock.isDefined

  def openDest = destUser.isEmpty
  def online   = status == Status.Created
  def active   = online || status == Status.Offline
  def declined = status == Status.Declined
  def accepted = status == Status.Accepted

  def setChallenger(u: Option[User], secret: Option[String]) =
    copy(
      challenger = u.map(toRegistered(variant, timeControl)) orElse
        secret.map(Challenger.Anonymous.apply) getOrElse Challenger.Open
    )
  def setDestUser(u: User) =
    copy(
      destUser = toRegistered(variant, timeControl)(u).some
    )

  def speed = speedOf(timeControl)

  def notableInitialFen: Option[FEN] =
    variant match {
      case Variant.Chess(variant)                => if (variant.standardInitialPosition) none else initialFen
      case Variant.Draughts(_)                   => draughtsCustomStartingPosition ?? initialFen
      case Variant.Go(_) | Variant.Backgammon(_) => customStartingPosition ?? initialFen
      case _                                     => none
    }

  def customStartingPosition: Boolean =
    initialFen.isDefined && !initialFen.exists(f => variant.initialFens.map(_.value).contains(f.value))

  private def draughtsCustomStartingPosition: Boolean =
    variant == Variant.Draughts(strategygames.draughts.variant.FromPosition) ||
      (draughtsFenVariants(variant) && customStartingPosition)

  //When updating, also edit modules/game, modules/puzzle and ui/@types/playstrategy/index.d.ts:declare type PlayerName
  def playerTrans(p: PlayerIndex)(implicit lang: Lang): String =
    variant.playerNames(p) match {
      case "White" => I18nKeys.white.txt()
      case "Black" => I18nKeys.black.txt()
      //Xiangqi add back in when adding red as a colour for Xiangqi
      //case "Red"   => I18nKeys.red.txt()
      case "Sente"   => I18nKeys.sente.txt()
      case "Gote"    => I18nKeys.gote.txt()
      case s: String => s
    }

  def playerChoiceTrans(p: PlayerIndexChoice)(implicit lang: Lang): String = p match {
    case PlayerIndexChoice.Random => "random"
    case PlayerIndexChoice.P1     => playerTrans(P1)
    case PlayerIndexChoice.P2     => playerTrans(P2)
  }

  def isOpen = ~open

  def isMultiMatch = ~multiMatch

  lazy val perfType = perfTypeOf(variant, timeControl)

  def anyDeclineReason = declineReason | DeclineReason.default

  def declineWith(reason: DeclineReason) = copy(
    status = Status.Declined,
    declineReason = reason.some
  )
}

object Challenge {

  type ID = String

  sealed abstract class Status(val id: Int) {
    val name = toString.toLowerCase
  }
  object Status {
    case object Created  extends Status(10)
    case object Offline  extends Status(15)
    case object Canceled extends Status(20)
    case object Declined extends Status(30)
    case object Accepted extends Status(40)
    val all                            = List(Created, Offline, Canceled, Declined, Accepted)
    def apply(id: Int): Option[Status] = all.find(_.id == id)
  }

  sealed abstract class DeclineReason(val trans: I18nKey) {
    val key = toString.toLowerCase
  }

  object DeclineReason {
    case object Generic     extends DeclineReason(I18nKeys.challenge.declineGeneric)
    case object Later       extends DeclineReason(I18nKeys.challenge.declineLater)
    case object TooFast     extends DeclineReason(I18nKeys.challenge.declineTooFast)
    case object TooSlow     extends DeclineReason(I18nKeys.challenge.declineTooSlow)
    case object TimeControl extends DeclineReason(I18nKeys.challenge.declineTimeControl)
    case object Rated       extends DeclineReason(I18nKeys.challenge.declineRated)
    case object Casual      extends DeclineReason(I18nKeys.challenge.declineCasual)
    case object Standard    extends DeclineReason(I18nKeys.challenge.declineStandard)
    case object Variant     extends DeclineReason(I18nKeys.challenge.declineVariant)
    case object NoBot       extends DeclineReason(I18nKeys.challenge.declineNoBot)
    case object OnlyBot     extends DeclineReason(I18nKeys.challenge.declineOnlyBot)
    case object NoAnon      extends DeclineReason(I18nKeys.challenge.declineNoAnon)

    val default: DeclineReason = Generic
    val all: List[DeclineReason] =
      List(Generic, Later, TooFast, TooSlow, TimeControl, Rated, Casual, Standard, Variant, NoBot, OnlyBot, NoAnon)
    val allExceptBot: List[DeclineReason] =
      all.filterNot(r => r == NoBot || r == OnlyBot)
    def apply(key: String) = all.find { d => d.key == key.toLowerCase || d.trans.key == key } | Generic
  }

  case class Rating(int: Int, provisional: Boolean) {
    def show = s"$int${if (provisional) "?" else ""}"
  }
  object Rating {
    def apply(p: lila.rating.Perf): Rating = Rating(p.intRating, p.provisional)
  }

  sealed trait Challenger
  object Challenger {
    case class Registered(id: User.ID, rating: Rating) extends Challenger
    case class Anonymous(secret: String)               extends Challenger
    case object Open                                   extends Challenger
  }

  sealed trait TimeControl
  object TimeControl {
    case object Unlimited                extends TimeControl
    case class Correspondence(days: Int) extends TimeControl
    case class Clock(config: strategygames.ClockConfig) extends TimeControl {
      // All durations are expressed in seconds
      def limit = config.limit
      // TODO: This should be renamed to properly reflect that it also
      //       represents Bronstein and SimpleDelay and not just increment
      def increment = config match {
        case c: strategygames.ByoyomiClock.Config => c.byoyomiSeconds
        case c                                    => c.graceSeconds
      }
      def show = config.show
    }
  }

  sealed trait PlayerIndexChoice
  object PlayerIndexChoice {
    case object Random extends PlayerIndexChoice
    case object P1     extends PlayerIndexChoice
    case object P2     extends PlayerIndexChoice
    def apply(c: PlayerIndex) = c.fold[PlayerIndexChoice](P1, P2)
  }

  private def speedOf(timeControl: TimeControl) =
    timeControl match {
      case TimeControl.Clock(config) => Speed(config)
      case _                         => Speed.Correspondence
    }

  private def perfTypeOf(variant: Variant, timeControl: TimeControl): PerfType =
    lila.rating.PerfType(variant, speedOf(timeControl))

  private val idSize = 8

  private def randomId = lila.common.ThreadLocalRandom nextString idSize

  def toRegistered(variant: Variant, timeControl: TimeControl)(u: User) =
    Challenger.Registered(u.id, Rating(u.perfs(perfTypeOf(variant, timeControl))))

  def randomPlayerIndex = PlayerIndex.fromP1(lila.common.ThreadLocalRandom.nextBoolean())

  // NOTE: Only variants with standardInitialPosition = false!
  private val draughtsFenVariants: Set[Variant] =
    GameFamily.Draughts().variants.filter(v => v.fenVariant || v.fromPositionVariant).toSet

  def make(
      variant: Variant,
      fenVariant: Option[Variant],
      initialFen: Option[FEN],
      timeControl: TimeControl,
      mode: Mode,
      playerIndex: String,
      challenger: Challenger,
      destUser: Option[User],
      rematchOf: Option[Game.ID],
      name: Option[String] = None,
      multiMatch: Boolean = false,
      //this could be extended into 'gameSettings' if more game specific fields are required
      backgammonPoints: Option[Int] = None
  ): Challenge = {
    val (playerIndexChoice, finalPlayerIndex) = playerIndex match {
      case "p1" => PlayerIndexChoice.P1     -> P1
      case "p2" => PlayerIndexChoice.P2     -> P2
      case _    => PlayerIndexChoice.Random -> randomPlayerIndex
    }
    val finalVariant = fenVariant match {
      case Some(v) if draughtsFenVariants(variant) =>
        if (variant.fromPositionVariant && v.standardVariant)
          Variant
            .byName(GameLogic.Draughts(), "From Position")
            .getOrElse(Variant.orDefault(GameLogic.Draughts(), 3))
        else v
      case _ => variant
    }
    //val finalInitialFen = finalVariant match {
    //  case Variant.Draughts(v) =>
    //    draughtsFenVariants(v) ?? {
    //      initialFen.flatMap(fen => Forsyth.<<@(finalVariant.gameLogic, finalVariant, fen.value))
    //        .map(sit => FEN(Forsyth.>>(finalVariant.gameLogic, sit.withoutGhosts)))
    //    } match {
    //      case fen @ Some(_) => fen
    //      case _ => !finalVariant.standardInitialPosition option FEN(finalVariant.initialFen)
    //    }
    //}
    val finalMode = timeControl match {
      case TimeControl.Clock(clock) if !lila.game.Game.allowRated(variant, clock.some) => Mode.Casual
      case _                                                                           => mode
    }
    val isOpen = challenger == Challenge.Challenger.Open
    var challenge = new Challenge(
      _id = randomId,
      status = Status.Created,
      variant = variant,
      initialFen =
        if (
          variant.fromPositionVariant || variant.gameFamily == GameFamily
            .Go() || variant.gameFamily == GameFamily.Backgammon()
        ) initialFen
        else if (variant == Variant.Chess(Chess960)) initialFen filter { fen =>
          fen.chessFen.map(fen => Chess960.positionNumber(fen).isDefined).getOrElse(false)
        }
        else if (variant.initialFens.size > 1)
          Random.shuffle(variant.initialFens).headOption
        else !variant.standardInitialPosition option variant.initialFen,
      timeControl = timeControl,
      mode = finalMode,
      playerIndexChoice = playerIndexChoice,
      finalPlayerIndex = finalPlayerIndex,
      challenger = challenger,
      destUser = destUser map toRegistered(variant, timeControl),
      rematchOf = rematchOf,
      createdAt = DateTime.now,
      seenAt = !isOpen option DateTime.now,
      expiresAt = if (isOpen) DateTime.now.plusDays(1) else inTwoWeeks,
      open = isOpen option true,
      name = name,
      multiMatch = multiMatch option true,
      backgammonPoints = backgammonPoints
    )
    if (multiMatch && !challenge.customStartingPosition)
      challenge = challenge.copy(multiMatch = none)
    if (challenge.mode.rated && !challenge.isMultiMatch && challenge.customStartingPosition)
      challenge = challenge.copy(mode = Mode.Casual)
    challenge
  }
}
