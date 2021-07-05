package lila.socket

import play.api.libs.json._

import strategygames.chess.format.FEN
import strategygames.chess.opening._
import strategygames.chess.variant.Variant
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
      val sit = strategygames.chess.Game(variant.some, fen.some).situation
      sit.playable(false) ?? destString(sit.destinations)
    }

  lazy val opening = Variant.openingSensibleVariants(variant) ?? {
    FullOpeningDB findByFen fen
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
      variant = strategygames.chess.variant.Variant orDefault ~d.str("variant")
      fen  <- d str "fen"
      path <- d str "path"
    } yield AnaDests(variant = variant, fen = FEN(fen), path = path, chapterId = d str "ch")
}
