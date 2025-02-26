package lila.insight

import reactivemongo.api.bson._

import strategygames.chess.opening.{ Ecopening, EcopeningDB }
import strategygames.{ Player => PlayerIndex, GameFamily, GameLogic, Role }
import lila.db.BSON
import lila.db.dsl._
import lila.rating.BSONHandlers.perfTypeIdHandler
import lila.rating.PerfType

private object BSONHandlers {

  implicit val EcopeningBSONHandler: BSONHandler[Ecopening] = tryHandler[Ecopening](
    { case BSONString(v) => EcopeningDB.allByEco get v toTry s"Invalid ECO $v" },
    e => BSONString(e.eco)
  )
  implicit val RelativeStrengthBSONHandler: BSONHandler[RelativeStrength] = tryHandler[RelativeStrength](
    { case BSONInteger(v) => RelativeStrength.byId get v toTry s"Invalid relative strength $v" },
    e => BSONInteger(e.id)
  )
  implicit val ResultBSONHandler: BSONHandler[Result] = tryHandler[Result](
    { case BSONInteger(v) => Result.byId get v toTry s"Invalid result $v" },
    e => BSONInteger(e.id)
  )

  implicit val PhaseBSONHandler: BSONHandler[Phase] = tryHandler[Phase](
    { case BSONInteger(v) => Phase.byId get v toTry s"Invalid phase $v" },
    e => BSONInteger(e.id)
  )
  implicit val RoleBSONHandler: BSONHandler[Role] = tryHandler[Role](
    { case BSONString(r) =>
      r.split(":") match {
        case Array(lib, r) =>
          //require gf for fairy as roles are different, all other gamelogic currently dont need this
          if (lib.toInt == 2)
            Role.allByForsyth(
              GameLogic.FairySF(),
              GameFamily(lib.toInt)
            ) get r.head toTry s"Invalid role $r"
          else Role.allByForsyth(GameLogic(lib.toInt)) get r.head toTry s"Invalid role $r"
        case _ => sys.error("role not correctly encoded")
      }
    },
    e =>
      e match {
        case Role.ChessRole(r)              => BSONString(s"0:${r.forsyth.toString}")
        case Role.ChessPromotableRole(r)    => BSONString(s"0:${r.forsyth.toString}")
        case Role.DraughtsRole(r)           => BSONString(s"1:${r.forsyth.toString}")
        case Role.DraughtsPromotableRole(r) => BSONString(s"1:${r.forsyth.toString}")
        case Role.FairySFRole(r)            => BSONString(s"${r.gameFamily.id}:${r.forsyth.toString}")
        case Role.FairySFPromotableRole(r)  => BSONString(s"${r.gameFamily.id}:${r.forsyth.toString}")
        case Role.SamuraiRole(r)            => BSONString(s"${r.gameFamily.id}:${r.forsyth.toString}")
        case Role.TogyzkumalakRole(r)       => BSONString(s"${r.gameFamily.id}:${r.forsyth.toString}")
        case Role.GoRole(r)                 => BSONString(s"${r.gameFamily.id}:${r.forsyth.toString}")
        case Role.BackgammonRole(r)         => BSONString(s"${r.gameFamily.id}:${r.forsyth.toString}")
        case Role.AbaloneRole(r)            => BSONString(s"${r.gameFamily.id}:${r.forsyth.toString}")
      }
  )
  implicit val TerminationBSONHandler: BSONHandler[Termination] = tryHandler[Termination](
    { case BSONInteger(v) => Termination.byId get v toTry s"Invalid termination $v" },
    e => BSONInteger(e.id)
  )
  implicit val MovetimeRangeBSONHandler: BSONHandler[MovetimeRange] = tryHandler[MovetimeRange](
    { case BSONInteger(v) => MovetimeRange.byId get v toTry s"Invalid movetime range $v" },
    e => BSONInteger(e.id)
  )
  implicit val CastlingBSONHandler: BSONHandler[Castling] = tryHandler[Castling](
    { case BSONInteger(v) => Castling.byId get v toTry s"Invalid Castling $v" },
    e => BSONInteger(e.id)
  )
  implicit val MaterialRangeBSONHandler: BSONHandler[MaterialRange] = tryHandler[MaterialRange](
    { case BSONInteger(v) => MaterialRange.byId get v toTry s"Invalid material range $v" },
    e => BSONInteger(e.id)
  )
  implicit val QueenTradeBSONHandler: BSONHandler[QueenTrade] =
    BSONBooleanHandler.as[QueenTrade](QueenTrade.apply, _.id)

  private val BSONBooleanNullHandler = quickHandler[Boolean](
    { case BSONBoolean(v) => v; case BSONNull => false },
    v => if (v) BSONBoolean(true) else BSONNull
  )

  implicit val BlurBSONHandler: BSONHandler[Blur] = BSONBooleanNullHandler.as[Blur](Blur.apply, _.id)

  implicit val TimeVarianceBSONHandler: BSONHandler[TimeVariance] = BSONIntegerHandler.as[TimeVariance](
    i => TimeVariance(i.toFloat / TimeVariance.intFactor),
    v => (v.id * TimeVariance.intFactor).toInt
  )

  implicit val CplRangeBSONHandler: BSONHandler[CplRange] = tryHandler[CplRange](
    { case BSONInteger(v) => CplRange.byId get v toTry s"Invalid CPL range $v" },
    e => BSONInteger(e.cpl)
  )

  implicit val DateRangeBSONHandler: BSONDocumentHandler[DateRange] = Macros.handler[lila.insight.DateRange]

  implicit val PeriodBSONHandler: BSONHandler[Period] = intIsoHandler(
    lila.common.Iso.int[Period](Period.apply, _.days)
  )

  implicit def MoveBSONHandler: BSON[InsightMove] =
    new BSON[InsightMove] {
      def reads(r: BSON.Reader) =
        InsightMove(
          phase = r.get[Phase]("p"),
          tenths = r.get[Int]("t"),
          role = r.get[Role]("r"),
          eval = r.intO("e"),
          mate = r.intO("m"),
          cpl = r.intO("c"),
          material = r.int("i"),
          opportunism = r.boolO("o"),
          luck = r.boolO("l"),
          blur = r.boolD("b"),
          timeCv = r.intO("v").map(v => v.toFloat / TimeVariance.intFactor)
        )
      def writes(w: BSON.Writer, b: InsightMove) =
        BSONDocument(
          "p" -> b.phase,
          "t" -> b.tenths,
          "r" -> b.role,
          "e" -> b.eval,
          "m" -> b.mate,
          "c" -> b.cpl,
          "i" -> b.material,
          "o" -> b.opportunism,
          "l" -> b.luck,
          "b" -> w.boolO(b.blur),
          "v" -> b.timeCv.map(v => (v * TimeVariance.intFactor).toInt)
        )
    }

  implicit def EntryBSONHandler: BSON[InsightEntry] =
    new BSON[InsightEntry] {
      import InsightEntry.BSONFields._
      def reads(r: BSON.Reader) =
        InsightEntry(
          id = r.str(id),
          number = r.int(number),
          userId = r.str(userId),
          playerIndex = r.get[PlayerIndex](playerIndex),
          perf = r.get[PerfType](perf),
          eco = r.getO[Ecopening](eco),
          myCastling = r.get[Castling](myCastling),
          opponentRating = r.int(opponentRating),
          opponentStrength = r.get[RelativeStrength](opponentStrength),
          opponentCastling = r.get[Castling](opponentCastling),
          moves = r.get[List[InsightMove]](moves),
          queenTrade = r.get[QueenTrade](queenTrade),
          result = r.get[Result](result),
          termination = r.get[Termination](termination),
          ratingDiff = r.int(ratingDiff),
          analysed = r.boolD(analysed),
          provisional = r.boolD(provisional),
          date = r.date(date)
        )
      def writes(w: BSON.Writer, e: InsightEntry) =
        BSONDocument(
          id               -> e.id,
          number           -> e.number,
          userId           -> e.userId,
          playerIndex      -> e.playerIndex,
          perf             -> e.perf,
          eco              -> e.eco,
          myCastling       -> e.myCastling,
          opponentRating   -> e.opponentRating,
          opponentStrength -> e.opponentStrength,
          opponentCastling -> e.opponentCastling,
          moves            -> e.moves,
          queenTrade       -> e.queenTrade,
          result           -> e.result,
          termination      -> e.termination,
          ratingDiff       -> e.ratingDiff,
          analysed         -> w.boolO(e.analysed),
          provisional      -> w.boolO(e.provisional),
          date             -> e.date
        )
    }
}
