package lila.tournament

import strategygames.format.FEN
import strategygames.variant.Variant
import strategygames.{ GameLogic, Mode }
import reactivemongo.api.bson._

import lila.db.BSON
import lila.db.dsl._
import lila.rating.PerfType
import lila.user.User.playstrategyId

object BSONHandlers {

  implicit val stratVariantHandler = stratVariantByKeyHandler

  implicit private[tournament] val statusBSONHandler = tryHandler[Status](
    { case BSONInteger(v) => Status(v) toTry s"No such status: $v" },
    x => BSONInteger(x.id)
  )

  implicit private[tournament] val scheduleFreqHandler = tryHandler[Schedule.Freq](
    { case BSONString(v) => Schedule.Freq(v) toTry s"No such freq: $v" },
    x => BSONString(x.name)
  )

  implicit private[tournament] val scheduleSpeedHandler = tryHandler[Schedule.Speed](
    { case BSONString(v) => Schedule.Speed(v) toTry s"No such speed: $v" },
    x => BSONString(x.key)
  )

  implicit val scheduleWriter = BSONWriter[Schedule](s =>
    $doc(
      "freq"  -> s.freq,
      "speed" -> s.speed
    )
  )

  implicit val tournamentClockBSONHandler = clockConfigHandler

  implicit private val spotlightBSONHandler = Macros.handler[Spotlight]

  implicit val battleBSONHandler = Macros.handler[TeamBattle]

  implicit private val leaderboardRatio =
    BSONIntegerHandler.as[LeaderboardApi.Ratio](
      i => LeaderboardApi.Ratio(i.toDouble / 100_000),
      r => (r.value * 100_000).toInt
    )

  import Condition.BSONHandlers.AllBSONHandler

  implicit val tournamentHandler = new BSON[Tournament] {
    def reads(r: BSON.Reader) = {
      val lib     = GameLogic(r.intD("lib"))
      val variant = r.intO("variant").fold[Variant](Variant.default(lib))(v => Variant.orDefault(lib, v))
      val position: Option[FEN] =
        r.getO[FEN]("fen").filterNot(_.initial) orElse
          r.strO("eco").flatMap(Thematic.byEco).map(f => FEN.wrap(f.fen)) // for BC
      val startsAt   = r date "startsAt"
      val conditions = r.getO[Condition.All]("conditions") getOrElse Condition.All.empty
      val mVariants  = r.getO[List[Variant]]("mVariants")
      val mIntervals = r.getO[List[Int]]("mIntervals")
      val medleyVariantsAndIntervals: Option[List[(Variant, Int)]] = mVariants.map(_.zipWithIndex.map {
        case (v, i) =>
          (v, mIntervals.fold(0)(_.lift(i).getOrElse(0)))
      })
      Tournament(
        id = r str "_id",
        name = r str "name",
        status = r.get[Status]("status"),
        clock = r.get[strategygames.ClockConfig]("clock"),
        minutes = r int "minutes",
        variant = variant,
        medleyVariantsAndIntervals = medleyVariantsAndIntervals,
        medleyMinutes = r.intO("mMinutes"),
        position = position,
        mode = r.intO("mode") flatMap Mode.apply getOrElse Mode.Rated,
        password = r.strO("password"),
        conditions = r.getO[Condition.All]("conditions") getOrElse Condition.All.empty,
        teamBattle = r.getO[TeamBattle]("teamBattle"),
        noBerserk = r boolD "noBerserk",
        noStreak = r boolD "noStreak",
        statusScoring = r boolO "statusScoring" getOrElse false,
        schedule = for {
          doc   <- r.getO[Bdoc]("schedule")
          freq  <- doc.getAsOpt[Schedule.Freq]("freq")
          speed <- doc.getAsOpt[Schedule.Speed]("speed")
        } yield Schedule(freq, speed, variant, position, startsAt, None, None, conditions),
        nbPlayers = r int "nbPlayers",
        createdAt = r date "createdAt",
        createdBy = r strO "createdBy" getOrElse playstrategyId,
        startsAt = startsAt,
        winnerId = r strO "winner",
        featuredId = r strO "featured",
        spotlight = r.getO[Spotlight]("spotlight"),
        trophy1st = r strO "trophy1st",
        trophy2nd = r strO "trophy2nd",
        trophy3rd = r strO "trophy3rd",
        trophyExpiryDays = r intO "trophyExpiryDays",
        botsAllowed = r boolD "botsAllowed",
        description = r strO "description",
        hasChat = r boolO "chat" getOrElse true
      )
    }
    def writes(w: BSON.Writer, o: Tournament) =
      $doc(
        "_id"              -> o.id,
        "name"             -> o.name,
        "status"           -> o.status,
        "clock"            -> o.clock,
        "minutes"          -> o.minutes,
        "lib"              -> o.variant.gameLogic.id,
        "variant"          -> o.variant.some.filterNot(_.key == "standard").map(_.id),
        "mVariants"        -> o.medleyVariants,
        "mMinutes"         -> o.medleyMinutes,
        "mIntervals"       -> o.medleyIntervalSeconds,
        "fen"              -> o.position.map(_.value),
        "mode"             -> o.mode.some.filterNot(_.rated).map(_.id),
        "password"         -> o.password,
        "conditions"       -> o.conditions.ifNonEmpty,
        "forTeams"         -> o.conditions.teamMember.map(_.teamId),
        "teamBattle"       -> o.teamBattle,
        "noBerserk"        -> w.boolO(o.noBerserk),
        "noStreak"         -> w.boolO(o.noStreak),
        "statusScoring"    -> o.statusScoring.option(true),
        "schedule"         -> o.schedule,
        "nbPlayers"        -> o.nbPlayers,
        "createdAt"        -> w.date(o.createdAt),
        "createdBy"        -> o.nonPlayStrategyCreatedBy,
        "startsAt"         -> w.date(o.startsAt),
        "winner"           -> o.winnerId,
        "featured"         -> o.featuredId,
        "spotlight"        -> o.spotlight,
        "trophy1st"        -> o.trophy1st,
        "trophy2nd"        -> o.trophy2nd,
        "trophy3rd"        -> o.trophy3rd,
        "trophyExpiryDays" -> o.trophyExpiryDays,
        "botsAllowed"      -> o.botsAllowed,
        "description"      -> o.description,
        "chat"             -> (!o.hasChat).option(false)
      )
  }

  implicit val playerBSONHandler = new BSON[Player] {
    def reads(r: BSON.Reader) =
      Player(
        _id = r str "_id",
        tourId = r str "tid",
        userId = r str "uid",
        rating = r int "r",
        provisional = r boolD "pr",
        isBot = r boolD "b",
        withdraw = r boolD "w",
        disqualified = r boolD "dq",
        score = r intD "s",
        fire = r boolD "f",
        performance = r intD "e",
        team = r strO "t"
      )
    def writes(w: BSON.Writer, o: Player) =
      $doc(
        "_id" -> o._id,
        "tid" -> o.tourId,
        "uid" -> o.userId,
        "r"   -> o.rating,
        "pr"  -> w.boolO(o.provisional),
        "b"   -> w.boolO(o.isBot),
        "w"   -> w.boolO(o.withdraw),
        "dq"  -> w.boolO(o.disqualified),
        "s"   -> w.intO(o.score),
        "m"   -> o.magicScore,
        "f"   -> w.boolO(o.fire),
        "e"   -> o.performance,
        "t"   -> o.team
      )
  }

  implicit val pairingHandler = new BSON[Pairing] {
    def reads(r: BSON.Reader) = {
      val users = r strsD "u"
      val user1 = users.headOption err "tournament pairing first user"
      val user2 = users lift 1 err "tournament pairing second user"
      Pairing(
        id = r str "_id",
        tourId = r str "tid",
        status = strategygames.Status(r int "s") err "tournament pairing status",
        user1 = user1,
        user2 = user2,
        winner = r boolO "w" map {
          case true => user1
          case _    => user2
        },
        turns = r intO "t",
        berserk1 = r.intO("b1").fold(r.boolD("b1"))(1 ==), // it used to be int = 0/1
        berserk2 = r.intO("b2").fold(r.boolD("b2"))(1 ==)
      )
    }
    def writes(w: BSON.Writer, o: Pairing) =
      $doc(
        "_id" -> o.id,
        "tid" -> o.tourId,
        "s"   -> o.status.id,
        "u"   -> BSONArray(o.user1, o.user2),
        "w"   -> o.winner.map(o.user1 ==),
        "t"   -> o.turns,
        "b1"  -> w.boolO(o.berserk1),
        "b2"  -> w.boolO(o.berserk2)
      )
  }

  implicit val leaderboardEntryHandler = new BSON[LeaderboardApi.Entry] {
    def reads(r: BSON.Reader) =
      LeaderboardApi.Entry(
        id = r str "_id",
        userId = r str "u",
        tourId = r str "t",
        nbGames = r int "g",
        score = r int "s",
        rank = r int "r",
        rankRatio = r.get[LeaderboardApi.Ratio]("w"),
        metaPoints = r intO "mp",
        shieldKey = r strO "k",
        freq = r intO "f" flatMap Schedule.Freq.byId,
        speed = r intO "p" flatMap Schedule.Speed.byId,
        perf = PerfType.byId get r.int("v") err "Invalid leaderboard perf",
        date = r date "d"
      )

    def writes(w: BSON.Writer, o: LeaderboardApi.Entry) =
      $doc(
        "_id" -> o.id,
        "u"   -> o.userId,
        "t"   -> o.tourId,
        "g"   -> o.nbGames,
        "s"   -> o.score,
        "r"   -> o.rank,
        "w"   -> o.rankRatio,
        "mp"  -> o.metaPoints,
        "k"   -> o.shieldKey,
        "f"   -> o.freq.map(_.id),
        "p"   -> o.speed.map(_.id),
        "v"   -> o.perf.id,
        "d"   -> w.date(o.date)
      )
  }

  import LeaderboardApi.ChartData.AggregationResult
  implicit val leaderboardAggregationResultBSONHandler = Macros.handler[AggregationResult]

  implicit val shieldTableEntryHandler = new BSON[ShieldTableApi.ShieldTableEntry] {
    def reads(r: BSON.Reader) =
      ShieldTableApi.ShieldTableEntry(
        id = r str "_id",
        userId = r str "u",
        category = ShieldTableApi.Category.getFromId(r int "c"),
        points = r int "p"
      )

    def writes(w: BSON.Writer, o: ShieldTableApi.ShieldTableEntry) =
      $doc(
        "_id" -> o.id,
        "u"   -> o.userId,
        "c"   -> o.category.id,
        "p"   -> o.points
      )
  }

}
