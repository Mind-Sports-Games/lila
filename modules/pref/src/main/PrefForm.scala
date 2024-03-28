package lila.pref

import play.api.data._
import play.api.data.Forms._

import lila.common.Form.{ numberIn, stringIn }

object PrefForm {

  private def containedIn(choices: Seq[(Int, String)]): Int => Boolean =
    choice => choices.exists(_._1 == choice)

  private def checkedNumber(choices: Seq[(Int, String)]) =
    number.verifying(containedIn(choices))

  private lazy val booleanNumber =
    number.verifying(Pref.BooleanPref.verify)

  val pref = Form(
    mapping(
      "display" -> mapping(
        "animation"           -> numberIn(Set(0, 1, 2, 3)),
        "captured"            -> booleanNumber,
        "highlight"           -> booleanNumber,
        "destination"         -> booleanNumber,
        "playerTurnIndicator" -> booleanNumber,
        "coords"              -> checkedNumber(Pref.Coords.choices),
        "replay"              -> checkedNumber(Pref.Replay.choices),
        //"gameResult"    -> checkedNumber(Pref.DraughtsGameResult.choices),
        "coordSystem"   -> checkedNumber(Pref.DraughtsCoordSystem.choices),
        "pieceNotation" -> optional(booleanNumber),
        "zen"           -> optional(booleanNumber),
        "resizeHandle"  -> optional(checkedNumber(Pref.ResizeHandle.choices)),
        "blindfold"     -> checkedNumber(Pref.Blindfold.choices)
      )(DisplayData.apply)(DisplayData.unapply),
      "behavior" -> mapping(
        "moveEvent"        -> optional(numberIn(Set(0, 1, 2))),
        "mancalaMove"      -> optional(booleanNumber),
        "premove"          -> booleanNumber,
        "takeback"         -> checkedNumber(Pref.Takeback.choices),
        "autoQueen"        -> checkedNumber(Pref.AutoQueen.choices),
        "autoThreefold"    -> checkedNumber(Pref.AutoThreefold.choices),
        "submitMove"       -> checkedNumber(Pref.SubmitMove.choices),
        "confirmResign"    -> checkedNumber(Pref.ConfirmResign.choices),
        "confirmPass"      -> checkedNumber(Pref.ConfirmPass.choices),
        "playForcedAction" -> checkedNumber(Pref.PlayForcedAction.choices),
        "keyboardMove"     -> optional(booleanNumber),
        "rookCastle"       -> optional(booleanNumber)
      )(BehaviorData.apply)(BehaviorData.unapply),
      "clock" -> mapping(
        "tenths"   -> checkedNumber(Pref.ClockTenths.choices),
        "bar"      -> booleanNumber,
        "sound"    -> booleanNumber,
        "moretime" -> checkedNumber(Pref.Moretime.choices)
      )(ClockData.apply)(ClockData.unapply),
      "follow"       -> booleanNumber,
      "challenge"    -> checkedNumber(Pref.Challenge.choices),
      "message"      -> checkedNumber(Pref.Message.choices),
      "studyInvite"  -> optional(checkedNumber(Pref.StudyInvite.choices)),
      "insightShare" -> numberIn(Set(0, 1, 2))
    )(PrefData.apply)(PrefData.unapply)
  )

  case class DisplayData(
      animation: Int,
      captured: Int,
      highlight: Int,
      destination: Int,
      playerTurnIndicator: Int,
      coords: Int,
      replay: Int,
      //gameResult: Int,
      coordSystem: Int,
      pieceNotation: Option[Int],
      zen: Option[Int],
      resizeHandle: Option[Int],
      blindfold: Int
  )

  case class BehaviorData(
      moveEvent: Option[Int],
      mancalaMove: Option[Int],
      premove: Int,
      takeback: Int,
      autoQueen: Int,
      autoThreefold: Int,
      submitMove: Int,
      confirmResign: Int,
      confirmPass: Int,
      playForcedAction: Int,
      keyboardMove: Option[Int],
      rookCastle: Option[Int]
  )

  case class ClockData(
      tenths: Int,
      bar: Int,
      sound: Int,
      moretime: Int
  )

  case class PrefData(
      display: DisplayData,
      behavior: BehaviorData,
      clock: ClockData,
      follow: Int,
      challenge: Int,
      message: Int,
      studyInvite: Option[Int],
      insightShare: Int
  ) {

    def apply(pref: Pref) =
      pref.copy(
        autoQueen = behavior.autoQueen,
        autoThreefold = behavior.autoThreefold,
        takeback = behavior.takeback,
        moretime = clock.moretime,
        clockTenths = clock.tenths,
        clockBar = clock.bar == 1,
        clockSound = clock.sound == 1,
        follow = follow == 1,
        highlight = display.highlight == 1,
        destination = display.destination == 1,
        playerTurnIndicator = display.playerTurnIndicator == 1,
        coords = display.coords,
        replay = display.replay,
        //gameResult = display.gameResult,
        coordSystem = display.coordSystem,
        blindfold = display.blindfold,
        challenge = challenge,
        message = message,
        studyInvite = studyInvite | Pref.default.studyInvite,
        premove = behavior.premove == 1,
        animation = display.animation,
        submitMove = behavior.submitMove,
        insightShare = insightShare,
        confirmResign = behavior.confirmResign,
        confirmPass = behavior.confirmPass,
        playForcedAction = behavior.playForcedAction,
        captured = display.captured == 1,
        keyboardMove = behavior.keyboardMove | pref.keyboardMove,
        zen = display.zen | pref.zen,
        resizeHandle = display.resizeHandle | pref.resizeHandle,
        rookCastle = behavior.rookCastle | pref.rookCastle,
        pieceNotation = display.pieceNotation | pref.pieceNotation,
        moveEvent = behavior.moveEvent | pref.moveEvent,
        mancalaMove = behavior.mancalaMove | pref.mancalaMove
      )
  }

  object PrefData {
    def apply(pref: Pref): PrefData =
      PrefData(
        display = DisplayData(
          highlight = if (pref.highlight) 1 else 0,
          destination = if (pref.destination) 1 else 0,
          playerTurnIndicator = if (pref.playerTurnIndicator) 1 else 0,
          animation = pref.animation,
          coords = pref.coords,
          replay = pref.replay,
          //gameResult = pref.gameResult,
          coordSystem = pref.coordSystem,
          captured = if (pref.captured) 1 else 0,
          blindfold = pref.blindfold,
          zen = pref.zen.some,
          resizeHandle = pref.resizeHandle.some,
          pieceNotation = pref.pieceNotation.some
        ),
        behavior = BehaviorData(
          moveEvent = pref.moveEvent.some,
          mancalaMove = pref.mancalaMove.some,
          premove = if (pref.premove) 1 else 0,
          takeback = pref.takeback,
          autoQueen = pref.autoQueen,
          autoThreefold = pref.autoThreefold,
          submitMove = pref.submitMove,
          confirmResign = pref.confirmResign,
          confirmPass = pref.confirmPass,
          playForcedAction = pref.playForcedAction,
          keyboardMove = pref.keyboardMove.some,
          rookCastle = pref.rookCastle.some
        ),
        clock = ClockData(
          tenths = pref.clockTenths,
          bar = if (pref.clockBar) 1 else 0,
          sound = if (pref.clockSound) 1 else 0,
          moretime = pref.moretime
        ),
        follow = if (pref.follow) 1 else 0,
        challenge = pref.challenge,
        message = pref.message,
        studyInvite = pref.studyInvite.some,
        insightShare = pref.insightShare
      )
  }

  def prefOf(p: Pref): Form[PrefData] = pref fill PrefData(p)

  val theme = Form(
    single(
      "theme" -> text.verifying(Theme contains _)
    )
  )

  val pieceSet = Form(
    single(
      "set" -> text.verifying(PieceSet contains _)
    )
  )

  val theme3d = Form(
    single(
      "theme" -> text.verifying(Theme3d contains _)
    )
  )

  val pieceSet3d = Form(
    single(
      "set" -> text.verifying(PieceSet3d contains _)
    )
  )

  val soundSet = Form(
    single(
      "set" -> text.verifying(SoundSet contains _)
    )
  )

  val bg = Form(
    single(
      "bg" -> stringIn(Pref.Bg.fromString.keySet)
    )
  )

  val bgImg = Form(
    single(
      "bgImg" -> nonEmptyText(minLength = 10, maxLength = 400)
        .verifying { url =>
          url.startsWith("https://") || url.startsWith("//")
        }
    )
  )

  val colour = Form(
    single(
      "colour" -> stringIn(Pref.Colour.fromString.keySet)
    )
  )

  val is3d = Form(
    single(
      "is3d" -> text.verifying(List("true", "false") contains _)
    )
  )

  val zen = Form(
    single(
      "zen" -> text.verifying(Set("0", "1") contains _)
    )
  )
}
