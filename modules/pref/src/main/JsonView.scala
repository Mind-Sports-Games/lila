package lila.pref

import play.api.libs.json._
import play.api.libs.functional.syntax._

object JsonView {

  implicit val prefJsonWriter = OWrites[Pref] { p =>
    Json.obj(
      "dark"                -> (p.bg != Pref.Bg.LIGHT),
      "transp"              -> (p.bg == Pref.Bg.TRANSPARENT),
      "bgImg"               -> p.bgImgOrDefault,
      "color"               -> p.color,
      "is3d"                -> p.is3d,
      "theme"               -> p.theme.map(p => Json.obj("name" -> p.name, "gameFamily" -> p.gameFamily)),
      "pieceSet"            -> p.pieceSet.map(p => Json.obj("name" -> p.name, "gameFamily" -> p.gameFamily)),
      "theme3d"             -> p.theme3d,
      "pieceSet3d"          -> p.pieceSet3d,
      "soundSet"            -> p.soundSet,
      "blindfold"           -> p.blindfold,
      "autoQueen"           -> p.autoQueen,
      "autoThreefold"       -> p.autoThreefold,
      "takeback"            -> p.takeback,
      "moretime"            -> p.moretime,
      "clockTenths"         -> p.clockTenths,
      "clockBar"            -> p.clockBar,
      "clockSound"          -> p.clockSound,
      "premove"             -> p.premove,
      "animation"           -> p.animation,
      "captured"            -> p.captured,
      "follow"              -> p.follow,
      "highlight"           -> p.highlight,
      "destination"         -> p.destination,
      "playerTurnIndicator" -> p.playerTurnIndicator,
      "coords"              -> p.coords,
      "replay"              -> p.replay,
      "challenge"           -> p.challenge,
      "message"             -> p.message,
      "coordPlayerIndex"    -> p.coordPlayerIndex,
      "submitMove"          -> p.submitMove,
      "confirmResign"       -> p.confirmResign,
      "confirmPass"         -> p.confirmPass,
      "playForcedAction"    -> p.playForcedAction,
      "insightShare"        -> p.insightShare,
      "keyboardMove"        -> p.keyboardMove,
      "zen"                 -> p.zen,
      "moveEvent"           -> p.moveEvent,
      "mancalaMove"         -> p.mancalaMove,
      "rookCastle"          -> p.rookCastle
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

  implicit val themeJsonWrites: Writes[Theme] = (
    (JsPath \ "name").write[String] and
      (JsPath \ "gameFamily").write[Int]
  )(unlift(Theme.unapply))

  implicit val themeJsonReads: Reads[Theme] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "gameFamily").read[Int]
  )(Theme.apply _)

  implicit val themeFormat: Format[Theme] =
    Format(themeJsonReads, themeJsonWrites)

  implicit val themesRead: Reads[List[Theme]] = Reads.list(themeFormat)

  implicit val themesWrite: Writes[List[Theme]] = Writes.list(themeFormat)
}
