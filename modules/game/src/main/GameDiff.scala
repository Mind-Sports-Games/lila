package lila.game

import strategygames.{
  P2,
  Board,
  ByoyomiClock,
  Centis,
  ClockBase,
  Clock,
  GameFamily,
  Player => PlayerIndex,
  GameLogic,
  History,
  PocketData,
  P1,
  Pos,
  Score
}
import strategygames.chess.CheckCount
import strategygames.draughts.KingMoves
import Game.BSONFields._
import reactivemongo.api.bson._
import scala.util.Try

import lila.db.BSON.BSONJodaDateTimeHandler
import lila.db.ByteArray
import lila.db.ByteArray.ByteArrayBSONHandler

object GameDiff {

  private type Set   = (String, BSONValue)
  private type Unset = (String, BSONValue)

  sealed private trait ClockType
  private case class FischerClockType() extends ClockType
  private case class ByoyomiClockType() extends ClockType
  private type ClockHistorySide = (ClockType, Centis, Vector[Centis], Boolean)

  private type PeriodEntriesSide = Vector[Int]

  type Diff = (List[Set], List[Unset])

  private val w = lila.db.BSON.writer

  def apply(a: Game, b: Game): Diff = {

    val setBuilder   = scala.collection.mutable.ListBuffer[Set]()
    val unsetBuilder = scala.collection.mutable.ListBuffer[Unset]()

    def d[A](name: String, getter: Game => A, toBson: A => BSONValue): Unit = {
      val vb = getter(b)
      if (getter(a) != vb) {
        if (vb == None || vb == null || vb == "") unsetBuilder += (name -> bTrue)
        else setBuilder += name                                         -> toBson(vb)
      }
    }

    def dOpt[A](name: String, getter: Game => A, toBson: A => Option[BSONValue]): Unit = {
      val vb = getter(b)
      if (getter(a) != vb) {
        if (vb == None || vb == null || vb == "") unsetBuilder += (name -> bTrue)
        else
          toBson(vb) match {
            case None    => unsetBuilder += (name -> bTrue)
            case Some(x) => setBuilder += name    -> x
          }
      }
    }

    def dTry[A](name: String, getter: Game => A, toBson: A => Try[BSONValue]): Unit =
      d[A](name, getter, a => toBson(a).get)

    def dOptTry[A](name: String, getter: Game => A, toBson: A => Option[Try[BSONValue]]): Unit =
      dOpt[A](name, getter, a => toBson(a).map(_.get))

    def getClockHistory(playerIndex: PlayerIndex)(g: Game): Option[ClockHistorySide] =
      for {
        clk     <- g.clock
        history <- g.clockHistory
        curPlayerIndex = g.turnPlayerIndex
        times          = history(playerIndex)
      } yield (
        clk match {
          case _: Clock        => FischerClockType()
          case _: ByoyomiClock => ByoyomiClockType()
        },
        clk.limit,
        times,
        g.flagged has playerIndex
      )

    def clockHistoryToBytes(o: Option[ClockHistorySide]) =
      o.flatMap { case (clockType, x, y, z) =>
        clockType match {
          case _: FischerClockType =>
            ByteArrayBSONHandler.writeOpt(BinaryFormat.fischerClockHistory.writeSide(x, y, z))
          case _: ByoyomiClockType =>
            ByteArrayBSONHandler.writeOpt(BinaryFormat.byoyomiClockHistory.writeSide(x, y, z))
        }
      }

    def getPeriodEntries(playerIndex: PlayerIndex)(g: Game): Option[Vector[Int]] =
      g.clockHistory.flatMap(ch =>
        ch match {
          case bch: ByoyomiClockHistory => Some(bch.periodEntries(playerIndex))
          case _                        => None
        }
      )

    def periodEntriesToBytes(o: Option[PeriodEntriesSide]) =
      o.flatMap { x =>
        ByteArrayBSONHandler.writeOpt(BinaryFormat.periodEntries.writeSide(x))
      }

    def pfnStorageWriter(pgnMoves: PgnMoves) =
      PfnStorage.OldBin.encode(a.variant.gameFamily, pgnMoves)

    def pmnStorageWriter(pgnMoves: PgnMoves) =
      PmnStorage.OldBin.encode(a.variant.gameFamily, pgnMoves)

    def ptnStorageWriter(pgnMoves: PgnMoves) =
      PtnStorage.OldBin.encode(a.variant.gameFamily, pgnMoves)

    def ponStorageWriter(pgnMoves: PgnMoves) =
      PonStorage.OldBin.encode(pgnMoves)

    a.variant.gameLogic match {
      case GameLogic.Draughts() =>
        a.pdnStorage match {
          case Some(PdnStorage.OldBin) => {
            dTry(oldPgn, _.pgnMoves, writeBytes compose PdnStorage.OldBin.encode)
            dTry(
              binaryPieces,
              _.board match {
                case Board.Draughts(b) => b.pieces
                case _                 => sys.error("Wrong board type")
              },
              writeBytes compose { m: strategygames.draughts.PieceMap =>
                BinaryFormat.piece.writeDraughts(
                  m,
                  a.variant match {
                    case strategygames.variant.Variant.Draughts(v) => v
                    case _                                         => sys.error("Wrong variant type")
                  }
                )
              }
            )
            d(positionHashes, _.history.positionHashes, w.bytes)
            d(historyLastMove, _.history.lastMove.map(_.uci) | "", w.str)
            // since variants are always OldBin
            if (a.variant.frisianVariant || a.variant.draughts64Variant)
              dOptTry(
                kingMoves,
                _.history.kingMoves,
                (o: KingMoves) => o.nonEmpty option { BSONHandlers.kingMovesWriter writeTry o }
              )
          }
          case Some(PdnStorage.Huffman) => {
            dTry(huffmanPgn, _.pgnMoves, writeBytes compose PdnStorage.Huffman.encode)
          }
          case _ => sys.error("invalid draughts storage")
        }
      case GameLogic.Chess() =>
        if (a.variant.standard)
          dTry(huffmanPgn, _.pgnMoves, writeBytes compose PgnStorage.Huffman.encode)
        else {
          dTry(oldPgn, _.pgnMoves, writeBytes compose PgnStorage.OldBin.encode)
          dTry(
            binaryPieces,
            _.board match {
              case Board.Chess(b) => b.pieces
              case _              => sys.error("Wrong board type")
            },
            writeBytes compose BinaryFormat.piece.writeChess
          )
          d(positionHashes, _.history.positionHashes, w.bytes)
          dTry(
            unmovedRooks,
            _.history.unmovedRooks,
            writeBytes compose BinaryFormat.unmovedRooks.write
          )
          dTry(
            castleLastMove,
            makeCastleLastMove,
            CastleLastMove.castleLastMoveBSONHandler.writeTry
          )
          // since variants are always OldBin
          if (a.variant.threeCheck || a.variant.fiveCheck)
            dOpt(
              checkCount,
              _.history.checkCount,
              (o: CheckCount) => o.nonEmpty ?? { BSONHandlers.checkCountWriter writeOpt o }
            )
          if (a.variant.dropsVariant)
            dOpt(
              pocketData,
              _.board.pocketData,
              (o: Option[PocketData]) => o map BSONHandlers.pocketDataBSONHandler.write
            )
        }
      case GameLogic.FairySF() => {
        dTry(
          oldPgn,
          { g =>
            g.board match {
              case Board.FairySF(b) =>
                b.variant.gameFamily match {
                  //in the case of Amazons we want to store our moves and drops as individuals
                  case GameFamily.Amazons() => g.pgnMoves
                  //in other cases we want to store the fairysf format (difference in promotion notation)
                  case _ => b.uciMoves.toVector
                }
              case _ => sys.error("Wrong board type")
            }
          },
          writeBytes compose pfnStorageWriter
        )
        dTry(
          binaryPieces,
          _.board match {
            case Board.FairySF(b) => b.pieces
            case _                => sys.error("Wrong board type")
          },
          writeBytes compose BinaryFormat.piece.writeFairySF
        )
        d(positionHashes, _.history.positionHashes, w.bytes)
        d(historyLastMove, _.history.lastMove.map(_.uci) | "", w.str)
        if (a.variant.dropsVariant)
          dOpt(
            pocketData,
            _.board.pocketData,
            (o: Option[PocketData]) => o map BSONHandlers.pocketDataBSONHandler.write
          )
      }
      case GameLogic.Samurai() => {
        dTry(
          oldPgn,
          _.board match {
            case Board.Samurai(b) => b.uciMoves.toVector
            case _                => sys.error("Wrong board type")
          },
          writeBytes compose pmnStorageWriter
        )
        dTry(
          binaryPieces,
          _.board match {
            case Board.Samurai(b) => b.pieces
            case _                => sys.error("Wrong board type")
          },
          writeBytes compose BinaryFormat.piece.writeSamurai
        )
        d(positionHashes, _.history.positionHashes, w.bytes)
        d(historyLastMove, _.history.lastMove.map(_.uci) | "", w.str)
      }
      case GameLogic.Togyzkumalak() => {
        dTry(oldPgn, _.pgnMoves, writeBytes compose ptnStorageWriter)
        dTry(
          binaryPieces,
          _.board match {
            case Board.Togyzkumalak(b) => b.pieces
            case _                     => sys.error("Wrong board type")
          },
          writeBytes compose BinaryFormat.piece.writeTogyzkumalak
        )
        d(positionHashes, _.history.positionHashes, w.bytes)
        d(historyLastMove, _.history.lastMove.map(_.uci) | "", w.str)
        dOpt(
          score,
          _.history.score,
          (o: Score) => o.nonEmpty ?? { BSONHandlers.scoreWriter writeOpt o }
        )
      }
      case GameLogic.Go() => {
        dTry(oldPgn, _.pgnMoves, writeBytes compose ponStorageWriter)
        dTry(
          binaryPieces,
          _.board match {
            case Board.Go(b) => b.pieces
            case _           => sys.error("Wrong board type")
          },
          writeBytes compose BinaryFormat.piece.writeGo
        )
        d(positionHashes, _.history.positionHashes, w.bytes)
        d(historyLastMove, _.history.lastMove.map(_.uci) | "", w.str)
        dOpt(
          score,
          _.history.score,
          (o: Score) => o.nonEmpty ?? { BSONHandlers.scoreWriter writeOpt o }
        )
        if (a.variant.dropsVariant)
          dOpt(
            pocketData,
            _.board.pocketData,
            (o: Option[PocketData]) => o map BSONHandlers.pocketDataBSONHandler.write
          )
        dOptTry(
          selectedSquares,
          _.metadata.selectedSquares,
          //writeBytes compose BinaryFormat.pos.writeGo
          //(o: Option[List[Pos]]) => o.nonEmpty option { writeBytes compose BinaryFormat.pos.writeGo }
          (o: Option[List[Pos]]) => o.map { writeBytes compose BinaryFormat.pos.writeGo }
          //(o: KingMoves) => o.nonEmpty option { BSONHandlers.kingMovesWriter writeTry o }
        )
        dOpt(
          deadStoneOfferState,
          _.metadata.deadStoneOfferState.map(_.id),
          (o: Option[Int]) => o.map(w.int)
        )
      }
    }

    d(turns, _.turns, w.int)
    dOpt(moveTimes, _.binaryMoveTimes, (o: Option[ByteArray]) => o flatMap ByteArrayBSONHandler.writeOpt)
    dOpt(p1ClockHistory, getClockHistory(P1), clockHistoryToBytes)
    dOpt(p2ClockHistory, getClockHistory(P2), clockHistoryToBytes)
    dOpt(periodsP1, getPeriodEntries(P1), periodEntriesToBytes)
    dOpt(periodsP2, getPeriodEntries(P2), periodEntriesToBytes)
    dOpt(
      clock,
      _.clock,
      (o: Option[ClockBase]) =>
        o flatMap { c =>
          BSONHandlers.clockBSONWrite(a.createdAt, c).toOption
        }
    )
    dTry(drawOffers, _.drawOffers, BSONHandlers.gameDrawOffersHandler.writeTry)
    for (i <- 0 to 1) {
      import Player.BSONFields._
      val name                   = s"p$i."
      val player: Game => Player = if (i == 0) (_.p1Player) else (_.p2Player)
      dOpt(s"$name$isOfferingDraw", player(_).isOfferingDraw, w.boolO)
      dOpt(s"$name$isOfferingSelectSquares", player(_).isOfferingSelectSquares, w.boolO)
      dOpt(s"$name$proposeTakebackAt", player(_).proposeTakebackAt, w.intO)
      dTry(s"$name$blursBits", player(_).blurs, Blurs.BlursBSONHandler.writeTry)
    }
    dTry(movedAt, _.movedAt, BSONJodaDateTimeHandler.writeTry)

    (setBuilder.toList, unsetBuilder.toList)
  }

  private val bTrue = BSONBoolean(true)

  private val writeBytes = ByteArrayBSONHandler.writeTry _

  private def makeCastleLastMove(g: Game) =
    CastleLastMove(
      lastMove = g.history match {
        case History.Chess(h) => h.lastMove
        case _                => sys.error("Wrong history type")
      },
      castles = g.history.castles
    )
}
