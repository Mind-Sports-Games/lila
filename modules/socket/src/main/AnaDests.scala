package lila.socket

import scala.collection.MapView

import play.api.libs.json._

import strategygames.format.FEN
import strategygames.variant.Variant
import strategygames.opening.FullOpeningDB
import strategygames.{ Board, Game, GameLogic, Move, Pos, Situation }
import lila.tree.Node.{ destString, openingWriter }

//We think this code is deprecated and never used!
//Look in lila-ws for the new version (Chess.scala)
//Check to see if the pp has ever been called
case class AnaDests(
    variant: Variant,
    fen: FEN,
    path: String,
    chapterId: Option[String],
    lastUci: Option[String] = None,
    fullCapture: Option[Boolean] = None
) {

  def isInitial = variant.standardVariant && fen.initial && path == ""

  private lazy val sit = Game(variant.gameLogic, variant.some, fen.some).situation

  //draughts
  private val orig: Option[strategygames.draughts.Pos] =
    (sit, variant) match {
      case (Situation.Draughts(sit), Variant.Draughts(variant)) =>
        (lastUci.exists(_.length >= 4) && sit.ghosts > 0) ?? lastUci.flatMap { uci =>
          variant.boardSize.pos.posAt(uci.substring(uci.length - 2))
        }
      case _ => None
    }

  //draughts
  private lazy val validMoves =
    AnaDests.validMoves(sit, orig, ~fullCapture)

  //draughts
  lazy val captureLength: Int = sit match {
    case Situation.Draughts(sit) =>
      orig.fold(sit.allMovesCaptureLength)(~sit.captureLengthFrom(_))
    case _ => 0
  }

  //draughts
  private val truncatedMoves: Option[MapView[strategygames.draughts.Pos, List[String]]] =
    (!isInitial && ~fullCapture && captureLength > 1) option AnaDests.truncateMoves(validMoves)

  val dests: String = variant match {
    case Variant.Chess(_) =>
      if (isInitial) AnaDests.initialChessDests
      else sit.playable(false) ?? destString(sit.destinations)
    case Variant.Draughts(variant) =>
      if (isInitial) AnaDests.initialDraughtsDests
      else
        sit.playable(false) ?? {
          val truncatedDests = truncatedMoves.map {
            _ mapValues { _ flatMap (uci => variant.boardSize.pos.posAt(uci.takeRight(2))) }
          }
          val destsToConvert: Map[Pos, List[Pos]] =
            truncatedDests
              .getOrElse(validMoves.view.mapValues { _ map (_.dest) })
              .to(Map)
              .map { case (p, lp) => (Pos.Draughts(p), lp.map(Pos.Draughts)) }
          val destStr = destString(destsToConvert)
          if (captureLength > 0) s"#$captureLength $destStr"
          else destStr
        }
  }

  //draughts
  val destsUci: Option[List[String]] = truncatedMoves.map(_.values.toList.flatten)

  lazy val opening = Variant.openingSensibleVariants(variant.gameLogic)(variant) ?? {
    FullOpeningDB.findByFen(variant.gameLogic, fen)
  }

  def json =
    Json
      .obj(
        "dests" -> dests,
        "path"  -> path
      )
      .add("opening" -> opening)
      .add("ch", chapterId)
      .add("destsUci", destsUci)
}

object AnaDests {

  private val initialChessDests    = "iqy muC gvx ltB bqs pxF jrz nvD ksA owE"
  private val initialDraughtsDests = "HCD GBC ID FAB EzA"

  //draughts
  private type BoardWithUci = (Option[strategygames.draughts.Board], String)

  //draughts
  private def uniqueUci(otherUcis: List[BoardWithUci], uci: BoardWithUci) = {
    var i      = 2
    var unique = uci._2.slice(0, i)
    while (i + 2 <= uci._2.length && otherUcis.exists(_._2.startsWith(unique))) {
      i += 2
      unique = uci._2.slice(0, i)
    }
    if (i == uci._2.length) uci
    else if (i == 2) (none, uci._2.slice(0, 4))
    else (none, unique)
  }

  //draughts
  def validMoves(
      sit: Situation,
      from: Option[strategygames.draughts.Pos],
      fullCapture: Boolean
  ): Map[strategygames.draughts.Pos, List[strategygames.draughts.Move]] = sit match {
    case Situation.Draughts(sit) =>
      from.fold(if (fullCapture) sit.validMovesFinal else sit.validMoves) { pos =>
        Map(pos -> sit.movesFrom(pos, fullCapture))
      }
    case _ => Map.empty[strategygames.draughts.Pos, List[strategygames.draughts.Move]]
  }

  //draughts
  def truncateMoves(
      validMoves: Map[strategygames.draughts.Pos, List[strategygames.draughts.Move]]
  ): MapView[strategygames.draughts.Pos, List[String]] = {
    var truncated = false
    val truncatedMoves = validMoves map { case (pos, moves) =>
      if (moves.size <= 1) pos -> moves.map(m => (m.after.some, m.toUci.uci))
      else
        pos -> moves.foldLeft(List[BoardWithUci]()) { (acc, move) =>
          val sameDestUcis = moves
            .filter(m => m != move && m.dest == move.dest && (m.orig == m.dest || m.after != move.after))
            .map(m => (m.after.some, m.toUci.uci))
          val uci = (move.after.some, move.toUci.uci)
          val newUci =
            if (sameDestUcis.isEmpty && move.orig != move.dest) uci else uniqueUci(sameDestUcis, uci)
          if (!acc.contains(newUci)) {
            if (newUci._2.length != uci._2.length) truncated = true
            newUci :: acc
          } else {
            truncated = true
            acc
          }
        }
    }
    (if (truncated) truncateUcis(truncatedMoves) else truncatedMoves).view.mapValues { _ map { _._2 } }
  }

  //draughts
  @scala.annotation.tailrec
  private def truncateUcis(
      validUcis: Map[strategygames.draughts.Pos, List[BoardWithUci]]
  ): Map[strategygames.draughts.Pos, List[BoardWithUci]] = {
    var truncated = false
    val truncatedUcis = validUcis map { case (pos, uciList) =>
      if (uciList.size <= 1) pos -> uciList
      else
        pos -> uciList.foldLeft(List[BoardWithUci]()) { (acc, uci) =>
          val dest = uci._2.takeRight(2)
          val sameDestUcis = uciList.filter(u =>
            u != uci && u._2.takeRight(2) == dest && (u._2.startsWith(
              dest
            ) || (u._1.isEmpty && uci._1.isEmpty) || u._1 != uci._1)
          )
          val newUci = if (sameDestUcis.isEmpty) uci else uniqueUci(sameDestUcis, uci)
          if (!acc.contains(newUci)) {
            if (newUci._2.length != uci._2.length) truncated = true
            newUci :: acc
          } else {
            truncated = true
            acc
          }
        }
    }
    if (truncated) truncateUcis(truncatedUcis)
    else truncatedUcis
  }

  def parse(o: JsObject) =
    for {
      d   <- o obj "d"
      lib <- d int "lib"
      variant = Variant.orDefault(GameLogic(lib), ~d.str("variant"))
      fen  <- d str "fen"
      path <- d str "path"
    } yield AnaDests(
      variant = variant,
      fen = FEN(GameLogic(lib), fen),
      path = path,
      chapterId = d str "ch",
      fullCapture = d boolean "fullCapture"
    )
}
