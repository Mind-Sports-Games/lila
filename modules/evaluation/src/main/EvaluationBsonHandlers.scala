package lila.evaluation

import reactivemongo.api.bson._

import strategygames.{ Player => PlayerIndex }

import lila.db.BSON
import lila.db.dsl._

object EvaluationBsonHandlers {

  implicit val playerFlagsHandler = new BSON[PlayerFlags] {

    def reads(r: BSON.Reader): PlayerFlags =
      PlayerFlags(
        suspiciousErrorRate = r boolD "ser",
        alwaysHasAdvantage = r boolD "aha",
        highBlurRate = r boolD "hbr",
        moderateBlurRate = r boolD "mbr",
        highlyConsistentPlyTimes = r boolD "hcmt",
        moderatelyConsistentPlyTimes = r boolD "cmt",
        noFastPlies = r boolD "nfm",
        suspiciousHoldAlert = r boolD "sha"
      )

    def writes(w: BSON.Writer, o: PlayerFlags) =
      $doc(
        "ser"  -> w.boolO(o.suspiciousErrorRate),
        "aha"  -> w.boolO(o.alwaysHasAdvantage),
        "hbr"  -> w.boolO(o.highBlurRate),
        "mbr"  -> w.boolO(o.moderateBlurRate),
        "hcmt" -> w.boolO(o.highlyConsistentPlyTimes),
        "cmt"  -> w.boolO(o.moderatelyConsistentPlyTimes),
        "nfm"  -> w.boolO(o.noFastPlies),
        "sha"  -> w.boolO(o.suspiciousHoldAlert)
      )
  }

  implicit val GameAssessmentBSONHandler =
    BSONIntegerHandler.as[GameAssessment](GameAssessment.orDefault, _.id)

  implicit val playerAssessmentHandler = new BSON[PlayerAssessment] {

    def reads(r: BSON.Reader): PlayerAssessment = PlayerAssessment(
      _id = r str "_id",
      gameId = r str "gameId",
      userId = r str "userId",
      playerIndex = PlayerIndex.fromP1(r bool "p1"),
      assessment = r.get[GameAssessment]("assessment"),
      date = r date "date",
      basics = PlayerAssessment.Basics(
        plyTimes = Statistics.IntAvgSd(
          avg = r int "mtAvg",
          sd = r int "mtSd"
        ),
        hold = r bool "hold",
        blurs = r int "blurs",
        blurStreak = r intO "blurStreak",
        mtStreak = r boolD "mtStreak"
      ),
      analysis = Statistics.IntAvgSd(
        avg = r int "sfAvg",
        sd = r int "sfSd"
      ),
      flags = r.get[PlayerFlags]("flags"),
      tcFactor = r doubleO "tcFactor"
    )

    def writes(w: BSON.Writer, o: PlayerAssessment) =
      $doc(
        "_id"        -> o._id,
        "gameId"     -> o.gameId,
        "userId"     -> o.userId,
        "p1"         -> o.playerIndex.p1,
        "assessment" -> o.assessment,
        "date"       -> o.date,
        "flags"      -> o.flags,
        "sfAvg"      -> o.analysis.avg,
        "sfSd"       -> o.analysis.sd,
        "mtAvg"      -> o.basics.plyTimes.avg,
        "mtSd"       -> o.basics.plyTimes.sd,
        "blurs"      -> o.basics.blurs,
        "hold"       -> o.basics.hold,
        "blurStreak" -> o.basics.blurStreak,
        "mtStreak"   -> w.boolO(o.basics.mtStreak),
        "tcFactor"   -> o.tcFactor
      )
  }
}
