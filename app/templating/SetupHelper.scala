package lila.app
package templating

import strategygames.{ GameFamily, Mode, Speed }
import strategygames.variant.Variant
import play.api.i18n.Lang

import lila.i18n.{ I18nKeys => trans, VariantKeys }
import lila.pref.Pref
import lila.report.Reason
import lila.setup.TimeMode
import lila.common.LightUser

trait SetupHelper { self: I18nHelper =>

  type SelectChoice = (String, String, Option[String])

  val clockTimeChoices: List[SelectChoice] = List(
    ("0", "0", none),
    ("0.25", "¼", none),
    ("0.5", "½", none),
    ("0.75", "¾", none)
  ) ::: List(
    "1",
    "1.5",
    "2",
    "3",
    "4",
    "5",
    "6",
    "7",
    "8",
    "9",
    "10",
    "11",
    "12",
    "13",
    "14",
    "15",
    "16",
    "17",
    "18",
    "19",
    "20",
    "25",
    "30",
    "35",
    "40",
    "45",
    "60",
    "75",
    "90",
    "105",
    "120",
    "135",
    "150",
    "165",
    "180"
  ).map { v =>
    (v, v, none)
  }

  val clockIncrementChoices: List[SelectChoice] = {
    (0 to 20).toList ::: List(25, 30, 35, 40, 45, 60, 90, 120, 150, 180)
  } map { s =>
    (s.toString, s.toString, none)
  }

  val clockByoyomiChoices: List[SelectChoice] = {
    (1 to 20).toList ::: List(25, 30, 35, 40, 45, 60, 90, 120, 150, 180)
  } map { s =>
    (s.toString, s.toString, none)
  }

  val periodsChoices: List[SelectChoice] = {
    (1 to 5).toList map { s =>
      (s.toString, s.toString, none)
    }
  }

  val corresDaysChoices: List[SelectChoice] =
    ("1", "One day", none) :: List(2, 3, 5, 7, 10, 14).map { d =>
      (d.toString, s"$d days", none)
    }

  def translatedTimeModeChoices(implicit lang: Lang) =
    List(
      (TimeMode.FischerClock.id.toString, trans.realTime.txt(), none),
      (TimeMode.ByoyomiClock.id.toString, trans.byoyomiTime.txt(), none),
      (TimeMode.BronsteinDelayClock.id.toString, trans.bronsteinDelay.txt(), none),
      (TimeMode.SimpleDelayClock.id.toString, trans.simpleDelay.txt(), none),
      (TimeMode.Correspondence.id.toString, trans.correspondence.txt(), none),
      (TimeMode.Unlimited.id.toString, trans.unlimited.txt(), none)
    )

  def translatedTimeModeChoicesLive(implicit lang: Lang) =
    List(
      (TimeMode.FischerClock.id.toString, trans.realTime.txt(), none),
      (TimeMode.ByoyomiClock.id.toString, trans.byoyomiTime.txt(), none),
      (TimeMode.BronsteinDelayClock.id.toString, trans.bronsteinDelay.txt(), none),
      (TimeMode.SimpleDelayClock.id.toString, trans.simpleDelay.txt(), none)
    )

  val goHandicapChoices: List[SelectChoice] = {
    (0 to 9).toList map { s =>
      (s.toString, s.toString, none)
    }
  }

  val goKomiChoices: List[SelectChoice] = {
    (-100 to 100 by 5).toList map { s =>
      (s.toString, (s / 10.0).toString.replace(".0", ""), none)
    }
  }

  val backgammonPointChoices: List[SelectChoice] = {
    (1 to 31 by 2).toList map { s =>
      (s.toString, s.toString, none)
    }
  }

  def translatedReasonChoices(implicit lang: Lang) =
    List(
      (Reason.Cheat.key, trans.cheat.txt()),
      (Reason.Comm.key, trans.insult.txt()),
      (Reason.Boost.key, trans.ratingManipulation.txt()),
      (Reason.Comm.key, trans.troll.txt()),
      (Reason.Other.key, trans.other.txt())
    )

  def translatedModeChoices(implicit lang: Lang) =
    List(
      (Mode.Casual.id.toString, trans.casual.txt(), none),
      (Mode.Rated.id.toString, trans.rated.txt(), none)
    )

  def translatedModeIconChoices(implicit lang: Lang): List[SelectChoice] =
    List(
      (Mode.Casual.id.toString, "\uE92A", trans.casual.txt().some),
      (Mode.Rated.id.toString, "\uE92B", trans.rated.txt().some)
    )

  def translatedTimeModeIconChoices(implicit lang: Lang): List[SelectChoice] =
    List(
      ("bullet", "\u0054", "1+0".some),
      ("blitz", "\u0029", "3+2".some),
      ("rapid", "\u0023", "5+5".some),
      ("classical", "\u002B", "20+10".some),
      ("correspondence", "\u003B", "2 days".some),
      ("custom", "\u006E", "Custom".some)
    )

  def translatedIncrementChoices(implicit lang: Lang) =
    List(
      (1, trans.yes.txt(), none),
      (0, trans.no.txt(), none)
    )

  def translatedModeChoicesTournament(implicit lang: Lang) =
    List(
      (Mode.Casual.id.toString, trans.casualTournament.txt(), none),
      (Mode.Rated.id.toString, trans.ratedTournament.txt(), none)
    )

  private val encodeId           = (v: Variant) => v.id.toString
  private val encodeGameFamilyId = (lib: GameFamily) => lib.id.toString

  private def variantTupleId = variantTuple(encodeId) _

  private def variantTuple(
      encode: Variant => String,
      variantName: Variant => String = VariantKeys.variantName(_)
  )(variant: Variant) =
    (encode(variant), variantName(variant), VariantKeys.variantTitle(variant).some)

  def translatedGameFamilyIconChoices(implicit lang: Lang): List[SelectChoice] =
    GameFamily.all.map(translatedGameFamilyIconChoice(_))

  private def translatedGameFamilyIconChoice(
      gameFamily: GameFamily
  )(implicit lang: Lang): SelectChoice =
    (
      encodeGameFamilyId(gameFamily),
      gameFamily.defaultVariant.perfIcon.toString(),
      VariantKeys.gameFamilyName(gameFamily).some
    )

  def translatedVariantIconChoices(implicit lang: Lang): List[SelectChoice] =
    Variant.all.map(translatedVariantIconChoice(_))

  private def translatedVariantIconChoice(
      variant: Variant
  )(implicit lang: Lang): SelectChoice =
    (
      s"${encodeGameFamilyId(variant.gameFamily)}_${encodeId(variant)}",
      variant.perfIcon.toString(),
      VariantKeys.variantName(variant).some
    )

  def translatedGameFamilyChoices(implicit lang: Lang): List[SelectChoice] =
    GameFamily.all.map(translatedGameFamilyChoice(_))

  private def translatedGameFamilyChoice(
      gameFamily: GameFamily
  )(implicit lang: Lang): SelectChoice =
    (
      encodeGameFamilyId(gameFamily),
      VariantKeys.gameFamilyName(gameFamily),
      VariantKeys.gameFamilyName(gameFamily).some
    )

  def translatedVariantChoices(
      encode: Variant => String = encodeId
  )(implicit lang: Lang): List[(SelectChoice, List[SelectChoice])] =
    GameFamily.all.map(gf =>
      (
        translatedGameFamilyChoice(gf),
        translatedVariantChoicesByGameFamily(gf, encode)
      )
    )

  private def translatedVariantChoicesByGameFamily(
      gameFamily: GameFamily,
      encode: Variant => String
  )(implicit lang: Lang): List[SelectChoice] =
    List(gameFamily.defaultVariant).map(variantTuple(encode))

  def translatedVariantChoicesWithVariants(implicit
      lang: Lang
  ): List[(SelectChoice, List[SelectChoice])] =
    translatedVariantChoicesWithVariants(encodeId)

  def translatedVariantChoicesWithVariants(
      encode: Variant => String = encodeId
  )(implicit lang: Lang): List[(SelectChoice, List[SelectChoice])] =
    GameFamily.all.map(gf =>
      (
        translatedGameFamilyChoice(gf),
        translatedVariantChoicesWithVariantsByGameFamily(gf, encode)
      )
    )

  def translatedAllVariantChoicesWithVariants(
      encode: Variant => String
  )(implicit lang: Lang): List[SelectChoice] =
    GameFamily.all
      .map(
        translatedVariantChoicesWithVariantsByGameFamily(_, encode)
      )
      .flatten

  def translatedVariantChoicesWithVariantsByGameFamily(
      gameFamily: GameFamily,
      encode: Variant => String = encodeId
  )(implicit lang: Lang): List[SelectChoice] =
    translatedVariantChoicesByGameFamily(gameFamily, encode) :::
      gameFamily.variants
        .filter(v => v != gameFamily.defaultVariant && !v.fromPositionVariant)
        .map(variantTuple(encode))

  def translatedVariantChoicesWithFen(implicit
      lang: Lang
  ): List[(SelectChoice, List[SelectChoice])] =
    GameFamily.all.map(gf =>
      (
        translatedGameFamilyChoice(gf),
        translatedVariantChoicesByGameFamily(gf, encodeId) :::
          gf.variants.filter(v => v.fenVariant || v.fromPositionVariant).map(variantTupleId)
      )
    )

  def translatedAiVariantChoices(implicit
      lang: Lang
  ): List[(SelectChoice, List[SelectChoice])] =
    GameFamily.all
      .filter(_.hasFishnet)
      .map(gf =>
        (
          translatedGameFamilyChoice(gf),
          translatedVariantChoicesByGameFamily(gf, encodeId) :::
            gf.variants
              .filter(v => v != gf.defaultVariant && !v.fromPositionVariant && v.hasFishnet)
              .map(variantTupleId) ::: gf.variants
              .filter(
                _.fromPositionVariant
              )
              .map(variantTupleId)
        )
      )

  def translatedVariantChoicesWithVariantsAndFen(implicit
      lang: Lang
  ): List[(SelectChoice, List[SelectChoice])] =
    GameFamily.all.map(gf =>
      (
        translatedGameFamilyChoice(gf),
        translatedVariantChoicesWithVariantsByGameFamily(gf, encodeId) :::
          gf.variants.filter(_.fromPositionVariant).map(variantTupleId)
      )
    )

  def translatedGreedyFourMoveChoices(implicit lang: Lang): List[(SelectChoice, List[SelectChoice])] =
    GameFamily.all.map(gf =>
      (
        translatedGameFamilyChoice(gf),
        translatedVariantChoicesWithVariantsByGameFamily(gf, encodeId) :::
          gf.variants.filter(_.fromPositionVariant).map(variantTupleId)
      )
    )

  //used in lidraughts but not in lila (yet?)
  //private def fromPositionVariantTupleId(v: Variant)(implicit lang: Lang) =
  //  variantTuple(encodeId, v => fromPositionVariantName(VariantKeys.variantName(v)))(v)

  //private def fromPositionVariantName(variantName: String) =
  //  s"From Position | ${variantName}"

  //def translatedDraughtsFromPositionVariantChoices(implicit lang: Lang) =
  //  List((
  //    encodeId(GameFamily.Draughts().defaultVariant),
  //    fromPositionVariantName(GameFamily.Draughts().defaultVariant.name),
  //    GameFamily.Draughts().defaultVariant.name.some
  //  )) ::: GameFamily.Draughts().variants.filter(_.fenVariant).map(fromPositionVariantTupleId)

  def translatedSpeedChoices(implicit lang: Lang) =
    Speed.limited map { s =>
      val minutes = s.range.max / 60 + 1
      (
        s.id.toString,
        s.toString + " - " + trans.lessThanNbMinutes.pluralSameTxt(minutes),
        none
      )
    }

  def translatedSideChoices(implicit lang: Lang) =
    List(
      ("p2", trans.p2.txt(), none),
      ("random", trans.randomColor.txt(), none),
      ("p1", trans.p1.txt(), none)
    )

  //TODO add to trans when complete
  def translatedOpponentChoices(implicit lang: Lang): List[SelectChoice] =
    List(
      ("lobby", "\u0066", "lobby".some),
      ("friend", "\u0072", "friend".some),
      ("bot", "\uE933", "bot".some)
    )

  def translatedBotChoices(implicit lang: Lang): List[SelectChoice] =
    LightUser.lobbyBotsIDs.map { id => (id.toString(), id.toString(), none) }

  def translatedAnimationChoices(implicit lang: Lang) =
    List(
      (Pref.Animation.NONE, trans.none.txt()),
      (Pref.Animation.FAST, trans.fast.txt()),
      (Pref.Animation.NORMAL, trans.normal.txt()),
      (Pref.Animation.SLOW, trans.slow.txt())
    )

  def translatedBoardCoordinateChoices(implicit lang: Lang) =
    List(
      (Pref.Coords.NONE, trans.no.txt()),
      (Pref.Coords.INSIDE, trans.insideTheBoard.txt()),
      (Pref.Coords.OUTSIDE, trans.outsideTheBoard.txt())
    )

  def translatedCoordinateSystemChoices(implicit lang: Lang) =
    List(
      (Pref.DraughtsCoordSystem.FIELDNUMBERS, trans.fieldnumbers8x8.txt()),
      (Pref.DraughtsCoordSystem.ALGEBRAIC, trans.algebraic8x8.txt())
    )

  def translatedMoveListWhilePlayingChoices(implicit lang: Lang) =
    List(
      (Pref.Replay.NEVER, trans.never.txt()),
      (Pref.Replay.SLOW, trans.onSlowGames.txt()),
      (Pref.Replay.ALWAYS, trans.always.txt())
    )

  def translatedPieceNotationChoices(implicit lang: Lang) =
    List(
      (Pref.PieceNotation.SYMBOL, trans.preferences.chessPieceSymbol.txt()),
      (Pref.PieceNotation.LETTER, trans.preferences.pgnLetter.txt())
    )

  def translatedClockTenthsChoices(implicit lang: Lang) =
    List(
      (Pref.ClockTenths.NEVER, trans.never.txt()),
      (Pref.ClockTenths.LOWTIME, trans.preferences.whenTimeRemainingLessThanTenSeconds.txt()),
      (Pref.ClockTenths.ALWAYS, trans.always.txt())
    )

  def translatedMoveEventChoices(implicit lang: Lang) =
    List(
      (Pref.MoveEvent.CLICK, trans.preferences.clickTwoSquares.txt()),
      (Pref.MoveEvent.DRAG, trans.preferences.dragPiece.txt()),
      (Pref.MoveEvent.BOTH, trans.preferences.bothClicksAndDrag.txt())
    )

  def translatedMancalaMoveChoices(implicit lang: Lang) =
    List(
      (Pref.MancalaMove.SINGLE_CLICK, trans.preferences.singleClick.txt()),
      (Pref.MancalaMove.TWO_HOUSE_CLICK, trans.preferences.clickTwoHouses.txt())
    )

  def translatedTakebackChoices(implicit lang: Lang) =
    List(
      (Pref.Takeback.NEVER, trans.never.txt()),
      (Pref.Takeback.ALWAYS, trans.always.txt()),
      (Pref.Takeback.CASUAL, trans.preferences.inCasualGamesOnly.txt())
    )

  def translatedMoretimeChoices(implicit lang: Lang) =
    List(
      (Pref.Moretime.NEVER, trans.never.txt()),
      (Pref.Moretime.ALWAYS, trans.always.txt()),
      (Pref.Moretime.CASUAL, trans.preferences.inCasualGamesOnly.txt())
    )

  def translatedAutoQueenChoices(implicit lang: Lang) =
    List(
      (Pref.AutoQueen.NEVER, trans.never.txt()),
      (Pref.AutoQueen.PREMOVE, trans.preferences.whenPremoving.txt()),
      (Pref.AutoQueen.ALWAYS, trans.always.txt())
    )

  def translatedAutoThreefoldChoices(implicit lang: Lang) =
    List(
      (Pref.AutoThreefold.NEVER, trans.never.txt()),
      (Pref.AutoThreefold.ALWAYS, trans.always.txt()),
      (Pref.AutoThreefold.TIME, trans.preferences.whenTimeRemainingLessThanThirtySeconds.txt())
    )

  def submitMoveChoices(implicit lang: Lang) =
    List(
      (Pref.SubmitMove.NEVER, trans.never.txt()),
      (Pref.SubmitMove.CORRESPONDENCE_ONLY, trans.preferences.inCorrespondenceGames.txt()),
      (Pref.SubmitMove.CORRESPONDENCE_UNLIMITED, trans.preferences.correspondenceAndUnlimited.txt()),
      (Pref.SubmitMove.ALWAYS, trans.always.txt())
    )

  def confirmResignChoices(implicit lang: Lang) =
    List(
      (Pref.ConfirmResign.NO, trans.no.txt()),
      (Pref.ConfirmResign.YES, trans.yes.txt())
    )

  def confirmPassChoices(implicit lang: Lang) =
    List(
      (Pref.ConfirmPass.NO, trans.no.txt()),
      (Pref.ConfirmPass.YES, trans.yes.txt())
    )

  def playForcedActionChoices(implicit lang: Lang) =
    List(
      (Pref.PlayForcedAction.NO, trans.no.txt()),
      (Pref.PlayForcedAction.YES, trans.yes.txt())
    )

  def translatedRookCastleChoices(implicit lang: Lang) =
    List(
      (Pref.RookCastle.NO, trans.preferences.castleByMovingTwoSquares.txt()),
      (Pref.RookCastle.YES, trans.preferences.castleByMovingOntoTheRook.txt())
    )

  def translatedChallengeChoices(implicit lang: Lang) =
    List(
      (Pref.Challenge.NEVER, trans.never.txt()),
      (
        Pref.Challenge.RATING,
        trans.ifRatingIsPlusMinusX.txt(lila.pref.Pref.Challenge.ratingThreshold)
      ),
      (Pref.Challenge.FRIEND, trans.onlyFriends.txt()),
      (Pref.Challenge.ALWAYS, trans.always.txt())
    )

  def translatedMessageChoices(implicit lang: Lang) =
    List(
      (Pref.Message.NEVER, trans.onlyExistingConversations.txt()),
      (Pref.Message.FRIEND, trans.onlyFriends.txt()),
      (Pref.Message.ALWAYS, trans.always.txt())
    )

  def translatedStudyInviteChoices(implicit lang: Lang) = privacyBaseChoices
  def translatedPalantirChoices(implicit lang: Lang)    = privacyBaseChoices
  private def privacyBaseChoices(implicit lang: Lang) =
    List(
      (Pref.StudyInvite.NEVER, trans.never.txt()),
      (Pref.StudyInvite.FRIEND, trans.onlyFriends.txt()),
      (Pref.StudyInvite.ALWAYS, trans.always.txt())
    )

  def translatedInsightShareChoices(implicit lang: Lang) =
    List(
      (Pref.InsightShare.NOBODY, trans.withNobody.txt()),
      (Pref.InsightShare.FRIENDS, trans.withFriends.txt()),
      (Pref.InsightShare.EVERYBODY, trans.withEverybody.txt())
    )

  def translatedBoardResizeHandleChoices(implicit lang: Lang) =
    List(
      (Pref.ResizeHandle.NEVER, trans.never.txt()),
      (Pref.ResizeHandle.INITIAL, trans.preferences.onlyOnInitialPosition.txt()),
      (Pref.ResizeHandle.ALWAYS, trans.always.txt())
    )

  def translatedBlindfoldChoices(implicit lang: Lang) =
    List(
      Pref.Blindfold.NO  -> trans.no.txt(),
      Pref.Blindfold.YES -> trans.yes.txt()
    )
}
