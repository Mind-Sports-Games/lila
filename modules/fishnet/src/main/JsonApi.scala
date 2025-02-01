package lila.fishnet

import strategygames.format.{ FEN, LexicalUci, Uci, UciDump }
import strategygames.variant.Variant
import strategygames.{ GameFamily, GameLogic }
import org.joda.time.DateTime
import play.api.libs.json._

import lila.common.Json._
import lila.common.{ IpAddress, Maths }
import lila.fishnet.{ Work => W }
import lila.tree.Eval.JsonHandlers._
import lila.tree.Eval.{ Cp, Mate }
import akka.actor.typed.PostStop

object JsonApi {

  sealed trait Request {
    val fishnet: Request.Fishnet

    def instance(ip: IpAddress) = Client.Instance(fishnet.version, ip, DateTime.now)
  }

  object Request {

    sealed trait Result

    case class Fishnet(
        version: Client.Version,
        python: Option[Client.Python],
        apikey: Client.Key
    )

    case class Stockfish(
        flavor: Option[String]
    ) {
      def isNnue = flavor.has("nnue")
    }

    case class Acquire(
        fishnet: Fishnet
    ) extends Request

    case class PostAnalysisLexicalUci(
        fishnet: Fishnet,
        stockfish: Stockfish,
        analysis: List[Option[Evaluation.OrSkipped[LexicalUci]]]
    ) extends Request
        with Result {

      def toUci(variant: Variant) =
        PostAnalysisUci(
          fishnet,
          stockfish,
          analysis.map(o =>
            o.map({
              case Right(e) => Right(Evaluation.toUci(e, variant))
              case Left(s)  => Left(s)
            })
          )
        )
    }

    case class PostAnalysisUci(
        fishnet: Fishnet,
        stockfish: Stockfish,
        analysis: List[Option[Evaluation.OrSkipped[Uci]]]
    ) extends Request
        with Result {

      def completeOrPartial =
        if (analysis.headOption.??(_.isDefined)) CompleteAnalysis(fishnet, stockfish, analysis.flatten)
        else PartialAnalysis(fishnet, stockfish, analysis)
    }

    case class CompleteAnalysis(
        fishnet: Fishnet,
        stockfish: Stockfish,
        analysis: List[Evaluation.OrSkipped[Uci]]
    ) {

      def evaluations = analysis.collect { case Right(e) => e }

      def medianNodes =
        Maths.median {
          evaluations
            .withFilter(e => !(e.mateFound || e.deadDraw))
            .flatMap(_.nodes)
        }

      // fishnet 2.x analysis is never weak in this sense. It is either exactly
      // the same as analysis provided by any other instance, or failed.
      def strong = stockfish.flavor.isDefined || medianNodes.fold(true)(_ > Evaluation.legacyAcceptableNodes)
      def weak   = !strong
    }

    case class PartialAnalysis(
        fishnet: Fishnet,
        stockfish: Stockfish,
        analysis: List[Option[Evaluation.OrSkipped[Uci]]]
    )

    // Because the incoming json won't know what variant or gamefamily
    // the initial parsing is _just_ into a list of strings.
    // To handle that, i've made this generic and some parts of the code
    // will deal with Evaluation[String], and other parts will deal with
    // Evaluation[Uci]
    case class Evaluation[T](
        pv: List[T],
        score: Evaluation.Score,
        time: Option[Int],
        nodes: Option[Int],
        nps: Option[Int],
        depth: Option[Int]
    ) {
      val cappedNps = nps.map(_ min Evaluation.npsCeil)

      val cappedPv = pv take lila.analyse.Info.LineMaxTurns

      def isCheckmate = score.mate has Mate(0)
      def mateFound   = score.mate.isDefined
      def deadDraw    = score.cp has Cp(0)
    }

    object Evaluation {

      object Skipped

      type OrSkipped[T] = Either[Skipped.type, Evaluation[T]]

      case class Score(cp: Option[Cp], mate: Option[Mate]) {
        def invert                  = copy(cp.map(_.invert), mate.map(_.invert))
        def invertIf(cond: Boolean) = if (cond) invert else this
      }

      val npsCeil = 10_000_000

      private val legacyDesiredNodes = 3_000_000
      val legacyAcceptableNodes      = legacyDesiredNodes * 0.9

      def toUci(eval: Evaluation[LexicalUci], variant: Variant): Evaluation[Uci] =
        Evaluation[Uci](
          UciDump
            .fromFishnetUci(variant, eval.pv)
            .flatMap(lexicalUci => Uci(variant.gameLogic, variant.gameFamily, lexicalUci.uci)),
          eval.score,
          eval.time,
          eval.nodes,
          eval.nps,
          eval.depth
        )
    }
  }

  case class UciGame(
      game_id: String,
      position: FEN,
      variant: Variant,
      moves: List[Uci]
  )

  case class Game(
      game_id: String,
      position: FEN,
      variant: Variant,
      moves: String
  ) {
    def uciMoves = ~(Uci.readList(variant.gameLogic, variant.gameFamily, moves))
    def toUci    = UciGame(game_id, position, variant, uciMoves)
  }

  def fromGame(g: W.Game) =
    Game(
      game_id = if (g.studyId.isDefined) "" else g.id,
      position = g.initialFen match {
        case Some(initialFen) => initialFen
        case None             => g.variant.initialFen
      },
      variant = g.variant,
      moves = g.moves
    )

  sealed trait Work {
    val id: String
    val game: Game
  }

  case class Analysis(
      id: String,
      game: Game,
      nodes: Int,
      skipPositions: List[Int]
  ) extends Work

  def analysisFromWork(nodes: Int)(m: Work.Analysis) =
    Analysis(
      id = m.id.value,
      game = fromGame(m.game),
      nodes = nodes,
      skipPositions = m.skipPositions
    )

  object readers {
    import play.api.libs.functional.syntax._
    implicit val ClientVersionReads: Reads[Client.Version]   = Reads.of[String].map(Client.Version(_))
    implicit val ClientPythonReads: Reads[Client.Python]     = Reads.of[String].map(Client.Python(_))
    implicit val ClientKeyReads: Reads[Client.Key]           = Reads.of[String].map(Client.Key(_))
    implicit val StockfishReads: Reads[Request.Stockfish]    = Json.reads[Request.Stockfish]
    implicit val FishnetReads: Reads[Request.Fishnet]        = Json.reads[Request.Fishnet]
    implicit val AcquireReads: Reads[Request.Acquire]        = Json.reads[Request.Acquire]
    implicit val ScoreReads: Reads[Request.Evaluation.Score] = Json.reads[Request.Evaluation.Score]
    implicit val uciListReadsLexicalUcis: Reads[Array[LexicalUci]] = Reads.of[String] map { str =>
      str.split(" ").flatMap(LexicalUci.apply)
    }

    implicit val EvaluationReads: Reads[Request.Evaluation[LexicalUci]] = (
      (__ \ "pv").readNullable[String].map(~_).map(_.split(" ").flatMap(LexicalUci.apply).toList) and
        (__ \ "score").read[Request.Evaluation.Score] and
        (__ \ "time").readNullable[Int] and
        (__ \ "nodes").readNullable[Long].map(_.map(_.toSaturatedInt)) and
        (__ \ "nps").readNullable[Long].map(_.map(_.toSaturatedInt)) and
        (__ \ "depth").readNullable[Int]
    )((moves, score, time, nodes, nps, depth) =>
      Request.Evaluation[LexicalUci](moves, score, time, nodes, nps, depth)
    )
    implicit val EvaluationOptionReads: Reads[Option[Request.Evaluation.OrSkipped[LexicalUci]]] =
      Reads[Option[Request.Evaluation.OrSkipped[LexicalUci]]] {
        case JsNull => JsSuccess(None)
        case obj =>
          if (~(obj boolean "skipped")) JsSuccess(Left(Request.Evaluation.Skipped).some)
          else EvaluationReads reads obj map Right.apply map some
      }
    implicit val PostAnalysisReads: Reads[Request.PostAnalysisLexicalUci] =
      Json.reads[Request.PostAnalysisLexicalUci]
  }

  object writers {
    implicit val VariantWrites: Writes[Variant] = Writes[Variant] { v => JsString(v.fishnetKey) }
    implicit val GameWrites: Writes[UciGame] = Writes[UciGame] { g =>
      Json.obj(
        "game_id"  -> g.game_id,
        "position" -> FEN.fishnetFen(g.variant)(g.position),
        "variant"  -> g.variant,
        "moves"    -> UciDump.fishnetUci(g.variant, g.moves)
      )
    }
    implicit val WorkIdWrites: Writes[Work.Id] = Writes[Work.Id] { id =>
      JsString(id.value)
    }
    implicit val WorkWrites: OWrites[Work] = OWrites[Work] { work =>
      (work match {
        case a: Analysis =>
          Json.obj(
            "work" -> Json.obj(
              "type" -> "analysis",
              "id"   -> a.id,
              "nodes" -> Json.obj(
                "sf15"      -> a.nodes,
                "sf14"      -> a.nodes * 14 / 10,
                "nnue"      -> a.nodes * 14 / 10, // bc fishnet <= 2.3.4
                "classical" -> a.nodes * 28 / 10
              ),
              "timeout" -> Cleaner.timeoutPerPly.toMillis
            ),
            "skipPositions" -> a.skipPositions
          )
      }) ++ Json.toJson(work.game.toUci).as[JsObject]
    }
  }
}
