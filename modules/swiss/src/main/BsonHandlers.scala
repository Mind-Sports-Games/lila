package lila.swiss

import strategygames.{ Player => PlayerIndex, GameLogic }
import strategygames.variant.Variant
import strategygames.format.FEN
import reactivemongo.api.bson._
import scala.concurrent.duration._

import lila.db.BSON
import lila.db.dsl._
import lila.user.User
import strategygames.ClockConfig

object BsonHandlers {

  implicit val stratVariantHandler: BSONHandler[Variant] = stratVariantByKeyHandler
  implicit val clockHandler: BSONHandler[ClockConfig]    = clockConfigHandler
  implicit val swissPointsHandler: BSONHandler[Swiss.Points] =
    intAnyValHandler[Swiss.Points](_.double, Swiss.Points.apply)
  implicit val swissSBTieBreakHandler: BSONHandler[Swiss.SonnenbornBerger] =
    doubleAnyValHandler[Swiss.SonnenbornBerger](_.value, Swiss.SonnenbornBerger.apply)
  implicit val swissBHTieBreakHandler: BSONHandler[Swiss.Buchholz] =
    doubleAnyValHandler[Swiss.Buchholz](_.value, Swiss.Buchholz.apply)
  implicit val swissPerformanceHandler: BSONHandler[Swiss.Performance] =
    floatAnyValHandler[Swiss.Performance](_.value, Swiss.Performance.apply)
  implicit val swissScoreHandler: BSONHandler[Swiss.Score] =
    longAnyValHandler[Swiss.Score](_.value, Swiss.Score.apply)
  implicit val roundNumberHandler: BSONHandler[SwissRound.Number] =
    intAnyValHandler[SwissRound.Number](_.value, SwissRound.Number.apply)
  implicit val swissIdHandler: BSONHandler[Swiss.Id] = stringAnyValHandler[Swiss.Id](_.value, Swiss.Id.apply)
  implicit val playerIdHandler: BSONHandler[SwissPlayer.Id] =
    stringAnyValHandler[SwissPlayer.Id](_.value, SwissPlayer.Id.apply)

  implicit val playerHandler: BSON[SwissPlayer] = new BSON[SwissPlayer] {
    import SwissPlayer.Fields._
    def reads(r: BSON.Reader) =
      SwissPlayer(
        id = r.get[SwissPlayer.Id](id),
        swissId = r.get[Swiss.Id](swissId),
        userId = r str userId,
        rating = r int rating,
        inputRating = r intO inputRating,
        provisional = r boolD provisional,
        points = r.get[Swiss.Points](points),
        sbTieBreak = r.get[Swiss.SonnenbornBerger](sbTieBreak),
        bhTieBreak = r.getO[Swiss.Buchholz](bhTieBreak),
        performance = r.getO[Swiss.Performance](performance),
        score = r.get[Swiss.Score](score),
        absent = r.boolD(absent),
        byes = ~r.getO[Set[SwissRound.Number]](byes),
        disqualified = r.boolD(disqualified)
      )
    def writes(w: BSON.Writer, o: SwissPlayer) =
      $doc(
        id           -> o.id,
        swissId      -> o.swissId,
        userId       -> o.userId,
        rating       -> o.rating,
        inputRating  -> o.inputRating,
        provisional  -> w.boolO(o.provisional),
        points       -> o.points,
        sbTieBreak   -> o.sbTieBreak,
        bhTieBreak   -> o.bhTieBreak,
        performance  -> o.performance,
        score        -> o.score,
        absent       -> w.boolO(o.absent),
        byes         -> o.byes.some.filter(_.nonEmpty),
        disqualified -> w.boolO(o.disqualified)
      )
  }

  implicit val pairingStatusHandler: BSONHandler[SwissPairing.Status] =
    lila.db.dsl.quickHandler[SwissPairing.Status](
      {
        case BSONBoolean(true)  => Left(SwissPairing.Ongoing)
        case BSONInteger(index) => Right(PlayerIndex.fromP1(index == 0).some)
        case _                  => Right(none)
      },
      {
        case Left(_)        => BSONBoolean(true)
        case Right(Some(c)) => BSONInteger(c.fold(0, 1))
        case _              => BSONNull
      }
    )
  implicit val pairingMatchStatusHandler: BSONHandler[SwissPairing.MatchStatus] =
    lila.db.dsl.quickHandler[SwissPairing.MatchStatus](
      {
        case BSONBoolean(true) => Left(SwissPairing.Ongoing)
        case BSONArray(indexs) =>
          Right(
            indexs
              .map(i =>
                i match {
                  case BSONInteger(0) => None
                  case BSONInteger(1) => PlayerIndex.fromName("p1")
                  case BSONInteger(2) => PlayerIndex.fromName("p2")
                  case _              => None
                }
              )
              .toList
          )
        case _ => Right(List(none))
      },
      {
        case Left(_) => BSONBoolean(true)
        case Right(l) =>
          BSONArray(l.map { p =>
            p match {
              case Some(p) => BSONInteger(if (p.name == "p1") 1 else 2)
              case _       => BSONInteger(0)
            }
          })
        case _ => BSONNull
      }
    )
  implicit val pairingHandler: BSON[SwissPairing] = new BSON[SwissPairing] {
    import SwissPairing.Fields._
    def reads(r: BSON.Reader) =
      r.get[List[User.ID]](players) match {
        case List(w, b) => {
          val variant = r.getO[Variant]("v")
          SwissPairing(
            id = r str id,
            swissId = r.get[Swiss.Id](swissId),
            round = r.get[SwissRound.Number](round),
            p1 = w,
            p2 = b,
            bbpPairingP1 = r.getO[User.ID](bbpPairingP1) | w,
            status = r.getO[SwissPairing.Status](status) | Right(none),
            matchStatus = r.getO[SwissPairing.MatchStatus](matchStatus) | Right(List(none)),
            //TODO: we could summarise this data or omit it when its identical to matchStatus
            startPlayerWinners = r.getO[SwissPairing.MatchStatus](startPlayerWinners),
            // TODO: long term we may want to skip storing both of these fields
            //       in the case that it's not a multimatch to save on storage
            multiMatchGameIds = r.getsO[String](multiMatchGameIds),
            isMatchScore = r.getD[Boolean](isMatchScore),
            isBestOfX = r.getD[Boolean](isBestOfX),
            isPlayX = r.getD[Boolean](isPlayX),
            nbGamesPerRound = r.intO("gpr") getOrElse SwissBounds.defaultGamesPerRound,
            //TODO change default for this?
            openingFEN = r
              .getO[String](openingFEN)
              .map(fen => FEN(variant.map(_.gameLogic).getOrElse(GameLogic.Draughts()), fen)),
            variant = variant
          )
        }
        case _ => sys error "Invalid swiss pairing users"
      }
    def writes(w: BSON.Writer, o: SwissPairing) =
      $doc(
        id           -> o.id,
        swissId      -> o.swissId,
        round        -> o.round,
        players      -> o.players,
        bbpPairingP1 -> o.bbpPairingP1,
        status       -> o.status,
        matchStatus  -> o.matchStatus,
        //TODO: we could summarise this data or omit it when its identical to matchStatus
        startPlayerWinners -> o.startPlayerWinners,
        // TODO: long term we may want to skip storing both of these fields
        //       in the case that it's not a multimatch to save on storage
        multiMatchGameIds -> o.multiMatchGameIds,
        isMatchScore      -> o.isMatchScore,
        isBestOfX         -> o.isBestOfX,
        isPlayX           -> o.isPlayX,
        nbGamesPerRound   -> (o.nbGamesPerRound != SwissBounds.defaultGamesPerRound).option(o.nbGamesPerRound),
        openingFEN        -> o.openingFEN.map(_.value),
        variant           -> o.variant
      )
  }
  implicit val pairingGamesHandler: BSON[SwissPairingGameIds] = new BSON[SwissPairingGameIds] {
    import SwissPairing.Fields._
    def reads(r: BSON.Reader) =
      SwissPairingGameIds(
        id = r str id,
        multiMatchGameIds = r.getsO[String](multiMatchGameIds),
        isMatchScore = r.get[Boolean](isMatchScore),
        isBestOfX = r.get[Boolean](isBestOfX),
        isPlayX = r.get[Boolean](isPlayX),
        nbGamesPerRound = r.intO("gpr") getOrElse SwissBounds.defaultGamesPerRound,
        //TODO allow this to work for chess too?
        openingFEN = r.getO[String](openingFEN).map(fen => FEN(GameLogic.Draughts(), fen))
      )
    def writes(w: BSON.Writer, o: SwissPairingGameIds) =
      $doc(
        id                -> o.id,
        multiMatchGameIds -> o.multiMatchGameIds,
        isMatchScore      -> o.isMatchScore,
        isBestOfX         -> o.isBestOfX,
        isPlayX           -> o.isPlayX,
        nbGamesPerRound   -> (o.nbGamesPerRound != SwissBounds.defaultGamesPerRound).option(o.nbGamesPerRound),
        openingFEN        -> o.openingFEN.map(_.value)
      )
  }

  import SwissCondition.BSONHandlers.AllBSONHandler

  implicit val settingsHandler: BSON[Swiss.Settings] = new BSON[Swiss.Settings] {
    def reads(r: BSON.Reader) =
      Swiss.Settings(
        nbRounds = r.get[Int]("n"),
        rated = r.boolO("r") | true,
        mcmahon = r.boolO("m") | false,
        mcmahonCutoff = r.getD[String]("mc"),
        handicapped = r.boolO("h") | false,
        backgammonPoints = r.intO("bp"),
        inputPlayerRatings = r.getD[String]("ipr"),
        isMatchScore = r.boolO("ms") | false,
        isBestOfX = r.boolO("x") | false,
        isPlayX = r.boolO("px") | false,
        nbGamesPerRound = r.intO("gpr") getOrElse SwissBounds.defaultGamesPerRound,
        description = r.strO("d"),
        useDrawTables = r.boolO("dt") | false,
        usePerPairingDrawTables = r.boolO("pdt") | false,
        position = r.getO[FEN]("f"),
        chatFor = r.intO("c") | Swiss.ChatFor.default,
        roundInterval = (r.intO("i") | 60).seconds,
        halfwayBreak = (r.intO("hb") | 0).seconds,
        password = r.strO("p"),
        conditions = r.getO[SwissCondition.All]("o") getOrElse SwissCondition.All.empty,
        forbiddenPairings = r.getD[String]("fp"),
        medleyVariants = r.getO[List[Variant]]("mv"),
        minutesBeforeStartToJoin = r.intO("mbs")
      )
    def writes(w: BSON.Writer, s: Swiss.Settings) =
      $doc(
        "n"   -> s.nbRounds,
        "r"   -> (!s.rated).option(false),
        "m"   -> (s.mcmahon).option(true),
        "mc"  -> s.mcmahonCutoff.some.filter(_.nonEmpty),
        "bp"  -> s.backgammonPoints,
        "h"   -> (s.handicapped).option(true),
        "ipr" -> s.inputPlayerRatings.some.filter(_.nonEmpty),
        "ms"  -> s.isMatchScore,
        "x"   -> s.isBestOfX,
        "px"  -> s.isPlayX,
        "gpr" -> (s.nbGamesPerRound != SwissBounds.defaultGamesPerRound).option(s.nbGamesPerRound),
        "d"   -> s.description,
        "dt"  -> s.useDrawTables,
        "pdt" -> s.usePerPairingDrawTables,
        "f"   -> s.position,
        "c"   -> (s.chatFor != Swiss.ChatFor.default).option(s.chatFor),
        "i"   -> s.roundInterval.toSeconds.toInt,
        "hb"  -> s.halfwayBreak.toSeconds.toInt,
        "p"   -> s.password,
        "o"   -> s.conditions.ifNonEmpty,
        "fp"  -> s.forbiddenPairings.some.filter(_.nonEmpty),
        "mv"  -> s.medleyVariants,
        "mbs" -> s.minutesBeforeStartToJoin
      )
  }

  implicit val swissHandler: BSONDocumentHandler[Swiss] = Macros.handler[Swiss]

  // "featurable" mostly means that the tournament isn't over yet
  def addFeaturable(s: Swiss) =
    swissHandler.writeTry(s).get ++ {
      s.isNotFinished ?? $doc(
        "featurable" -> true,
        "garbage"    -> s.unrealisticSettings.option(true)
      )
    }

  import Swiss.IdName
  implicit val SwissIdNameBSONHandler: BSONDocumentHandler[IdName] = Macros.handler[IdName]
}
