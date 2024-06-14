package lila.round

import strategygames.{ P2, Player => PlayerIndex, Speed, P1 }
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
  def save(game: Game, p1: User, p2: User): Fu[Option[RatingDiffs]] =
    botFarming(game) flatMap {
      case true => fuccess(none)
      case _ =>
        PerfPicker.main(game) ?? { mainPerf =>
          (game.rated && game.finished && game.accountable && !p1.lame && !p2.lame) ?? {
            val ratingsW = mkRatings(p1.perfs)
            val ratingsB = mkRatings(p2.perfs)
            game.ratingVariant match {
              case Variant.Chess(Chess960) =>
                updateRatings(ratingsW.chess960, ratingsB.chess960, game)
              case Variant.Chess(KingOfTheHill) =>
                updateRatings(ratingsW.kingOfTheHill, ratingsB.kingOfTheHill, game)
              case Variant.Chess(ThreeCheck) =>
                updateRatings(ratingsW.threeCheck, ratingsB.threeCheck, game)
              case Variant.Chess(FiveCheck) =>
                updateRatings(ratingsW.fiveCheck, ratingsB.fiveCheck, game)
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
              case Variant.Chess(NoCastling) =>
                updateRatings(ratingsW.noCastling, ratingsB.noCastling, game)
              case Variant.Chess(Monster) =>
                updateRatings(ratingsW.monster, ratingsB.monster, game)
              case Variant.Chess(LinesOfAction) =>
                updateRatings(ratingsW.linesOfAction, ratingsB.linesOfAction, game)
              case Variant.Chess(ScrambledEggs) =>
                updateRatings(ratingsW.scrambledEggs, ratingsB.scrambledEggs, game)
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
              case Variant.Draughts(strategygames.draughts.variant.Portuguese) =>
                updateRatings(ratingsW.portuguese, ratingsB.portuguese, game)
              case Variant.Draughts(strategygames.draughts.variant.English) =>
                updateRatings(ratingsW.english, ratingsB.english, game)
              case Variant.FairySF(strategygames.fairysf.variant.Shogi) =>
                updateRatings(ratingsW.shogi, ratingsB.shogi, game)
              case Variant.FairySF(strategygames.fairysf.variant.Xiangqi) =>
                updateRatings(ratingsW.xiangqi, ratingsB.xiangqi, game)
              case Variant.FairySF(strategygames.fairysf.variant.MiniShogi) =>
                updateRatings(ratingsW.minishogi, ratingsB.minishogi, game)
              case Variant.FairySF(strategygames.fairysf.variant.MiniXiangqi) =>
                updateRatings(ratingsW.minixiangqi, ratingsB.minixiangqi, game)
              case Variant.FairySF(strategygames.fairysf.variant.Flipello) =>
                updateRatings(ratingsW.flipello, ratingsB.flipello, game)
              case Variant.FairySF(strategygames.fairysf.variant.Flipello10) =>
                updateRatings(ratingsW.flipello10, ratingsB.flipello10, game)
              case Variant.FairySF(strategygames.fairysf.variant.Amazons) =>
                updateRatings(ratingsW.amazons, ratingsB.amazons, game)
              case Variant.FairySF(strategygames.fairysf.variant.BreakthroughTroyka) =>
                updateRatings(ratingsW.breakthroughtroyka, ratingsB.breakthroughtroyka, game)
              case Variant.FairySF(strategygames.fairysf.variant.MiniBreakthroughTroyka) =>
                updateRatings(ratingsW.minibreakthroughtroyka, ratingsB.minibreakthroughtroyka, game)
              case Variant.Samurai(strategygames.samurai.variant.Oware) =>
                updateRatings(ratingsW.oware, ratingsB.oware, game)
              case Variant.Togyzkumalak(strategygames.togyzkumalak.variant.Togyzkumalak) =>
                updateRatings(ratingsW.togyzkumalak, ratingsB.togyzkumalak, game)
              case Variant.Go(strategygames.go.variant.Go9x9) =>
                updateRatings(ratingsW.go9x9, ratingsB.go9x9, game)
              case Variant.Go(strategygames.go.variant.Go13x13) =>
                updateRatings(ratingsW.go13x13, ratingsB.go13x13, game)
              case Variant.Go(strategygames.go.variant.Go19x19) =>
                updateRatings(ratingsW.go19x19, ratingsB.go19x19, game)
              case Variant.Backgammon(strategygames.backgammon.variant.Backgammon) =>
                updateRatings(ratingsW.backgammon, ratingsB.backgammon, game)
              case Variant.Backgammon(strategygames.backgammon.variant.Nackgammon) =>
                updateRatings(ratingsW.nackgammon, ratingsB.nackgammon, game)
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
            val perfsW                      = mkPerfs(ratingsW, p1 -> p2, game)
            val perfsB                      = mkPerfs(ratingsB, p2 -> p1, game)
            def intRatingLens(perfs: Perfs) = mainPerf(perfs).glicko.intRating
            val ratingDiffs = PlayerIndex.Map(
              intRatingLens(perfsW) - intRatingLens(p1.perfs),
              intRatingLens(perfsB) - intRatingLens(p2.perfs)
            )
            gameRepo.setRatingDiffs(game.id, ratingDiffs) zip
              userRepo.setPerfs(p1, perfsW, p1.perfs) zip
              userRepo.setPerfs(p2, perfsB, p2.perfs) zip
              historyApi.add(p1, game, perfsW) zip
              historyApi.add(p2, game, perfsB) zip
              rankingApi.save(p1, game.perfType, perfsW) zip
              rankingApi.save(p2, game.perfType, perfsB) inject ratingDiffs.some
          }
        }
    }

  private case class Ratings(
      chess960: Rating,
      kingOfTheHill: Rating,
      threeCheck: Rating,
      fiveCheck: Rating,
      antichess: Rating,
      atomic: Rating,
      horde: Rating,
      racingKings: Rating,
      crazyhouse: Rating,
      noCastling: Rating,
      monster: Rating,
      linesOfAction: Rating,
      scrambledEggs: Rating,
      international: Rating,
      frisian: Rating,
      frysk: Rating,
      antidraughts: Rating,
      breakthrough: Rating,
      russian: Rating,
      brazilian: Rating,
      pool: Rating,
      portuguese: Rating,
      english: Rating,
      shogi: Rating,
      xiangqi: Rating,
      minishogi: Rating,
      minixiangqi: Rating,
      flipello: Rating,
      flipello10: Rating,
      amazons: Rating,
      oware: Rating,
      togyzkumalak: Rating,
      go9x9: Rating,
      go13x13: Rating,
      go19x19: Rating,
      backgammon: Rating,
      nackgammon: Rating,
      breakthroughtroyka: Rating,
      minibreakthroughtroyka: Rating,
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
      fiveCheck = perfs.fiveCheck.toRating,
      antichess = perfs.antichess.toRating,
      atomic = perfs.atomic.toRating,
      horde = perfs.horde.toRating,
      racingKings = perfs.racingKings.toRating,
      crazyhouse = perfs.crazyhouse.toRating,
      noCastling = perfs.noCastling.toRating,
      monster = perfs.monster.toRating,
      linesOfAction = perfs.linesOfAction.toRating,
      scrambledEggs = perfs.scrambledEggs.toRating,
      international = perfs.international.toRating,
      frisian = perfs.frisian.toRating,
      frysk = perfs.frysk.toRating,
      antidraughts = perfs.antidraughts.toRating,
      breakthrough = perfs.breakthrough.toRating,
      russian = perfs.russian.toRating,
      brazilian = perfs.brazilian.toRating,
      pool = perfs.pool.toRating,
      portuguese = perfs.portuguese.toRating,
      english = perfs.english.toRating,
      shogi = perfs.shogi.toRating,
      xiangqi = perfs.xiangqi.toRating,
      minishogi = perfs.minishogi.toRating,
      minixiangqi = perfs.minixiangqi.toRating,
      flipello = perfs.flipello.toRating,
      flipello10 = perfs.flipello10.toRating,
      amazons = perfs.amazons.toRating,
      oware = perfs.oware.toRating,
      togyzkumalak = perfs.togyzkumalak.toRating,
      go9x9 = perfs.go9x9.toRating,
      go13x13 = perfs.go13x13.toRating,
      go19x19 = perfs.go19x19.toRating,
      backgammon = perfs.backgammon.toRating,
      nackgammon = perfs.nackgammon.toRating,
      breakthroughtroyka = perfs.breakthroughtroyka.toRating,
      minibreakthroughtroyka = perfs.minibreakthroughtroyka.toRating,
      ultraBullet = perfs.ultraBullet.toRating,
      bullet = perfs.bullet.toRating,
      blitz = perfs.blitz.toRating,
      rapid = perfs.rapid.toRating,
      classical = perfs.classical.toRating,
      correspondence = perfs.correspondence.toRating
    )

  private def updateRatings(p1: Rating, p2: Rating, game: Game): Unit = {
    val result = game.winnerPlayerIndex match {
      case Some(P1) => Glicko.Result.Win
      case Some(P2) => Glicko.Result.Loss
      case None     => Glicko.Result.Draw
    }
    val results = new RatingPeriodResults()
    result match {
      case Glicko.Result.Draw => results.addDraw(p1, p2)
      case Glicko.Result.Win  => results.addResult(p1, p2)
      case Glicko.Result.Loss => results.addResult(p2, p1)
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
        val isStd            = game.ratingVariant.key == "standard"
        val isHumanVsMachine = player.noBot && opponent.isBot
        def addRatingIf(cond: Boolean, perf: Perf, rating: Rating) =
          if (cond) {
            val p = perf.addOrReset(_.round.error.glicko, s"game ${game.id}")(rating, game.updatedAt)
            if (isHumanVsMachine)
              p.copy(glicko = p.glicko average perf.glicko) // halve rating diffs for human
            else p
          } else perf
        def addRatingVariant(variant: Variant, perf: Perf, rating: Rating) =
          addRatingIf(game.ratingVariant == variant, perf, rating)
        val perfs1 = perfs.copy(
          chess960 = addRatingVariant(
            Variant.Chess(Chess960),
            perfs.chess960,
            ratings.chess960
          ),
          kingOfTheHill = addRatingVariant(
            Variant.Chess(KingOfTheHill),
            perfs.kingOfTheHill,
            ratings.kingOfTheHill
          ),
          threeCheck = addRatingVariant(
            Variant.Chess(ThreeCheck),
            perfs.threeCheck,
            ratings.threeCheck
          ),
          fiveCheck = addRatingVariant(
            Variant.Chess(FiveCheck),
            perfs.fiveCheck,
            ratings.fiveCheck
          ),
          antichess = addRatingVariant(
            Variant.Chess(Antichess),
            perfs.antichess,
            ratings.antichess
          ),
          atomic = addRatingVariant(
            Variant.Chess(Atomic),
            perfs.atomic,
            ratings.atomic
          ),
          horde = addRatingVariant(
            Variant.Chess(Horde),
            perfs.horde,
            ratings.horde
          ),
          racingKings = addRatingVariant(
            Variant.Chess(RacingKings),
            perfs.racingKings,
            ratings.racingKings
          ),
          crazyhouse = addRatingVariant(
            Variant.Chess(Crazyhouse),
            perfs.crazyhouse,
            ratings.crazyhouse
          ),
          noCastling = addRatingVariant(
            Variant.Chess(NoCastling),
            perfs.noCastling,
            ratings.noCastling
          ),
          monster = addRatingVariant(
            Variant.Chess(Monster),
            perfs.monster,
            ratings.monster
          ),
          linesOfAction = addRatingVariant(
            Variant.Chess(LinesOfAction),
            perfs.linesOfAction,
            ratings.linesOfAction
          ),
          scrambledEggs = addRatingVariant(
            Variant.Chess(ScrambledEggs),
            perfs.scrambledEggs,
            ratings.scrambledEggs
          ),
          international = addRatingVariant(
            Variant.Draughts(strategygames.draughts.variant.Standard),
            perfs.international,
            ratings.international
          ),
          frisian = addRatingVariant(
            Variant.Draughts(strategygames.draughts.variant.Frisian),
            perfs.frisian,
            ratings.frisian
          ),
          frysk = addRatingVariant(
            Variant.Draughts(strategygames.draughts.variant.Frysk),
            perfs.frysk,
            ratings.frysk
          ),
          antidraughts = addRatingVariant(
            Variant.Draughts(strategygames.draughts.variant.Antidraughts),
            perfs.antidraughts,
            ratings.antidraughts
          ),
          breakthrough = addRatingVariant(
            Variant.Draughts(strategygames.draughts.variant.Breakthrough),
            perfs.breakthrough,
            ratings.breakthrough
          ),
          russian = addRatingVariant(
            Variant.Draughts(strategygames.draughts.variant.Russian),
            perfs.russian,
            ratings.russian
          ),
          brazilian = addRatingVariant(
            Variant.Draughts(strategygames.draughts.variant.Brazilian),
            perfs.brazilian,
            ratings.brazilian
          ),
          pool = addRatingVariant(
            Variant.Draughts(strategygames.draughts.variant.Pool),
            perfs.pool,
            ratings.pool
          ),
          portuguese = addRatingVariant(
            Variant.Draughts(strategygames.draughts.variant.Portuguese),
            perfs.portuguese,
            ratings.portuguese
          ),
          english = addRatingVariant(
            Variant.Draughts(strategygames.draughts.variant.English),
            perfs.english,
            ratings.english
          ),
          shogi = addRatingVariant(
            Variant.FairySF(strategygames.fairysf.variant.Shogi),
            perfs.shogi,
            ratings.shogi
          ),
          xiangqi = addRatingVariant(
            Variant.FairySF(strategygames.fairysf.variant.Xiangqi),
            perfs.xiangqi,
            ratings.xiangqi
          ),
          minishogi = addRatingVariant(
            Variant.FairySF(strategygames.fairysf.variant.MiniShogi),
            perfs.minishogi,
            ratings.minishogi
          ),
          minixiangqi = addRatingVariant(
            Variant.FairySF(strategygames.fairysf.variant.MiniXiangqi),
            perfs.minixiangqi,
            ratings.minixiangqi
          ),
          flipello = addRatingVariant(
            Variant.FairySF(strategygames.fairysf.variant.Flipello),
            perfs.flipello,
            ratings.flipello
          ),
          flipello10 = addRatingVariant(
            Variant.FairySF(strategygames.fairysf.variant.Flipello10),
            perfs.flipello10,
            ratings.flipello10
          ),
          amazons = addRatingVariant(
            Variant.FairySF(strategygames.fairysf.variant.Amazons),
            perfs.amazons,
            ratings.amazons
          ),
          breakthroughtroyka = addRatingVariant(
            Variant.FairySF(strategygames.fairysf.variant.BreakthroughTroyka),
            perfs.breakthroughtroyka,
            ratings.breakthroughtroyka
          ),
          minibreakthroughtroyka = addRatingVariant(
            Variant.FairySF(strategygames.fairysf.variant.MiniBreakthroughTroyka),
            perfs.minibreakthroughtroyka,
            ratings.minibreakthroughtroyka
          ),
          oware = addRatingVariant(
            Variant.Samurai(strategygames.samurai.variant.Oware),
            perfs.oware,
            ratings.oware
          ),
          togyzkumalak = addRatingVariant(
            Variant.Togyzkumalak(strategygames.togyzkumalak.variant.Togyzkumalak),
            perfs.togyzkumalak,
            ratings.togyzkumalak
          ),
          go9x9 = addRatingVariant(
            Variant.Go(strategygames.go.variant.Go9x9),
            perfs.go9x9,
            ratings.go9x9
          ),
          go13x13 = addRatingVariant(
            Variant.Go(strategygames.go.variant.Go13x13),
            perfs.go13x13,
            ratings.go13x13
          ),
          go19x19 = addRatingVariant(
            Variant.Go(strategygames.go.variant.Go19x19),
            perfs.go19x19,
            ratings.go19x19
          ),
          backgammon = addRatingVariant(
            Variant.Backgammon(strategygames.backgammon.variant.Backgammon),
            perfs.backgammon,
            ratings.backgammon
          ),
          nackgammon = addRatingVariant(
            Variant.Backgammon(strategygames.backgammon.variant.Nackgammon),
            perfs.nackgammon,
            ratings.nackgammon
          ),
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
          chess960 = r(PT.orDefault("chess960"), perfs.chess960, perfs1.chess960),
          kingOfTheHill = r(PT.orDefault("kingOfTheHill"), perfs.kingOfTheHill, perfs1.kingOfTheHill),
          threeCheck = r(PT.orDefault("threeCheck"), perfs.threeCheck, perfs1.threeCheck),
          fiveCheck = r(PT.orDefault("fiveCheck"), perfs.fiveCheck, perfs1.fiveCheck),
          antichess = r(PT.orDefault("antichess"), perfs.antichess, perfs1.antichess),
          atomic = r(PT.orDefault("atomic"), perfs.atomic, perfs1.atomic),
          horde = r(PT.orDefault("horde"), perfs.horde, perfs1.horde),
          racingKings = r(PT.orDefault("racingKings"), perfs.racingKings, perfs1.racingKings),
          crazyhouse = r(PT.orDefault("crazyhouse"), perfs.crazyhouse, perfs1.crazyhouse),
          noCastling = r(PT.orDefault("noCastling"), perfs.noCastling, perfs1.noCastling),
          linesOfAction = r(PT.orDefault("linesOfAction"), perfs.linesOfAction, perfs1.linesOfAction),
          scrambledEggs = r(PT.orDefault("scrambledEggs"), perfs.scrambledEggs, perfs1.scrambledEggs),
          international = r(PT.orDefault("international"), perfs.international, perfs1.international),
          frisian = r(PT.orDefault("frisian"), perfs.frisian, perfs1.frisian),
          frysk = r(PT.orDefault("frysk"), perfs.frysk, perfs1.frysk),
          antidraughts = r(PT.orDefault("antidraughts"), perfs.antidraughts, perfs1.antidraughts),
          breakthrough = r(PT.orDefault("breakthrough"), perfs.breakthrough, perfs1.breakthrough),
          russian = r(PT.orDefault("russian"), perfs.russian, perfs1.russian),
          brazilian = r(PT.orDefault("brazilian"), perfs.brazilian, perfs1.brazilian),
          pool = r(PT.orDefault("pool"), perfs.pool, perfs1.pool),
          portuguese = r(PT.orDefault("portuguese"), perfs.portuguese, perfs1.portuguese),
          english = r(PT.orDefault("english"), perfs.english, perfs1.english),
          shogi = r(PT.orDefault("shogi"), perfs.shogi, perfs1.shogi),
          xiangqi = r(PT.orDefault("xiangqi"), perfs.xiangqi, perfs1.xiangqi),
          minishogi = r(PT.orDefault("minishogi"), perfs.minishogi, perfs1.minishogi),
          minixiangqi = r(PT.orDefault("minixiangqi"), perfs.minixiangqi, perfs1.minixiangqi),
          flipello = r(PT.orDefault("flipello"), perfs.flipello, perfs1.flipello),
          flipello10 = r(PT.orDefault("flipello10"), perfs.flipello10, perfs1.flipello10),
          amazons = r(PT.orDefault("amazons"), perfs.amazons, perfs1.amazons),
          oware = r(PT.orDefault("oware"), perfs.oware, perfs1.oware),
          togyzkumalak = r(PT.orDefault("togyzkumalak"), perfs.togyzkumalak, perfs1.togyzkumalak),
          go9x9 = r(PT.orDefault("go9x9"), perfs.go9x9, perfs1.go9x9),
          go13x13 = r(PT.orDefault("go13x13"), perfs.go13x13, perfs1.go13x13),
          go19x19 = r(PT.orDefault("go19x19"), perfs.go19x19, perfs1.go19x19),
          backgammon = r(PT.orDefault("backgammon"), perfs.backgammon, perfs1.backgammon),
          nackgammon = r(PT.orDefault("nackgammon"), perfs.nackgammon, perfs1.nackgammon),
          breakthroughtroyka =
            r(PT.orDefault("breakthroughtroyka"), perfs.breakthroughtroyka, perfs1.breakthroughtroyka),
          minibreakthroughtroyka = r(
            PT.orDefault("minibreakthroughtroyka"),
            perfs.minibreakthroughtroyka,
            perfs1.minibreakthroughtroyka
          ),
          bullet = r(PT.orDefault("bullet"), perfs.bullet, perfs1.bullet),
          blitz = r(PT.orDefault("blitz"), perfs.blitz, perfs1.blitz),
          rapid = r(PT.orDefault("rapid"), perfs.rapid, perfs1.rapid),
          classical = r(PT.orDefault("classical"), perfs.classical, perfs1.classical),
          correspondence = r(PT.orDefault("correspondence"), perfs.correspondence, perfs1.correspondence)
        )
        if (isStd) perfs2.updateStandard else perfs2
    }
}
