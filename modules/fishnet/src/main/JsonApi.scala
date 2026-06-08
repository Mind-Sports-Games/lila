package lila.fishnet

import strategygames.format.{ FEN, LexicalUci, Uci, UciDump }
import strategygames.variant.Variant
import org.joda.time.DateTime
import play.api.libs.json.*

import lila.common.Json.*
import lila.common.{ IpAddress, Maths }
import lila.fishnet.Work as W
import lila.tree.Eval.JsonHandlers.*
import lila.tree.Eval.{ Cp, Mate }

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
        apikey: Client.Key,
        // Variant (or family) keys this client can analyse. Absent or empty means
        // default routing: chess + fairysf only, never backgammon. See FishnetVariants.
        variants: Option[List[String]] = None
    )

    case class Stockfish(
        flavor: Option[String]
    ) {
      def isNnue = flavor.contains("nnue")
    }

    // Whole-game backgammon analysis, posted once by the gnubg-backed mindcube
    // worker. Maps 1:1 onto lila.analyse's backgammon model: gnubg's OWN per-player
    // error rate, luck and ratings, plus every candidate play it evaluated.
    case class EngineMeta(name: String, eval: String)

    case class BgProbsPost(
        win:            Double,
        winGammon:      Double,
        winBackgammon:  Double,
        lose:           Double,
        loseGammon:     Double,
        loseBackgammon: Double
    ) {
      def toModel =
        lila.analyse.BgProbabilities(win, winGammon, winBackgammon, lose, loseGammon, loseBackgammon)
    }

    case class BgCandPost(
        rank:          Int,
        evaluator:     String,
        play:          String,
        equity:        Double,
        equityDelta:   Option[Double],
        probabilities: BgProbsPost,
        evalClass:     Option[String],
        played:        Boolean
    ) {
      def toModel =
        lila.analyse.BgCandidate(rank, evaluator, play, equity, equityDelta, probabilities.toModel, evalClass, played)
    }

    case class BgMovePost(
        number:       Int,
        player:       String,
        kind:         String,
        dice:         Option[String],
        action:       String,
        bestAction:   Option[String],
        playedEquity: Option[Double],
        bestEquity:   Option[Double],
        rollLuck:     Option[Double],
        cubeAdvice:   Option[String],
        candidates:   List[BgCandPost]
    ) {
      def toModel =
        lila.analyse.BgMove(number, player, kind, dice, action, bestAction, playedEquity, bestEquity, rollLuck, cubeAdvice, candidates.map(_.toModel))
    }

    case class BgStatsPost(
        player:           String,
        chequerErrorRate: Option[Double],
        cubeErrorRate:    Option[Double],
        overallErrorRate: Option[Double],
        snowieErrorRate:  Option[Double],
        luckTotalEmg:     Option[Double],
        luckRateEmg:      Option[Double],
        chequerRating:    Option[String],
        cubeRating:       Option[String],
        overallRating:    Option[String],
        luckRating:       Option[String]
    ) {
      def toModel =
        lila.analyse.BgPlayerStats(player, chequerErrorRate, cubeErrorRate, overallErrorRate, snowieErrorRate, luckTotalEmg, luckRateEmg, chequerRating, cubeRating, overallRating, luckRating)
    }

    case class BgWinnerPost(player: String, points: Int, winType: String) {
      def toModel = lila.analyse.BgWinner(player, points, winType)
    }

    case class BgGamePost(
        number: Int,
        winner: Option[BgWinnerPost],
        stats:  List[BgStatsPost],
        moves:  List[BgMovePost]
    ) {
      def toModel =
        lila.analyse.BgGame(number, winner.map(_.toModel), stats.map(_.toModel), moves.map(_.toModel))
    }

    case class BackgammonPost(player1: String, player2: String, games: List[BgGamePost]) {
      def toGames: List[lila.analyse.BgGame] = games.map(_.toModel)
    }

    case class Acquire(
        fishnet: Fishnet
    ) extends Request

    case class PostAnalysisLexicalUci(
        fishnet: Fishnet,
        stockfish: Stockfish,
        analysis: List[Option[Evaluation.OrSkipped[LexicalUci]]],
        engine: Option[EngineMeta] = None,
        backgammon: Option[BackgammonPost] = None
    ) extends Request
        with Result {

      def toUci(variant: Variant) =
        PostAnalysisUci(
          fishnet,
          stockfish,
          analysis.map(o =>
            o.map {
              case Right(e) => Right(Evaluation.toUci(e, variant))
              case Left(s)  => Left(s)
            }
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
        if (analysis.headOption.so(_.isDefined)) CompleteAnalysis(fishnet, stockfish, analysis.flatten)
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

      def isCheckmate = score.mate.contains(Mate(0))
      def mateFound   = score.mate.isDefined
      def deadDraw    = score.cp.contains(Cp(0))
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
    def uciMoves = ~Uci.readList(variant.gameLogic, variant.gameFamily, moves)
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
      skipPositions: List[Int],
      backgammon: Option[W.BgWork] = None
  ) extends Work

  def analysisFromWork(nodes: Int)(m: Work.Analysis) =
    Analysis(
      id = m.id.value,
      game = fromGame(m.game),
      nodes = nodes,
      skipPositions = m.skipPositions,
      backgammon = m.game.backgammon
    )

  object readers {
    import play.api.libs.functional.syntax.*
    implicit val ClientVersionReads: Reads[Client.Version]         = Reads.of[String].map(Client.Version(_))
    implicit val ClientPythonReads: Reads[Client.Python]           = Reads.of[String].map(Client.Python(_))
    implicit val ClientKeyReads: Reads[Client.Key]                 = Reads.of[String].map(Client.Key(_))
    implicit val StockfishReads: Reads[Request.Stockfish]          = Json.reads[Request.Stockfish]
    implicit val FishnetReads: Reads[Request.Fishnet]              = Json.reads[Request.Fishnet]
    implicit val AcquireReads: Reads[Request.Acquire]              = Json.reads[Request.Acquire]
    implicit val ScoreReads: Reads[Request.Evaluation.Score]       = Json.reads[Request.Evaluation.Score]
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
        case obj    =>
          if (~obj.boolean("skipped")) JsSuccess(Left(Request.Evaluation.Skipped).some)
          else EvaluationReads reads obj map Right.apply map some
      }
    implicit val EngineMetaReads: Reads[Request.EngineMeta]         = Json.reads[Request.EngineMeta]
    implicit val BgProbsReads: Reads[Request.BgProbsPost]           = Json.reads[Request.BgProbsPost]
    implicit val BgCandReads: Reads[Request.BgCandPost]             = Json.reads[Request.BgCandPost]
    implicit val BgMoveReads: Reads[Request.BgMovePost]             = Json.reads[Request.BgMovePost]
    implicit val BgStatsReads: Reads[Request.BgStatsPost]           = Json.reads[Request.BgStatsPost]
    implicit val BgWinnerReads: Reads[Request.BgWinnerPost]         = Json.reads[Request.BgWinnerPost]
    implicit val BgGameReads: Reads[Request.BgGamePost]             = Json.reads[Request.BgGamePost]
    implicit val BackgammonPostReads: Reads[Request.BackgammonPost] = Json.reads[Request.BackgammonPost]

    // Lenient: chess clients send {fishnet, stockfish, analysis}; the backgammon
    // worker sends {fishnet, engine, backgammon}. Default the chess-only fields so
    // both shapes parse through the one /fishnet/analysis endpoint.
    implicit val PostAnalysisReads: Reads[Request.PostAnalysisLexicalUci] = (
      (__ \ "fishnet").read[Request.Fishnet] and
        (__ \ "stockfish").readNullable[Request.Stockfish].map(_ getOrElse Request.Stockfish(None)) and
        (__ \ "analysis")
          .readNullable[List[Option[Request.Evaluation.OrSkipped[LexicalUci]]]]
          .map(_ getOrElse Nil) and
        (__ \ "engine").readNullable[Request.EngineMeta] and
        (__ \ "backgammon").readNullable[Request.BackgammonPost]
    )((f, s, a, e, b) => Request.PostAnalysisLexicalUci(f, s, a, e, b))
  }

  object writers {
    implicit val VariantWrites: Writes[Variant]          = Writes[Variant] { v => JsString(v.fishnetKey) }
    implicit val BgWorkWrites: OWrites[W.BgWork]          = Json.writes[W.BgWork]
    implicit val GameWrites: Writes[UciGame]    = Writes[UciGame] { g =>
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
              "type"  -> "analysis",
              "id"    -> a.id,
              "nodes" -> Json.obj(
                "sf15"      -> a.nodes,
                "sf14"      -> a.nodes * 14 / 10,
                "nnue"      -> a.nodes * 14 / 10, // bc fishnet <= 2.3.4
                "classical" -> a.nodes * 28 / 10
              ),
              "timeout" -> Cleaner.timeoutPerPly.toMillis
            ),
            "skipPositions" -> a.skipPositions
          ) ++ a.backgammon.fold(Json.obj())(bg => Json.obj("backgammon" -> Json.toJson(bg)))
      }) ++ Json.toJson(work.game.toUci).as[JsObject]
    }
  }
}
