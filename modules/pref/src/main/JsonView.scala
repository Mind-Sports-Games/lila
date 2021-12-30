package lila.pref

import play.api.libs.json._
import play.api.libs.functional.syntax._

object JsonView {

  implicit val prefJsonWriter = OWrites[Pref] { p =>
    Json.obj(
      "dark"          -> (p.bg != Pref.Bg.LIGHT),
      "transp"        -> (p.bg == Pref.Bg.TRANSPARENT),
      "bgImg"         -> p.bgImgOrDefault,
      "is3d"          -> p.is3d,
      "theme"         -> p.theme,
      "pieceSet"      -> p.pieceSet.map( p => Json.obj( "name" -> p.name,
                                                        "gameFamily" -> p.gameFamily)),
      "theme3d"       -> p.theme3d,
      "pieceSet3d"    -> p.pieceSet3d,
      "soundSet"      -> p.soundSet,
      "blindfold"     -> p.blindfold,
      "autoQueen"     -> p.autoQueen,
      "autoThreefold" -> p.autoThreefold,
      "takeback"      -> p.takeback,
      "moretime"      -> p.moretime,
      "clockTenths"   -> p.clockTenths,
      "clockBar"      -> p.clockBar,
      "clockSound"    -> p.clockSound,
      "premove"       -> p.premove,
      "animation"     -> p.animation,
      "captured"      -> p.captured,
      "follow"        -> p.follow,
      "highlight"     -> p.highlight,
      "destination"   -> p.destination,
      "coords"        -> p.coords,
      "replay"        -> p.replay,
      "challenge"     -> p.challenge,
      "message"       -> p.message,
      "coordSGPlayer"    -> p.coordSGPlayer,
      "submitMove"    -> p.submitMove,
      "confirmResign" -> p.confirmResign,
      "insightShare"  -> p.insightShare,
      "keyboardMove"  -> p.keyboardMove,
      "zen"           -> p.zen,
      "moveEvent"     -> p.moveEvent,
      "rookCastle"    -> p.rookCastle
    )
  }

  implicit val pieceSetJsonWrites: Writes[PieceSet] = (
    (JsPath \ "name").write[String] and 
    (JsPath \ "gameFamily").write[Int]
  )(unlift(PieceSet.unapply))

  implicit val pieceSetJsonReads: Reads[PieceSet] = (
    (JsPath \ "name").read[String] and 
    (JsPath \ "gameFamily").read[Int]
  )(PieceSet.apply _)
  
  implicit val pieceSetFormat: Format[PieceSet] =
    Format(pieceSetJsonReads, pieceSetJsonWrites)

  implicit val pieceSetsRead: Reads[List[PieceSet]] = Reads.list(pieceSetFormat)

  implicit val pieceSetsWrite: Writes[List[PieceSet]] = Writes.list(pieceSetFormat)

}
