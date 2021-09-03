package lila.round

import strategygames.{ Black, Color, Speed, White }
import strategygames.variant.Variant
import strategygames.chess.variant._
import org.goochjs.glicko2._

import lila.game.{ Game, GameRepo, PerfPicker, RatingDiffs }
import lila.history.HistoryApi
import lila.rating.{ Glicko, Perf, RatingFactors, RatingRegulator, PerfType => PT }
import lila.user.{ Perfs, RankingApi, User, UserRepo }

final class PerfsUpdater(
    gameRepo: GameRepo,
    userRepo: UserRepo,
    historyApi: HistoryApi,
    rankingApi: RankingApi,
    botFarming: BotFarming,
    ratingFactors: () => RatingFactors
)(implicit ec: scala.concurrent.ExecutionContext) {

  // returns rating diffs
  def save(game: Game, white: User, black: User): Fu[Option[RatingDiffs]] =
    botFarming(game) flatMap {
      case true => fuccess(none)
      case _ =>
        PerfPicker.main(game) ?? { mainPerf =>
          (game.rated && game.finished && game.accountable && !white.lame && !black.lame) ?? {
            val ratingsW = mkRatings(white.perfs)
            val ratingsB = mkRatings(black.perfs)
            game.ratingVariant match {
              case Variant.Chess(Chess960) =>
                updateRatings(ratingsW.chess960, ratingsB.chess960, game)
              case Variant.Chess(KingOfTheHill) =>
                updateRatings(ratingsW.kingOfTheHill, ratingsB.kingOfTheHill, game)
              case Variant.Chess(ThreeCheck) =>
                updateRatings(ratingsW.threeCheck, ratingsB.threeCheck, game)
              case Variant.Chess(Antichess) =>
                updateRatings(ratingsW.antichess, ratingsB.antichess, game)
              case Variant.Chess(Atomic) =>
                updateRatings(ratingsW.atomic, ratingsB.atomic, game)
              case Variant.Chess(Horde) =>
                updateRatings(ratingsW.horde, ratingsB.horde, game)
              case Variant.Chess(RacingKings) =>
                updateRatings(ratingsW.racingKings, ratingsB.racingKings, game)
              case Variant.Chess(Crazyhouse) =>
                updateRatings(ratingsW.crazyhouse, ratingsB.crazyhouse, game)
              case Variant.Chess(LinesOfAction) =>
                updateRatings(ratingsW.linesOfAction, ratingsB.linesOfAction, game)
              case Variant.Draughts(strategygames.draughts.variant.Standard) =>
                updateRatings(ratingsW.international, ratingsB.international, game)
              case Variant.Draughts(strategygames.draughts.variant.Frisian) =>
                updateRatings(ratingsW.frisian, ratingsB.frisian, game)
              case Variant.Draughts(strategygames.draughts.variant.Frysk) =>
                updateRatings(ratingsW.frysk, ratingsB.frysk, game)
              case Variant.Draughts(strategygames.draughts.variant.Antidraughts) =>
                updateRatings(ratingsW.antidraughts, ratingsB.antidraughts, game)
              case Variant.Draughts(strategygames.draughts.variant.Breakthrough) =>
                updateRatings(ratingsW.breakthrough, ratingsB.breakthrough, game)
              case Variant.Draughts(strategygames.draughts.variant.Russian) =>
                updateRatings(ratingsW.russian, ratingsB.russian, game)
              case Variant.Draughts(strategygames.draughts.variant.Brazilian) =>
                updateRatings(ratingsW.brazilian, ratingsB.brazilian, game)
              case Variant.Draughts(strategygames.draughts.variant.Pool) =>
                updateRatings(ratingsW.pool, ratingsB.pool, game)
              case Variant.Chess(Standard) =>
                game.speed match {
                  case Speed.Bullet =>
                    updateRatings(ratingsW.bullet, ratingsB.bullet, game)
                  case Speed.Blitz =>
                    updateRatings(ratingsW.blitz, ratingsB.blitz, game)
                  case Speed.Rapid =>
                    updateRatings(ratingsW.rapid, ratingsB.rapid, game)
                  case Speed.Classical =>
                    updateRatings(ratingsW.classical, ratingsB.classical, game)
                  case Speed.Correspondence =>
                    updateRatings(ratingsW.correspondence, ratingsB.correspondence, game)
                  case Speed.UltraBullet =>
                    updateRatings(ratingsW.ultraBullet, ratingsB.ultraBullet, game)
                }
              case _ =>
            }
            val perfsW                      = mkPerfs(ratingsW, white -> black, game)
            val perfsB                      = mkPerfs(ratingsB, black -> white, game)
            def intRatingLens(perfs: Perfs) = mainPerf(perfs).glicko.intRating
            val ratingDiffs = Color.Map(
              intRatingLens(perfsW) - intRatingLens(white.perfs),
              intRatingLens(perfsB) - intRatingLens(black.perfs)
            )
            gameRepo.setRatingDiffs(game.id, ratingDiffs) zip
              userRepo.setPerfs(white, perfsW, white.perfs) zip
              userRepo.setPerfs(black, perfsB, black.perfs) zip
              historyApi.add(white, game, perfsW) zip
              historyApi.add(black, game, perfsB) zip
              rankingApi.save(white, game.perfType, perfsW) zip
              rankingApi.save(black, game.perfType, perfsB) inject ratingDiffs.some
          }
        }
    }

  private case class Ratings(
      chess960: Rating,
      kingOfTheHill: Rating,
      threeCheck: Rating,
      antichess: Rating,
      atomic: Rating,
      horde: Rating,
      racingKings: Rating,
      crazyhouse: Rating,
      linesOfAction: Rating,
      international: Rating,
      frisian: Rating,
      frysk: Rating,
      antidraughts: Rating,
      breakthrough: Rating,
      russian: Rating,
      brazilian: Rating,
      pool: Rating,
      ultraBullet: Rating,
      bullet: Rating,
      blitz: Rating,
      rapid: Rating,
      classical: Rating,
      correspondence: Rating
  )

  private def mkRatings(perfs: Perfs) =
    Ratings(
      chess960 = perfs.chess960.toRating,
      kingOfTheHill = perfs.kingOfTheHill.toRating,
      threeCheck = perfs.threeCheck.toRating,
      antichess = perfs.antichess.toRating,
      atomic = perfs.atomic.toRating,
      horde = perfs.horde.toRating,
      racingKings = perfs.racingKings.toRating,
      crazyhouse = perfs.crazyhouse.toRating,
      linesOfAction = perfs.linesOfAction.toRating,
      international = perfs.international.toRating,
      frisian = perfs.frisian.toRating,
      frysk = perfs.frysk.toRating,
      antidraughts = perfs.antidraughts.toRating,
      breakthrough = perfs.breakthrough.toRating,
      russian = perfs.russian.toRating,
      brazilian = perfs.brazilian.toRating,
      pool = perfs.pool.toRating,
      ultraBullet = perfs.ultraBullet.toRating,
      bullet = perfs.bullet.toRating,
      blitz = perfs.blitz.toRating,
      rapid = perfs.rapid.toRating,
      classical = perfs.classical.toRating,
      correspondence = perfs.correspondence.toRating
    )

  private def updateRatings(white: Rating, black: Rating, game: Game): Unit = {
    val result = game.winnerColor match {
      case Some(White) => Glicko.Result.Win
      case Some(Black) => Glicko.Result.Loss
      case None              => Glicko.Result.Draw
    }
    val results = new RatingPeriodResults()
    result match {
      case Glicko.Result.Draw => results.addDraw(white, black)
      case Glicko.Result.Win  => results.addResult(white, black)
      case Glicko.Result.Loss => results.addResult(black, white)
    }
    try {
      Glicko.system.updateRatings(results, true)
    } catch {
      case e: Exception => logger.error(s"update ratings #${game.id}", e)
    }
  }

  private def mkPerfs(ratings: Ratings, users: (User, User), game: Game): Perfs =
    users match {
      case (player, opponent) =>
        val perfs            = player.perfs
        val speed            = game.speed
        val isStd            = game.ratingVariant.standard
        val isHumanVsMachine = player.noBot && opponent.isBot
        def addRatingIf(cond: Boolean, perf: Perf, rating: Rating) =
          if (cond) {
            val p = perf.addOrReset(_.round.error.glicko, s"game ${game.id}")(rating, game.movedAt)
            if (isHumanVsMachine)
              p.copy(glicko = p.glicko average perf.glicko) // halve rating diffs for human
            else p
          } else perf
        val perfs1 = perfs.copy(
          chess960 = addRatingIf(game.ratingVariant.chess960, perfs.chess960, ratings.chess960),
          kingOfTheHill =
            addRatingIf(game.ratingVariant.kingOfTheHill, perfs.kingOfTheHill, ratings.kingOfTheHill),
          threeCheck = addRatingIf(game.ratingVariant.threeCheck, perfs.threeCheck, ratings.threeCheck),
          antichess = addRatingIf(game.ratingVariant.antichess, perfs.antichess, ratings.antichess),
          atomic = addRatingIf(game.ratingVariant.atomic, perfs.atomic, ratings.atomic),
          horde = addRatingIf(game.ratingVariant.horde, perfs.horde, ratings.horde),
          racingKings = addRatingIf(game.ratingVariant.racingKings, perfs.racingKings, ratings.racingKings),
          crazyhouse = addRatingIf(game.ratingVariant.crazyhouse, perfs.crazyhouse, ratings.crazyhouse),
          linesOfAction = addRatingIf(game.ratingVariant.linesOfAction, perfs.linesOfAction, ratings.linesOfAction),
          international = addRatingIf(game.ratingVariant.draughtsStandard, perfs.international, ratings.international),
          frisian = addRatingIf(game.ratingVariant.frisian, perfs.frisian, ratings.frisian),
          frysk = addRatingIf(game.ratingVariant.frysk, perfs.frysk, ratings.frysk),
          antidraughts = addRatingIf(game.ratingVariant.antidraughts, perfs.antidraughts, ratings.antidraughts),
          breakthrough = addRatingIf(game.ratingVariant.breakthrough, perfs.breakthrough, ratings.breakthrough),
          russian = addRatingIf(game.ratingVariant.russian, perfs.russian, ratings.russian),
          brazilian = addRatingIf(game.ratingVariant.brazilian, perfs.brazilian, ratings.brazilian),
          pool = addRatingIf(game.ratingVariant.pool, perfs.pool, ratings.pool),
          ultraBullet =
            addRatingIf(isStd && speed == Speed.UltraBullet, perfs.ultraBullet, ratings.ultraBullet),
          bullet = addRatingIf(isStd && speed == Speed.Bullet, perfs.bullet, ratings.bullet),
          blitz = addRatingIf(isStd && speed == Speed.Blitz, perfs.blitz, ratings.blitz),
          rapid = addRatingIf(isStd && speed == Speed.Rapid, perfs.rapid, ratings.rapid),
          classical = addRatingIf(isStd && speed == Speed.Classical, perfs.classical, ratings.classical),
          correspondence =
            addRatingIf(isStd && speed == Speed.Correspondence, perfs.correspondence, ratings.correspondence)
        )
        val r = RatingRegulator(ratingFactors()) _
        val perfs2 = perfs1.copy(
          chess960 = r(PT.Chess960, perfs.chess960, perfs1.chess960),
          kingOfTheHill = r(PT.KingOfTheHill, perfs.kingOfTheHill, perfs1.kingOfTheHill),
          threeCheck = r(PT.ThreeCheck, perfs.threeCheck, perfs1.threeCheck),
          antichess = r(PT.Antichess, perfs.antichess, perfs1.antichess),
          atomic = r(PT.Atomic, perfs.atomic, perfs1.atomic),
          horde = r(PT.Horde, perfs.horde, perfs1.horde),
          racingKings = r(PT.RacingKings, perfs.racingKings, perfs1.racingKings),
          crazyhouse = r(PT.Crazyhouse, perfs.crazyhouse, perfs1.crazyhouse),
          linesOfAction = r(PT.LinesOfAction, perfs.linesOfAction, perfs1.linesOfAction),
          international = r(PT.International, perfs.international, perfs1.international),
          frisian = r(PT.Frisian, perfs.frisian, perfs1.frisian),
          frysk = r(PT.Frysk, perfs.frysk, perfs1.frysk),
          antidraughts = r(PT.Antidraughts, perfs.antidraughts, perfs1.antidraughts),
          breakthrough = r(PT.Breakthrough, perfs.breakthrough, perfs1.breakthrough),
          russian = r(PT.Russian, perfs.russian, perfs1.russian),
          brazilian = r(PT.Brazilian, perfs.brazilian, perfs1.brazilian),
          pool = r(PT.Pool, perfs.pool, perfs1.pool),
          bullet = r(PT.Bullet, perfs.bullet, perfs1.bullet),
          blitz = r(PT.Blitz, perfs.blitz, perfs1.blitz),
          rapid = r(PT.Rapid, perfs.rapid, perfs1.rapid),
          classical = r(PT.Classical, perfs.classical, perfs1.classical),
          correspondence = r(PT.Correspondence, perfs.correspondence, perfs1.correspondence)
        )
        if (isStd) perfs2.updateStandard else perfs2
    }
}
