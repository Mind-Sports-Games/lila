package lila.socket

import play.api.libs.json._

import strategygames.format.FEN
import strategygames.chess.opening._
import strategygames.variant.Variant
import strategygames.{ Game, GameLib }
import lila.tree.Node.{ destString, openingWriter }

case class AnaDests(
    variant: Variant,
    fen: FEN,
    path: String,
    chapterId: Option[String]
) {

  def isInitial = variant.standard && fen.initial && path == ""

  val dests: String =
    if (isInitial) AnaDests.initialDests
    else {
      val sit = Game(GameLib.Chess(), variant.some, fen.some).situation
      sit.playable(false) ?? destString(sit.destinations)
    }

  lazy val opening = Variant.openingSensibleVariants(GameLib.Chess())(variant) ?? {
    fen match {
      case FEN.Chess(fen) => FullOpeningDB findByFen fen
      case _ => sys.error("Invalid fen lib")
    }
  }

  def json =
    Json
      .obj(
        "dests" -> dests,
        "path"  -> path
      )
      .add("opening" -> opening)
      .add("ch", chapterId)
}

object AnaDests {

  private val initialDests = "iqy muC gvx ltB bqs pxF jrz nvD ksA owE"

  def parse(o: JsObject) =
    for {
      d <- o obj "d"
      variant = Variant.orDefault(GameLib.Chess(), ~d.str("variant"))
      fen  <- d str "fen"
      path <- d str "path"
    } yield AnaDests(
      variant = variant,
      fen = FEN.Chess(strategygames.chess.format.FEN.apply(fen)),
      path = path,
      chapterId = d str "ch"
    )
}
