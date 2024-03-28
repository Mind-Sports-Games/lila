package lila.pref

import reactivemongo.api.bson._

import lila.db.BSON
import lila.db.dsl._
import play.api.libs.json._
import lila.pref.PieceSet
import scala.util.{ Success }

private object PrefHandlers {

  implicit private[pref] val pieceSetBSONHandler = new BSON[PieceSet] {

    def reads(r: BSON.Reader) = new PieceSet(r.str("name"), r.int("gameFamily"))

    def writes(w: BSON.Writer, p: PieceSet) =
      $doc(
        "name"       -> p.name,
        "gameFamily" -> p.gameFamily
      )
  }

  implicit private[pref] val themeBSONHandler = new BSON[Theme] {

    def reads(r: BSON.Reader) = Theme.apply(r.str("name"), r.int("gameFamily"))

    def writes(w: BSON.Writer, t: Theme) =
      $doc(
        "name"       -> t.name,
        "gameFamily" -> t.gameFamily
      )
  }

  implicit val prefBSONHandler = new BSON[Pref] {

    def reads(r: BSON.Reader): Pref =
      Pref(
        _id = r str "_id",
        bg = r.getD("bg", Pref.default.bg),
        bgImg = r.strO("bgImg"),
        colour = r.getD("colour", Pref.default.colour),
        is3d = r.getD("is3d", Pref.default.is3d),
        theme = r.getsO[lila.pref.Theme]("theme") getOrElse Pref.default.theme,
        pieceSet = r.getsO[lila.pref.PieceSet]("pieceSet") getOrElse Pref.default.pieceSet,
        theme3d = r.getD("theme3d", Pref.default.theme3d),
        pieceSet3d = r.getD("pieceSet3d", Pref.default.pieceSet3d),
        soundSet = r.getD("soundSet", Pref.default.soundSet),
        blindfold = r.getD("blindfold", Pref.default.blindfold),
        autoQueen = r.getD("autoQueen", Pref.default.autoQueen),
        autoThreefold = r.getD("autoThreefold", Pref.default.autoThreefold),
        takeback = r.getD("takeback", Pref.default.takeback),
        moretime = r.getD("moretime", Pref.default.moretime),
        clockTenths = r.getD("clockTenths", Pref.default.clockTenths),
        clockBar = r.getD("clockBar", Pref.default.clockBar),
        clockSound = r.getD("clockSound", Pref.default.clockSound),
        premove = r.getD("premove", Pref.default.premove),
        animation = r.getD("animation", Pref.default.animation),
        captured = r.getD("captured", Pref.default.captured),
        follow = r.getD("follow", Pref.default.follow),
        highlight = r.getD("highlight", Pref.default.highlight),
        destination = r.getD("destination", Pref.default.destination),
        playerTurnIndicator = r.getD("playerTurnIndicator", Pref.default.playerTurnIndicator),
        coords = r.getD("coords", Pref.default.coords),
        replay = r.getD("replay", Pref.default.replay),
        gameResult = r.getD("gameResult", Pref.default.gameResult),
        coordSystem = r.getD("coordSystem", Pref.default.coordSystem),
        challenge = r.getD("challenge", Pref.default.challenge),
        message = r.getD("message", Pref.default.message),
        studyInvite = r.getD("studyInvite", Pref.default.studyInvite),
        coordPlayerIndex = r.getD("coordPlayerIndex", Pref.default.coordPlayerIndex),
        submitMove = r.getD("submitMove", Pref.default.submitMove),
        confirmResign = r.getD("confirmResign", Pref.default.confirmResign),
        confirmPass = r.getD("confirmPass", Pref.default.confirmPass),
        playForcedAction = r.getD("playForcedAction", Pref.default.playForcedAction),
        insightShare = r.getD("insightShare", Pref.default.insightShare),
        keyboardMove = r.getD("keyboardMove", Pref.default.keyboardMove),
        zen = r.getD("zen", Pref.default.zen),
        rookCastle = r.getD("rookCastle", Pref.default.rookCastle),
        pieceNotation = r.getD("pieceNotation", Pref.default.pieceNotation),
        resizeHandle = r.getD("resizeHandle", Pref.default.resizeHandle),
        moveEvent = r.getD("moveEvent", Pref.default.moveEvent),
        mancalaMove = r.getD("mancalaMove", Pref.default.mancalaMove),
        tags = r.getD("tags", Pref.default.tags)
      )

    def writes(w: BSON.Writer, o: Pref) =
      $doc(
        "_id"                 -> o._id,
        "bg"                  -> o.bg,
        "bgImg"               -> o.bgImg,
        "colour"              -> o.colour,
        "is3d"                -> o.is3d,
        "theme"               -> w.listO(o.theme),
        "pieceSet"            -> w.listO(o.pieceSet),
        "theme3d"             -> o.theme3d,
        "pieceSet3d"          -> o.pieceSet3d,
        "soundSet"            -> SoundSet.name2key(o.soundSet),
        "blindfold"           -> o.blindfold,
        "autoQueen"           -> o.autoQueen,
        "autoThreefold"       -> o.autoThreefold,
        "takeback"            -> o.takeback,
        "moretime"            -> o.moretime,
        "clockTenths"         -> o.clockTenths,
        "clockBar"            -> o.clockBar,
        "clockSound"          -> o.clockSound,
        "premove"             -> o.premove,
        "animation"           -> o.animation,
        "captured"            -> o.captured,
        "follow"              -> o.follow,
        "highlight"           -> o.highlight,
        "destination"         -> o.destination,
        "playerTurnIndicator" -> o.playerTurnIndicator,
        "coords"              -> o.coords,
        "replay"              -> o.replay,
        "gameResult"          -> o.gameResult,
        "coordSystem"         -> o.coordSystem,
        "challenge"           -> o.challenge,
        "message"             -> o.message,
        "studyInvite"         -> o.studyInvite,
        "coordPlayerIndex"    -> o.coordPlayerIndex,
        "submitMove"          -> o.submitMove,
        "confirmResign"       -> o.confirmResign,
        "confirmPass"         -> o.confirmPass,
        "playForcedAction"    -> o.playForcedAction,
        "insightShare"        -> o.insightShare,
        "keyboardMove"        -> o.keyboardMove,
        "zen"                 -> o.zen,
        "rookCastle"          -> o.rookCastle,
        "moveEvent"           -> o.moveEvent,
        "mancalaMove"         -> o.mancalaMove,
        "pieceNotation"       -> o.pieceNotation,
        "resizeHandle"        -> o.resizeHandle,
        "tags"                -> o.tags
      )
  }
}
