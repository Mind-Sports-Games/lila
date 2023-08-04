package lila.game

import play.api.libs.json._

import strategygames.{
  Board,
  ByoyomiClock,
  Centis,
  FischerClock,
  Player => PlayerIndex,
  GameFamily,
  GameLogic,
  Move => StratMove,
  Drop => StratDrop,
  Pass => StratPass,
  SelectSquares => StratSelectSquares,
  PromotableRole,
  PocketData,
  Pos,
  Situation,
  Status,
  Role,
  P1,
  P2
}
import strategygames.chess
import strategygames.variant.Variant
import strategygames.format.Forsyth
import JsonView._
import lila.chat.{ PlayerLine, UserLine }
import lila.common.ApiVersion

sealed trait Event {
  def typ: String
  def data: JsValue
  def only: Option[PlayerIndex]   = None
  def owner: Boolean              = false
  def watcher: Boolean            = false
  def troll: Boolean              = false
  def moveBy: Option[PlayerIndex] = None
}

object Event {

  sealed trait Empty extends Event {
    def data = JsNull
  }

  object Start extends Empty {
    def typ = "start"
  }

  object Action {

    def data(
        gf: GameFamily,
        fen: String,
        check: Boolean,
        threefold: Boolean,
        perpetualWarning: Boolean,
        state: State,
        clock: Option[ClockEvent],
        possibleMoves: Map[Pos, List[Pos]],
        possibleDrops: Option[List[Pos]],
        possibleDropsByRole: Option[Map[Role, List[Pos]]],
        pocketData: Option[PocketData],
        captLen: Option[Int] = None
    )(extra: JsObject) = {
      extra ++ Json
        .obj(
          "fen"         -> fen,
          "ply"         -> state.turns,
          "dests"       -> PossibleMoves.oldJson(possibleMoves),
          "captLen"     -> ~captLen,
          "gf"          -> gf.id,
          "dropsByRole" -> PossibleDropsByRole.json(possibleDropsByRole.getOrElse(Map.empty))
        )
        .add("clock" -> clock.map(_.data))
        .add("status" -> state.status)
        .add("winner" -> state.winner)
        .add("check" -> check)
        .add("threefold" -> threefold)
        .add("perpetualWarning" -> perpetualWarning)
        .add("wDraw" -> state.p1OffersDraw)
        .add("bDraw" -> state.p2OffersDraw)
        .add("crazyhouse" -> pocketData)
        .add("drops" -> possibleDrops.map { squares =>
          JsString(squares.map(_.key).mkString)
        })
    }
  }

  case class Move(
      gf: GameFamily,
      orig: Pos,
      dest: Pos,
      san: String,
      fen: String, // not a FEN, just a board fen
      check: Boolean,
      threefold: Boolean,
      perpetualWarning: Boolean,
      promotion: Option[Promotion],
      enpassant: Option[Enpassant],
      castle: Option[Castling],
      state: State,
      clock: Option[ClockEvent],
      possibleMoves: Map[Pos, List[Pos]],
      possibleDrops: Option[List[Pos]],
      possibleDropsByRole: Option[Map[Role, List[Pos]]],
      pocketData: Option[PocketData],
      captLen: Option[Int]
  ) extends Event {
    def typ = "move"
    def data =
      Action.data(
        gf,
        fen,
        check,
        threefold,
        perpetualWarning,
        state,
        clock,
        possibleMoves,
        possibleDrops,
        possibleDropsByRole,
        pocketData,
        captLen
      ) {
        Json
          .obj(
            "uci" -> s"${orig.key}${dest.key}",
            "san" -> san
          )
          .add("promotion" -> promotion.map(_.data))
          .add("enpassant" -> enpassant.map(_.data))
          .add("castle" -> castle.map(_.data))
      }
    override def moveBy = Some(!state.playerIndex)
  }
  object Move {
    def apply(
        move: StratMove,
        situation: Situation,
        state: State,
        clock: Option[ClockEvent],
        pocketData: Option[PocketData]
    ): Move =
      Move(
        gf = situation.board.variant.gameFamily,
        orig = move.orig,
        dest = move.dest,
        san = move match {
          case StratMove.Chess(move)        => strategygames.chess.format.pgn.Dumper(move)
          case StratMove.Draughts(move)     => strategygames.draughts.format.pdn.Dumper(move)
          case StratMove.FairySF(move)      => strategygames.fairysf.format.pgn.Dumper(move)
          case StratMove.Samurai(move)      => strategygames.samurai.format.pgn.Dumper(move)
          case StratMove.Togyzkumalak(move) => strategygames.togyzkumalak.format.pgn.Dumper(move)
        },
        fen =
          if (
            situation.board.variant.gameLogic == GameLogic
              .Draughts() && situation.board.variant.frisianVariant
          )
            situation.board match {
              case Board.Draughts(board) =>
                Forsyth.exportBoard(GameLogic.Draughts(), situation.board) + ":" +
                  strategygames.draughts.format.Forsyth.exportKingMoves(board)
              case _ => sys.error("mismatched board lib types")
            }
          else
            Forsyth
              .exportBoard(situation.board.variant.gameLogic, situation.board),
        check = situation.check,
        threefold = situation.threefoldRepetition,
        perpetualWarning = situation.perpetualPossible,
        promotion = move.promotion.map { Promotion(_, move.dest) },
        enpassant = (move.capture ifTrue move.enpassant).map { (capture: List[Pos]) =>
          Event.Enpassant(capture(0), !move.player)
        },
        castle = move.castle.map { case (king, rook) =>
          Castling(king, rook, move.player)
        },
        state = state,
        clock = clock,
        possibleMoves = (situation, move.dest) match {
          //TODO: The Draughts specific logic should be pushed into strategygames
          //and should be ready to go now validMoves handles this ghosts logic internally
          //see Situation.Draughts.destinations
          case (Situation.Draughts(situation), Pos.Draughts(moveDest)) =>
            if (situation.ghosts > 0)
              Map(
                Pos.Draughts(moveDest) ->
                  situation.destinationsFrom(moveDest).map(Pos.Draughts)
              )
            else
              situation.allDestinations.map { case (from, to) =>
                (Pos.Draughts(from), to.map(Pos.Draughts))
              }
          case _ => situation.destinations
        },
        possibleDrops = situation.drops,
        possibleDropsByRole = situation match {
          case (Situation.FairySF(_)) =>
            situation.dropsByRole
          case _ => None
        },
        pocketData = pocketData,
        captLen = (situation, move.dest) match {
          case (Situation.Draughts(situation), Pos.Draughts(moveDest)) =>
            if (situation.ghosts > 0)
              situation.captureLengthFrom(moveDest)
            else
              situation.allMovesCaptureLength.some
          case _ => None
        }
      )
  }

  case class Drop(
      gf: GameFamily,
      role: Role,
      pos: Pos,
      san: String,
      fen: String,
      check: Boolean,
      threefold: Boolean,
      perpetualWarning: Boolean,
      state: State,
      clock: Option[ClockEvent],
      possibleMoves: Map[Pos, List[Pos]],
      pocketData: Option[PocketData],
      possibleDrops: Option[List[Pos]],
      possibleDropsByRole: Option[Map[Role, List[Pos]]]
  ) extends Event {
    def typ = "drop"
    def data =
      Action.data(
        gf,
        fen,
        check,
        threefold,
        perpetualWarning,
        state,
        clock,
        possibleMoves,
        possibleDrops,
        possibleDropsByRole,
        pocketData
      ) {
        Json.obj(
          "role" -> role.groundName,
          "uci"  -> s"${role.pgn}@${pos.key}",
          "san"  -> san
        )
      }
    override def moveBy = Some(!state.playerIndex)
  }
  object Drop {
    def apply(
        drop: StratDrop,
        situation: Situation,
        state: State,
        clock: Option[ClockEvent],
        pocketData: Option[PocketData]
    ): Drop =
      Drop(
        gf = situation.board.variant.gameFamily,
        role = drop.piece.role,
        pos = drop.pos,
        san = drop match {
          case StratDrop.Chess(drop)   => strategygames.chess.format.pgn.Dumper(drop)
          case StratDrop.FairySF(drop) => strategygames.fairysf.format.pgn.Dumper(drop)
          case StratDrop.Go(drop)      => strategygames.go.format.pgn.Dumper(drop)
        },
        fen = Forsyth.exportBoard(situation.board.variant.gameLogic, situation.board),
        check = situation.check,
        threefold = situation.threefoldRepetition,
        perpetualWarning = situation.perpetualPossible,
        state = state,
        clock = clock,
        possibleMoves = situation.destinations,
        possibleDrops = situation.drops,
        possibleDropsByRole = situation match {
          case (Situation.FairySF(_)) =>
            situation.dropsByRole
          case (Situation.Go(_)) =>
            situation.dropsByRole
          case _ => None
        },
        pocketData = pocketData
      )
  }

  case class Pass(
      gf: GameFamily,
      // role: Role,
      // pos: Pos,
      canSelectSquares: Boolean,
      san: String,
      fen: String,
      check: Boolean,
      threefold: Boolean,
      perpetualWarning: Boolean,
      state: State,
      clock: Option[ClockEvent],
      possibleMoves: Map[Pos, List[Pos]],
      pocketData: Option[PocketData],
      possibleDrops: Option[List[Pos]],
      possibleDropsByRole: Option[Map[Role, List[Pos]]]
  ) extends Event {
    def typ = "pass"
    def data =
      Action.data(
        gf,
        fen,
        check,
        threefold,
        perpetualWarning,
        state,
        clock,
        possibleMoves,
        possibleDrops,
        possibleDropsByRole,
        pocketData
      ) {
        Json.obj(
          //"role" -> role.groundName,
          "canSelectSquares" -> canSelectSquares,
          "uci"              -> "pass",
          "san"              -> san
        )
      }
    override def moveBy = Some(!state.playerIndex)
  }
  object Pass {
    def apply(
        pass: StratPass,
        situation: Situation,
        state: State,
        clock: Option[ClockEvent],
        pocketData: Option[PocketData]
    ): Pass =
      Pass(
        gf = situation.board.variant.gameFamily,
        canSelectSquares = situation match {
          case (Situation.Go(s)) => s.canSelectSquares
          case _                 => false
        },
        san = "pass",
        fen = Forsyth.exportBoard(situation.board.variant.gameLogic, situation.board),
        check = situation.check,
        threefold = situation.threefoldRepetition,
        perpetualWarning = situation.perpetualPossible,
        state = state,
        clock = clock,
        possibleMoves = situation.destinations,
        possibleDrops = situation.drops,
        possibleDropsByRole = situation match {
          case (Situation.FairySF(_)) =>
            situation.dropsByRole
          case (Situation.Go(_)) =>
            situation.dropsByRole
          case _ => None
        },
        pocketData = pocketData
      )
  }

  case class SelectSquares(
      gf: GameFamily,
      squares: List[Pos],
      san: String,
      fen: String,
      check: Boolean,
      threefold: Boolean,
      perpetualWarning: Boolean,
      state: State,
      clock: Option[ClockEvent],
      possibleMoves: Map[Pos, List[Pos]],
      pocketData: Option[PocketData],
      possibleDrops: Option[List[Pos]],
      possibleDropsByRole: Option[Map[Role, List[Pos]]]
  ) extends Event {
    def typ = "selectSquares"
    def data =
      Action.data(
        gf,
        fen,
        check,
        threefold,
        perpetualWarning,
        state,
        clock,
        possibleMoves,
        possibleDrops,
        possibleDropsByRole,
        pocketData
      ) {
        Json.obj(
          "squares"          -> squares.mkString(","),
          "canSelectSquares" -> false,
          "uci"              -> s"ss:${squares.mkString(",")}",
          "san"              -> san
        )
      }
    override def moveBy = Some(!state.playerIndex)
  }
  object SelectSquares {
    def apply(
        ss: StratSelectSquares,
        situation: Situation,
        state: State,
        clock: Option[ClockEvent],
        pocketData: Option[PocketData]
    ): SelectSquares =
      SelectSquares(
        gf = situation.board.variant.gameFamily,
        squares = ss.squares,
        san = s"ss:${ss.squares.mkString(",")}",
        fen = Forsyth.exportBoard(situation.board.variant.gameLogic, situation.board),
        check = situation.check,
        threefold = situation.threefoldRepetition,
        perpetualWarning = situation.perpetualPossible,
        state = state,
        clock = clock,
        possibleMoves = situation.destinations,
        possibleDrops = situation.drops,
        possibleDropsByRole = situation match {
          case (Situation.FairySF(_)) =>
            situation.dropsByRole
          case (Situation.Go(_)) =>
            situation.dropsByRole
          case _ => None
        },
        pocketData = pocketData
      )
  }

  object PossibleDropsByRole {

    def json(drops: Map[Role, List[Pos]]) =
      if (drops.isEmpty) JsNull
      else {
        val sb    = new java.lang.StringBuilder(128)
        var first = true
        drops foreach { case (orig, dests) =>
          if (first) first = false
          else sb append " "
          sb append orig.forsyth
          dests foreach { sb append _.key }
        }
        JsString(sb.toString)
      }

  }

  object PossibleMoves {

    def json(moves: Map[Pos, List[Pos]], apiVersion: ApiVersion) =
      if (apiVersion gte 4) newJson(moves)
      else oldJson(moves)

    def newJson(moves: Map[Pos, List[Pos]]) =
      if (moves.isEmpty) JsNull
      else {
        val sb    = new java.lang.StringBuilder(128)
        var first = true
        moves foreach { case (orig, dests) =>
          if (first) first = false
          else sb append " "
          sb append orig.key
          dests foreach { sb append _.key }
        }
        JsString(sb.toString)
      }

    def oldJson(moves: Map[Pos, List[Pos]]) =
      if (moves.isEmpty) JsNull
      else
        moves.foldLeft(JsObject(Nil)) { case (res, (o, d)) =>
          res + (o.key -> JsString(d map (_.key) mkString))
        }
  }

  case class Enpassant(pos: Pos, playerIndex: PlayerIndex) extends Event {
    def typ = "enpassant"
    def data =
      Json.obj(
        "key"         -> pos.key,
        "playerIndex" -> playerIndex
      )
  }

  case class Castling(king: (Pos, Pos), rook: (Pos, Pos), playerIndex: PlayerIndex) extends Event {
    def typ = "castling"
    def data =
      Json.obj(
        "king"        -> Json.arr(king._1.key, king._2.key),
        "rook"        -> Json.arr(rook._1.key, rook._2.key),
        "playerIndex" -> playerIndex
      )
  }

  case class RedirectOwner(
      playerIndex: PlayerIndex,
      id: String,
      cookie: Option[JsObject]
  ) extends Event {
    def typ = "redirect"
    def data =
      Json
        .obj(
          "id"  -> id,
          "url" -> s"/$id"
        )
        .add("cookie" -> cookie)
    override def only = Some(playerIndex)
  }

  case class Promotion(role: PromotableRole, pos: Pos) extends Event {
    private val lib = pos match {
      case Pos.Chess(_)        => GameLogic.Chess().id
      case Pos.Draughts(_)     => GameLogic.Draughts().id
      case Pos.FairySF(_)      => GameLogic.FairySF().id
      case Pos.Samurai(_)      => GameLogic.Samurai().id
      case Pos.Togyzkumalak(_) => GameLogic.Togyzkumalak().id
      case Pos.Go(_)           => GameLogic.Go().id
    }
    def typ = "promotion"
    def data =
      Json.obj(
        "key"        -> pos.key,
        "pieceClass" -> role.groundName,
        "lib"        -> lib
      )
  }

  case class PlayerMessage(line: PlayerLine) extends Event {
    def typ            = "message"
    def data           = lila.chat.JsonView(line)
    override def owner = true
    override def troll = false
  }

  case class UserMessage(line: UserLine, w: Boolean) extends Event {
    def typ              = "message"
    def data             = lila.chat.JsonView(line)
    override def troll   = line.troll
    override def watcher = w
    override def owner   = !w
  }

  // for mobile app BC only
  case class End(winner: Option[PlayerIndex]) extends Event {
    def typ  = "end"
    def data = Json.toJson(winner)
  }

  case class EndData(game: Game, ratingDiff: Option[RatingDiffs]) extends Event {
    def typ = "endData"
    def data =
      Json
        .obj(
          "winner"       -> game.winnerPlayerIndex,
          "winnerPlayer" -> game.winnerPlayerIndex.map(game.variant.playerNames),
          "loserPlayer"  -> game.winnerPlayerIndex.map(w => game.variant.playerNames(!w)),
          "status"       -> game.status
        )
        .add("clock" -> game.clock.map { c =>
          c match {
            case fc: FischerClock =>
              Json.obj(
                "p1" -> fc.remainingTime(P1).centis,
                "p2" -> fc.remainingTime(P2).centis
              )
            case bc: ByoyomiClock =>
              Json.obj(
                "p1"        -> bc.remainingTime(P1).centis,
                "p2"        -> bc.remainingTime(P2).centis,
                "p1Periods" -> bc.players(P1).periodsLeft,
                "p2Periods" -> bc.players(P2).periodsLeft
              )
          }
        })
        .add("ratingDiff" -> ratingDiff.map { rds =>
          Json.obj(
            PlayerIndex.P1.name -> rds.p1,
            PlayerIndex.P2.name -> rds.p2
          )
        })
        .add("boosted" -> game.boosted)
  }

  case object Reload extends Empty {
    def typ = "reload"
  }
  case object ReloadOwner extends Empty {
    def typ            = "reload"
    override def owner = true
  }

  private def reloadOr[A: Writes](typ: String, data: A) = Json.obj("t" -> typ, "d" -> data)

  // use t:reload for mobile app BC,
  // but send extra data for the web to avoid reloading
  case class RematchOffer(by: Option[PlayerIndex]) extends Event {
    def typ            = "reload"
    def data           = reloadOr("rematchOffer", by)
    override def owner = true
  }

  case class RematchTaken(nextId: Game.ID) extends Event {
    def typ  = "reload"
    def data = reloadOr("rematchTaken", nextId)
  }

  case class DrawOffer(by: Option[PlayerIndex]) extends Event {
    def typ  = "reload"
    def data = reloadOr("drawOffer", by)
  }

  case class ClockInc(playerIndex: PlayerIndex, time: Centis) extends Event {
    def typ = "clockInc"
    def data =
      Json.obj(
        "playerIndex" -> playerIndex,
        "time"        -> time.centis
      )
  }

  sealed trait ClockEvent extends Event

  case class Clock(
      p1: Centis,
      p2: Centis,
      p1Periods: Int = 0,
      p2Periods: Int = 0,
      nextLagComp: Option[Centis] = None
  ) extends ClockEvent {
    def typ = "clock"
    def data =
      Json
        .obj(
          "p1"        -> p1.toSeconds,
          "p2"        -> p2.toSeconds,
          "p1Periods" -> p1Periods,
          "p2Periods" -> p2Periods
        )
        .add("lag" -> nextLagComp.collect { case Centis(c) if c > 1 => c })
  }
  object Clock {
    def apply(clock: strategygames.Clock): Clock =
      clock match {
        case fc: FischerClock =>
          Clock(
            fc.remainingTime(P1),
            fc.remainingTime(P2),
            nextLagComp = fc.lagCompEstimate(fc.player)
          )
        case bc: ByoyomiClock =>
          Clock(
            bc.remainingTime(P1),
            bc.remainingTime(P2),
            bc.players(P1).spentPeriods,
            bc.players(P2).spentPeriods,
            bc.lagCompEstimate(bc.player)
          )
      }
  }

  case class Berserk(playerIndex: PlayerIndex) extends Event {
    def typ  = "berserk"
    def data = Json.toJson(playerIndex)
  }

  case class CorrespondenceClock(p1: Float, p2: Float) extends ClockEvent {
    def typ  = "cclock"
    def data = Json.obj("p1" -> p1, "p2" -> p2)
  }
  object CorrespondenceClock {
    def apply(clock: lila.game.CorrespondenceClock): CorrespondenceClock =
      CorrespondenceClock(clock.p1Time, clock.p2Time)
  }

  case class CheckCount(p1: Int, p2: Int) extends Event {
    def typ = "checkCount"
    def data =
      Json.obj(
        "p1" -> p1,
        "p2" -> p2
      )
  }

  case class Score(p1: Int, p2: Int) extends Event {
    def typ = "score"
    def data =
      Json.obj(
        "p1" -> p1,
        "p2" -> p2
      )
  }

  case class KingMoves(p1: Int, p2: Int, p1King: Option[Pos], p2King: Option[Pos]) extends Event {
    def typ = "kingMoves"
    def data = Json.obj(
      "p1"     -> p1,
      "p2"     -> p2,
      "p1King" -> p1King.map(_.toString),
      "p2King" -> p2King.map(_.toString)
    )
  }

  case class State(
      playerIndex: PlayerIndex,
      turns: Int,
      status: Option[Status],
      winner: Option[PlayerIndex],
      p1OffersDraw: Boolean,
      p2OffersDraw: Boolean
  ) extends Event {
    def typ = "state"
    def data =
      Json
        .obj(
          "playerIndex" -> playerIndex,
          "turns"       -> turns
        )
        .add("status" -> status)
        .add("winner" -> winner)
        .add("wDraw" -> p1OffersDraw)
        .add("bDraw" -> p2OffersDraw)
  }

  case class TakebackOffers(
      p1: Boolean,
      p2: Boolean
  ) extends Event {
    def typ = "takebackOffers"
    def data =
      Json
        .obj()
        .add("p1" -> p1)
        .add("p2" -> p2)
    override def owner = true
  }

  case class Crowd(
      p1: Boolean,
      p2: Boolean,
      watchers: Option[JsValue]
  ) extends Event {
    def typ = "crowd"
    def data =
      Json
        .obj(
          "p1" -> p1,
          "p2" -> p2
        )
        .add("watchers" -> watchers)
  }
}
