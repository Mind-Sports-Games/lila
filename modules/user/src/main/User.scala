package lila.user

import org.joda.time.DateTime
import play.api.i18n.Lang
import scala.concurrent.duration._

import lila.common.{ EmailAddress, LightUser, NormalizedEmailAddress }
import lila.rating.{ Perf, PerfType }
import lila.hub.actorApi.user.{ KidId, NonKidId }

import strategygames.GameLogic
import reactivemongo.api.bson.BSONDocumentHandler
import reactivemongo.api.bson.BSONHandler

case class User(
    id: String,
    username: String,
    perfs: Perfs,
    count: Count,
    enabled: Boolean,
    roles: List[String],
    profile: Option[Profile] = None,
    toints: Int = 0,
    playTime: Option[User.PlayTime],
    title: Option[Title] = None,
    createdAt: DateTime,
    seenAt: Option[DateTime],
    kid: Boolean,
    lang: Option[String],
    plan: Plan,
    totpSecret: Option[TotpSecret] = None,
    marks: UserMarks = UserMarks.empty
) extends Ordered[User] {

  override def equals(other: Any) =
    other match {
      case u: User => id == u.id
      case _       => false
    }

  override def hashCode: Int = id.hashCode

  override def toString =
    s"User $username(${perfs.bestRating}) games:${count.game}${marks.troll ?? " troll"}${marks.engine ?? " engine"}${!enabled ?? " closed"}"

  def light = LightUser(
    id = id,
    name = username,
    country = profile.flatMap(_.country),
    title = title.map(_.value),
    isPatron = isPatron
  )

  def realNameOrUsername = profileOrDefault.nonEmptyRealName | username

  def realLang = lang flatMap Lang.get

  def compare(other: User) = id compareTo other.id

  def disabled = !enabled

  def canPalantir = !kid && !marks.troll

  def usernameWithBestRating = s"$username (${perfs.bestRating})"

  def titleUsername = title.fold(username)(t => s"$t $username")

  def hasVariantRating = PerfType.variants.exists(perfs.apply(_).nonEmpty)

  def titleUsernameWithBestRating =
    title.fold(usernameWithBestRating) { t =>
      s"$t $usernameWithBestRating"
    }

  def profileOrDefault = profile | Profile.default

  def hasGames = count.game > 0

  def countRated = count.rated

  def hasTitle = title.exists(Title.BOT !=)

  lazy val seenRecently: Boolean = timeNoSee < User.seenRecently

  def timeNoSee: Duration = (nowMillis - (seenAt | createdAt).getMillis).millis

  def everLoggedIn = seenAt.??(createdAt !=)

  def lame = marks.boost || marks.engine

  def lameOrTroll      = lame || marks.troll
  def lameOrAlt        = lame || marks.alt
  def lameOrTrollOrAlt = lameOrTroll || marks.alt

  def canBeFeatured = hasTitle && !lameOrTroll

  def isSimulFeatured = roles.exists(_ contains "FEATURED_SIMUL")

  def canFullyLogin = enabled || !lameOrTrollOrAlt

  def withMarks(f: UserMarks => UserMarks) = copy(marks = f(marks))

  def lightPerf(key: String) =
    perfs(key) map { perf =>
      User.LightPerf(light, key, perf.intRating, perf.progress)
    }

  def lightCount = User.LightCount(light, count.game)

  private def bestOf(perfTypes: List[PerfType], nb: Int) =
    perfTypes.sortBy { pt =>
      -(perfs(pt).nb * PerfType.totalTimeRoughEstimation(pt).roundSeconds)
    } take nb

  // TODO once ratings for all games have been decided, change grouping in rating/src/main/PerfType
  def best8Perfs: List[PerfType] = bestOf(PerfType.nonPuzzle, 8)

  def best6Perfs: List[PerfType] = bestOf(PerfType.nonPuzzle, 6)

  def best3Perfs: List[PerfType] = bestOf(PerfType.nonPuzzle, 3)

  def hasEstablishedRating(pt: PerfType) = perfs(pt).established

  def isPatron = plan.active

  def activePlan: Option[Plan] = if (plan.active) Some(plan) else None

  def planMonths: Option[Int] = activePlan.map(_.months)

  def createdSinceDays(days: Int) = createdAt isBefore DateTime.now.minusDays(days)

  def is(name: String) = id == User.normalize(name)
  def is(other: User)  = id == other.id

  def isBot = title has Title.BOT
  def noBot = !isBot

  def rankable = noBot && !marks.rankban

  def addRole(role: String) = copy(roles = role :: roles)

  def isVerified   = roles.exists(_ contains "ROLE_VERIFIED")
  def isSuperAdmin = roles.exists(_ contains "ROLE_SUPER_ADMIN")
  def isAdmin      = roles.exists(_ contains "ROLE_ADMIN") || isSuperAdmin
  def isApiHog     = roles.exists(_ contains "ROLE_API_HOG")

  def isPlayStrategyTourBot = roles.exists(_ contains "ROLE_PSTBOT")
  def isUserBot             = isBot && !isPlayStrategyTourBot
}

object User {

  type ID = String

  type CredentialCheck = ClearPassword => Boolean
  case class LoginCandidate(user: User, check: CredentialCheck) {
    import LoginCandidate._
    def apply(p: PasswordAndToken): Result = {
      val res =
        if (check(p.password)) user.totpSecret.fold[Result](Success(user)) { tp =>
          p.token.fold[Result](MissingTotpToken) { token =>
            if (tp verify token) Success(user) else InvalidTotpToken
          }
        }
        else InvalidUsernameOrPassword
      lila.mon.user.auth.count(res.success).increment()
      res
    }
    def option(p: PasswordAndToken): Option[User] = apply(p).toOption
  }
  object LoginCandidate {
    sealed abstract class Result(val toOption: Option[User]) {
      def success = toOption.isDefined
    }
    case class Success(user: User)        extends Result(user.some)
    case object InvalidUsernameOrPassword extends Result(none)
    case object MissingTotpToken          extends Result(none)
    case object InvalidTotpToken          extends Result(none)
  }

  val anonymous      = "Anonymous"
  val playstrategyId = "playstrategy"
  val broadcasterId  = "broadcaster"
  val ghostId        = "ghost"
  def isOfficial(username: String) =
    normalize(username) == playstrategyId || normalize(username) == broadcasterId

  val seenRecently = 2.minutes

  case class GDPRErase(user: User)  extends AnyVal
  case class Erased(value: Boolean) extends AnyVal

  case class LightPerf(user: LightUser, perfKey: String, rating: Int, progress: Int)
  case class LightCount(user: LightUser, count: Int)

  case class Emails(current: Option[EmailAddress], previous: Option[NormalizedEmailAddress]) {
    def list = current.toList ::: previous.toList
  }
  case class WithEmails(user: User, emails: Emails)

  case class ClearPassword(value: String) extends AnyVal {
    override def toString = "ClearPassword(****)"
  }

  case class TotpToken(value: String) extends AnyVal
  case class PasswordAndToken(password: ClearPassword, token: Option[TotpToken])

  case class Speaker(username: String, title: Option[Title], enabled: Boolean, marks: Option[UserMarks]) {
    def isBot   = title has Title.BOT
    def isTroll = marks.exists(_.troll)
  }

  case class Contact(
      _id: ID,
      kid: Option[Boolean],
      marks: Option[UserMarks],
      roles: Option[List[String]],
      createdAt: DateTime
  ) {
    def id                     = _id
    def isKid                  = ~kid
    def isTroll                = marks.exists(_.troll)
    def isVerified             = roles.exists(_ contains "ROLE_VERIFIED")
    def isApiHog               = roles.exists(_ contains "ROLE_API_HOG")
    def isDaysOld(days: Int)   = createdAt isBefore DateTime.now.minusDays(days)
    def isHoursOld(hours: Int) = createdAt isBefore DateTime.now.minusHours(hours)
    def kidId                  = if (isKid) KidId(id) else NonKidId(id)
  }
  case class Contacts(orig: Contact, dest: Contact) {
    def hasKid = orig.isKid || dest.isKid
  }

  case class PlayTime(total: Int, tv: Int) {
    import org.joda.time.Period
    def totalPeriod      = new Period(total * 1000L)
    def tvPeriod         = new Period(tv * 1000L)
    def nonEmptyTvPeriod = (tv > 0) option tvPeriod
  }
  implicit def playTimeHandler: BSONDocumentHandler[PlayTime] =
    reactivemongo.api.bson.Macros.handler[PlayTime]

  // what existing usernames are like
  val historicalUsernameRegex = "(?i)[a-z0-9][a-z0-9_-]{0,28}[a-z0-9]".r
  // what new usernames should be like -- now split into further parts for clearer error messages
  val newUsernameRegex   = "(?i)[a-z][a-z0-9_-]{0,28}[a-z0-9]".r
  val newUsernamePrefix  = "(?i)^[a-z].*".r
  val newUsernameSuffix  = "(?i).*[a-z0-9]$".r
  val newUsernameChars   = "(?i)^[a-z0-9_-]*$".r
  val newUsernameLetters = "(?i)^([a-z0-9][_-]?)+$".r

  def couldBeUsername(str: User.ID) = noGhost(str) && historicalUsernameRegex.matches(str)

  def normalize(username: String) = username.toLowerCase

  def validateId(name: String): Option[User.ID] = couldBeUsername(name) option normalize(name)

  def isGhost(name: String) = normalize(name) == ghostId || name.headOption.has('!')

  def noGhost(name: String) = !isGhost(name)

  object BSONFields {
    val id                    = "_id"
    val username              = "username"
    val perfs                 = "perfs"
    val count                 = "count"
    val enabled               = "enabled"
    val roles                 = "roles"
    val profile               = "profile"
    val country               = s"$profile.country"
    val toints                = "toints"
    val playTime              = "time"
    val createdAt             = "createdAt"
    val seenAt                = "seenAt"
    val kid                   = "kid"
    val createdWithApiVersion = "createdWithApiVersion"
    val lang                  = "lang"
    val title                 = "title"
    def glicko(perf: String)  = s"$perfs.$perf.gl"
    val email                 = "email"
    val verbatimEmail         = "verbatimEmail"
    val mustConfirmEmail      = "mustConfirmEmail"
    val prevEmail             = "prevEmail"
    val playerIndexIt         = "playerIndexIt"
    val plan                  = "plan"
    val salt                  = "salt"
    val bpass                 = "bpass"
    val sha512                = "sha512"
    val totpSecret            = "totp"
    val changedCase           = "changedCase"
    val marks                 = "marks"
    val eraseAt               = "eraseAt"
    val erasedAt              = "erasedAt"
    val blind                 = "blind"
  }

  def withFields[A](f: BSONFields.type => A): A = f(BSONFields)

  import lila.db.BSON
  import lila.db.dsl._

  implicit val userBSONHandler: BSON[User] = new BSON[User] {

    import BSONFields._
    import reactivemongo.api.bson.BSONDocument
    import UserMarks.marksBsonHandler
    import Title.titleBsonHandler
    implicit private def countHandler: BSON[Count]                    = Count.countBSONHandler
    implicit private def profileHandler: BSONDocumentHandler[Profile] = Profile.profileBSONHandler
    implicit private def perfsHandler: BSON[Perfs]                    = Perfs.perfsBSONHandler
    implicit private def planHandler: BSONDocumentHandler[Plan]       = Plan.planBSONHandler
    implicit private def totpSecretHandler: BSONHandler[TotpSecret]   = TotpSecret.totpSecretBSONHandler

    def reads(r: BSON.Reader): User = {
      val userTitle = r.getO[Title](title)
      User(
        id = r str id,
        username = r str username,
        perfs = r.getO[Perfs](perfs).fold(Perfs.default) { perfs =>
          if (userTitle has Title.BOT) perfs.copy(ultraBullet = Perf.default)
          else perfs
        },
        count = r.get[Count](count),
        enabled = r bool enabled,
        roles = ~r.getO[List[String]](roles),
        profile = r.getO[Profile](profile),
        toints = r nIntD toints,
        playTime = r.getO[PlayTime](playTime),
        createdAt = r date createdAt,
        seenAt = r dateO seenAt,
        kid = r boolD kid,
        lang = r strO lang,
        title = userTitle,
        plan = r.getO[Plan](plan) | Plan.empty,
        totpSecret = r.getO[TotpSecret](totpSecret),
        marks = r.getO[UserMarks](marks) | UserMarks.empty
      )
    }

    def writes(w: BSON.Writer, o: User) =
      BSONDocument(
        id         -> o.id,
        username   -> o.username,
        perfs      -> o.perfs,
        count      -> o.count,
        enabled    -> o.enabled,
        roles      -> o.roles.some.filter(_.nonEmpty),
        profile    -> o.profile,
        toints     -> w.intO(o.toints),
        playTime   -> o.playTime,
        createdAt  -> o.createdAt,
        seenAt     -> o.seenAt,
        kid        -> w.boolO(o.kid),
        lang       -> o.lang,
        title      -> o.title,
        plan       -> o.plan.nonEmpty,
        totpSecret -> o.totpSecret,
        marks      -> o.marks.nonEmpty
      )
  }

  implicit val speakerHandler: BSONDocumentHandler[Speaker] = reactivemongo.api.bson.Macros.handler[Speaker]
  implicit val contactHandler: BSONDocumentHandler[Contact] = reactivemongo.api.bson.Macros.handler[Contact]

  private val firstRow: List[PerfType] = PerfType.standard

  private val secondRow: List[PerfType] =
    PerfType.all.filter(_.key == "ultraBullet") :::
      PerfType.variants.filter(p =>
        (p.category match {
          case Left(Right(v)) => v.gameLogic
          case _              => GameLogic.Chess()
        }) == GameLogic.Chess()
      )

  val topPerfTrophiesEnabled = false
}
