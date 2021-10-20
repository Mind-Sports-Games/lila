package lila.game

import play.api.libs.json._

import strategygames.chess.variant.Crazyhouse
import strategygames.{ Board, Centis, Color, GameLogic, Move => StratMove, PromotableRole, Pos, Situation, Status, Role, White, Black }
import strategygames.chess
import strategygames.format.Forsyth
import strategygames.chess.format.pgn.Dumper
import JsonView._
import lila.chat.{ PlayerLine, UserLine }
import lila.common.ApiVersion

sealed trait Event {
  def typ: String
  def data: JsValue
  def only: Option[Color]   = None
  def owner: Boolean        = false
  def watcher: Boolean      = false
  def troll: Boolean        = false
  def moveBy: Option[Color] = None
}

object Event {

  sealed trait Empty extends Event {
    def data = JsNull
  }

  object Start extends Empty {
    def typ = "start"
  }

  object MoveOrDrop {

    def data(
        lib: GameLogic,
        fen: String,
        check: Boolean,
        threefold: Boolean,
        state: State,
        clock: Option[ClockEvent],
        possibleMoves: Map[Pos, List[Pos]],
        possibleDrops: Option[List[Pos]],
        crazyData: Option[Crazyhouse.Data],
        captLen: Option[Int] = None
    )(extra: JsObject) = {
      extra ++ Json
        .obj(
          "fen"   -> fen,
          "ply"   -> state.turns,
          "dests" -> PossibleMoves.oldJson(possibleMoves),
          "captLen" -> ~captLen,
          "lib"     -> lib.id
        )
        .add("clock" -> clock.map(_.data))
        .add("status" -> state.status)
        .add("winner" -> state.winner)
        .add("check" -> check)
        .add("threefold" -> threefold)
        .add("wDraw" -> state.whiteOffersDraw)
        .add("bDraw" -> state.blackOffersDraw)
        .add("crazyhouse" -> crazyData)
        .add("drops" -> possibleDrops.map { squares =>
          JsString(squares.map(_.key).mkString)
        })
    }
  }

  case class Move(
      lib: GameLogic,
      orig: Pos,
      dest: Pos,
      san: String,
      fen: String, // not a FEN, just a board fen
      check: Boolean,
      threefold: Boolean,
      promotion: Option[Promotion],
      enpassant: Option[Enpassant],
      castle: Option[Castling],
      state: State,
      clock: Option[ClockEvent],
      possibleMoves: Map[Pos, List[Pos]],
      possibleDrops: Option[List[Pos]],
      crazyData: Option[Crazyhouse.Data],
      captLen: Option[Int]
  ) extends Event {
    def typ = "move"
    def data =
      MoveOrDrop.data(
        lib,
        fen,
        check,
        threefold,
        state,
        clock,
        possibleMoves,
        possibleDrops,
        crazyData,
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
    override def moveBy = Some(!state.color)
  }
  object Move {
    def apply(
        move: StratMove,
        situation: Situation,
        state: State,
        clock: Option[ClockEvent],
        crazyData: Option[Crazyhouse.Data]
    ): Move =
      Move(
        lib = situation.board.variant.gameLogic,
        orig = move.orig,
        dest = move.dest,
        san = move match {
          case StratMove.Chess(move)    => Dumper(move)
          case StratMove.Draughts(move) => strategygames.draughts.format.pdn.Dumper(move)
        },
        fen = if (situation.board.variant.gameLogic == GameLogic.Draughts() && situation.board.variant.frisianVariant)
            situation.board match {
              case Board.Draughts(board)
                => Forsyth.exportBoard(GameLogic.Draughts(), situation.board) + ":" + 
                  strategygames.draughts.format.Forsyth.exportKingMoves(board)
              case _ => sys.error("mismatched board lib types")
            }
          else
            Forsyth.exportBoard(situation.board.variant.gameLogic, situation.board),
        check = situation.check,
        threefold = situation.threefoldRepetition,
        promotion = move.promotion.map { Promotion(_, move.dest) },
        enpassant = (move.capture ifTrue move.enpassant).map {
          (capture: List[Pos]) => Event.Enpassant(capture(0), !move.color)
        },
        castle = move.castle.map { case (king, rook) =>
          Castling(king, rook, move.color)
        },
        state = state,
        clock = clock,
        possibleMoves = (situation, move.dest) match {
          case (Situation.Draughts(situation), Pos.Draughts(moveDest)) =>
            if (situation.ghosts > 0)
              Map(Pos.Draughts(moveDest) ->
                situation.destinationsFrom(moveDest).map(Pos.Draughts)
              )
            else situation.allDestinations.map{
              case(from, to) => (Pos.Draughts(from), to.map(Pos.Draughts))
            }
          case _ => situation.destinations
        },
        possibleDrops = situation.drops,
        crazyData = crazyData,
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
      role: Role,
      pos: Pos,
      san: String,
      fen: String,
      check: Boolean,
      threefold: Boolean,
      state: State,
      clock: Option[ClockEvent],
      possibleMoves: Map[Pos, List[Pos]],
      crazyData: Option[Crazyhouse.Data],
      possibleDrops: Option[List[Pos]]
  ) extends Event {
    def typ = "drop"
    def data =
      MoveOrDrop.data(GameLogic.Chess(), fen, check, threefold, state, clock, possibleMoves, possibleDrops, crazyData) {
        Json.obj(
          "role" -> role.groundName,
          "uci"  -> s"${role.pgn}@${pos.key}",
          "san"  -> san
        )
      }
    override def moveBy = Some(!state.color)
  }
  object Drop {
    def apply(
        drop: chess.Drop,
        situation: Situation,
        state: State,
        clock: Option[ClockEvent],
        crazyData: Option[Crazyhouse.Data]
    ): Drop =
      Drop(
        role = Role.ChessRole(drop.piece.role),
        pos = Pos.Chess(drop.pos),
        san = Dumper(drop),
        fen = Forsyth.exportBoard(GameLogic.Chess(), situation.board),
        check = situation.check,
        threefold = situation.threefoldRepetition,
        state = state,
        clock = clock,
        possibleMoves = situation.destinations,
        possibleDrops = situation.drops,
        crazyData = crazyData
      )
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

  case class Enpassant(pos: Pos, color: Color) extends Event {
    def typ = "enpassant"
    def data =
      Json.obj(
        "key"   -> pos.key,
        "color" -> color
      )
  }

  case class Castling(king: (Pos, Pos), rook: (Pos, Pos), color: Color) extends Event {
    def typ = "castling"
    def data =
      Json.obj(
        "king"  -> Json.arr(king._1.key, king._2.key),
        "rook"  -> Json.arr(rook._1.key, rook._2.key),
        "color" -> color
      )
  }

  case class RedirectOwner(
      color: Color,
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
    override def only = Some(color)
  }

  case class Promotion(role: PromotableRole, pos: Pos) extends Event {
    private val lib = pos match {
      case Pos.Chess(_)    => GameLogic.Chess().id
      case Pos.Draughts(_) => GameLogic.Draughts().id
    }
    def typ = "promotion"
    def data =
      Json.obj(
        "key"        -> pos.key,
        "pieceClass" -> role.toString.toLowerCase,
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
  case class End(winner: Option[Color]) extends Event {
    def typ  = "end"
    def data = Json.toJson(winner)
  }

  case class EndData(game: Game, ratingDiff: Option[RatingDiffs]) extends Event {
    def typ = "endData"
    def data =
      Json
        .obj(
          "winner" -> game.winnerColor,
          "status" -> game.status
        )
        .add("clock" -> game.clock.map { c =>
          Json.obj(
            "wc" -> c.remainingTime(White).centis,
            "bc" -> c.remainingTime(Black).centis
          )
        })
        .add("ratingDiff" -> ratingDiff.map { rds =>
          Json.obj(
            Color.White.name -> rds.white,
            Color.Black.name -> rds.black
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
  case class RematchOffer(by: Option[Color]) extends Event {
    def typ            = "reload"
    def data           = reloadOr("rematchOffer", by)
    override def owner = true
  }

  case class RematchTaken(nextId: Game.ID) extends Event {
    def typ  = "reload"
    def data = reloadOr("rematchTaken", nextId)
  }

  case class DrawOffer(by: Option[Color]) extends Event {
    def typ  = "reload"
    def data = reloadOr("drawOffer", by)
  }

  case class ClockInc(color: Color, time: Centis) extends Event {
    def typ = "clockInc"
    def data =
      Json.obj(
        "color" -> color,
        "time"  -> time.centis
      )
  }

  sealed trait ClockEvent extends Event

  case class Clock(white: Centis, black: Centis, nextLagComp: Option[Centis] = None) extends ClockEvent {
    def typ = "clock"
    def data =
      Json
        .obj(
          "white" -> white.toSeconds,
          "black" -> black.toSeconds
        )
        .add("lag" -> nextLagComp.collect { case Centis(c) if c > 1 => c })
  }
  object Clock {
    def apply(clock: strategygames.Clock): Clock =
      Clock(
        clock remainingTime White,
        clock remainingTime Black,
        clock lagCompEstimate clock.color
      )
  }

  case class Berserk(color: Color) extends Event {
    def typ  = "berserk"
    def data = Json.toJson(color)
  }

  case class CorrespondenceClock(white: Float, black: Float) extends ClockEvent {
    def typ  = "cclock"
    def data = Json.obj("white" -> white, "black" -> black)
  }
  object CorrespondenceClock {
    def apply(clock: lila.game.CorrespondenceClock): CorrespondenceClock =
      CorrespondenceClock(clock.whiteTime, clock.blackTime)
  }

  case class CheckCount(white: Int, black: Int) extends Event {
    def typ = "checkCount"
    def data =
      Json.obj(
        "white" -> white,
        "black" -> black
      )
  }

  case class KingMoves(white: Int, black: Int, whiteKing: Option[Pos], blackKing: Option[Pos]) extends Event {
    def typ = "kingMoves"
    def data = Json.obj(
      "white" -> white,
      "black" -> black,
      "whiteKing" -> whiteKing.map(_.toString),
      "blackKing" -> blackKing.map(_.toString)
    )
  }

  case class State(
      color: Color,
      turns: Int,
      status: Option[Status],
      winner: Option[Color],
      whiteOffersDraw: Boolean,
      blackOffersDraw: Boolean
  ) extends Event {
    def typ = "state"
    def data =
      Json
        .obj(
          "color" -> color,
          "turns" -> turns
        )
        .add("status" -> status)
        .add("winner" -> winner)
        .add("wDraw" -> whiteOffersDraw)
        .add("bDraw" -> blackOffersDraw)
  }

  case class TakebackOffers(
      white: Boolean,
      black: Boolean
  ) extends Event {
    def typ = "takebackOffers"
    def data =
      Json
        .obj()
        .add("white" -> white)
        .add("black" -> black)
    override def owner = true
  }

  case class Crowd(
      white: Boolean,
      black: Boolean,
      watchers: Option[JsValue]
  ) extends Event {
    def typ = "crowd"
    def data =
      Json
        .obj(
          "white" -> white,
          "black" -> black
        )
        .add("watchers" -> watchers)
  }
}
