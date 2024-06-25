package lila.user

import org.joda.time.DateTime
import reactivemongo.api.bson._
import reactivemongo.api.ReadPreference
import scala.concurrent.duration._

import lila.db.dsl._
import lila.memo.CacheApi._
import lila.rating.{ Glicko, Perf, PerfType }

final class RankingApi(
    userRepo: UserRepo,
    coll: Coll,
    cacheApi: lila.memo.CacheApi,
    mongoCache: lila.memo.MongoCache.Api,
    lightUser: lila.common.LightUser.Getter
)(implicit ec: scala.concurrent.ExecutionContext) {

  import RankingApi._
  implicit private val rankingBSONHandler = Macros.handler[Ranking]

  def save(user: User, perfType: Option[PerfType], perfs: Perfs): Funit =
    perfType ?? { pt =>
      save(user, pt, perfs(pt))
    }

  def save(user: User, perfType: PerfType, perf: Perf): Funit =
    (user.rankable && perf.nb >= 2) ?? coll.update
      .one(
        $id(makeId(user.id, perfType)),
        $doc(
          "perf"      -> perfType.id,
          "rating"    -> perf.intRating,
          "prog"      -> perf.progress,
          "stable"    -> perf.rankable(PerfType variantOf perfType),
          "expiresAt" -> DateTime.now.plusDays(31) // change back to 7 when more regular users
        ),
        upsert = true
      )
      .void

  def remove(userId: User.ID): Funit =
    userRepo byId userId flatMap {
      _ ?? { user =>
        coll.delete
          .one(
            $inIds(
              PerfType.leaderboardable
                .filter { pt =>
                  user.perfs(pt).nonEmpty
                }
                .map { makeId(user.id, _) }
            )
          )
          .void
      }
    }

  private def makeId(userId: User.ID, perfType: PerfType) =
    s"$userId:${perfType.id}"

  private[user] def topPerf(perfId: Perf.ID, nb: Int): Fu[List[User.LightPerf]] =
    PerfType.id2key(perfId) ?? { perfKey =>
      coll
        //.find($doc("perf" -> perfId, "stable" -> true)) // change back to stable when more regular users
        .find($doc("perf" -> perfId))
        .sort($doc("rating" -> -1))
        .cursor[Ranking](ReadPreference.secondaryPreferred)
        .list(nb)
        .flatMap {
          _.map { r =>
            lightUser(r.user).map {
              _ map { light =>
                User.LightPerf(
                  user = light,
                  perfKey = perfKey,
                  rating = r.rating,
                  progress = ~r.prog
                )
              }
            }
          }.sequenceFu
            .dmap(_.flatten)
        }
    }

  private[user] def fetchLeaderboard(nb: Int): Fu[Perfs.Leaderboards] =
    for {
      ultraBullet            <- topPerf(PerfType.orDefault("ultraBullet").id, nb)
      bullet                 <- topPerf(PerfType.orDefault("bullet").id, nb)
      blitz                  <- topPerf(PerfType.orDefault("blitz").id, nb)
      rapid                  <- topPerf(PerfType.orDefault("rapid").id, nb)
      classical              <- topPerf(PerfType.orDefault("classical").id, nb)
      chess960               <- topPerf(PerfType.orDefault("chess960").id, nb)
      kingOfTheHill          <- topPerf(PerfType.orDefault("kingOfTheHill").id, nb)
      threeCheck             <- topPerf(PerfType.orDefault("threeCheck").id, nb)
      fiveCheck              <- topPerf(PerfType.orDefault("fiveCheck").id, nb)
      antichess              <- topPerf(PerfType.orDefault("antichess").id, nb)
      atomic                 <- topPerf(PerfType.orDefault("atomic").id, nb)
      horde                  <- topPerf(PerfType.orDefault("horde").id, nb)
      racingKings            <- topPerf(PerfType.orDefault("racingKings").id, nb)
      crazyhouse             <- topPerf(PerfType.orDefault("crazyhouse").id, nb)
      noCastling             <- topPerf(PerfType.orDefault("noCastling").id, nb)
      monster                <- topPerf(PerfType.orDefault("monster").id, nb)
      linesOfAction          <- topPerf(PerfType.orDefault("linesOfAction").id, nb)
      scrambledEggs          <- topPerf(PerfType.orDefault("scrambledEggs").id, nb)
      international          <- topPerf(PerfType.orDefault("international").id, nb)
      frisian                <- topPerf(PerfType.orDefault("frisian").id, nb)
      frysk                  <- topPerf(PerfType.orDefault("frysk").id, nb)
      antidraughts           <- topPerf(PerfType.orDefault("antidraughts").id, nb)
      breakthrough           <- topPerf(PerfType.orDefault("breakthrough").id, nb)
      russian                <- topPerf(PerfType.orDefault("russian").id, nb)
      brazilian              <- topPerf(PerfType.orDefault("brazilian").id, nb)
      pool                   <- topPerf(PerfType.orDefault("pool").id, nb)
      portuguese             <- topPerf(PerfType.orDefault("portuguese").id, nb)
      english                <- topPerf(PerfType.orDefault("english").id, nb)
      shogi                  <- topPerf(PerfType.orDefault("shogi").id, nb)
      xiangqi                <- topPerf(PerfType.orDefault("xiangqi").id, nb)
      minishogi              <- topPerf(PerfType.orDefault("minishogi").id, nb)
      minixiangqi            <- topPerf(PerfType.orDefault("minixiangqi").id, nb)
      flipello               <- topPerf(PerfType.orDefault("flipello").id, nb)
      flipello10             <- topPerf(PerfType.orDefault("flipello10").id, nb)
      amazons                <- topPerf(PerfType.orDefault("amazons").id, nb)
      breakthroughtroyka     <- topPerf(PerfType.orDefault("breakthroughtroyka").id, nb)
      minibreakthroughtroyka <- topPerf(PerfType.orDefault("minibreakthroughtroyka").id, nb)
      oware                  <- topPerf(PerfType.orDefault("oware").id, nb)
      togyzkumalak           <- topPerf(PerfType.orDefault("togyzkumalak").id, nb)
      go9x9                  <- topPerf(PerfType.orDefault("go9x9").id, nb)
      go13x13                <- topPerf(PerfType.orDefault("go13x13").id, nb)
      go19x19                <- topPerf(PerfType.orDefault("go19x19").id, nb)
      backgammon             <- topPerf(PerfType.orDefault("backgammon").id, nb)
      nackgammon             <- topPerf(PerfType.orDefault("nackgammon").id, nb)
    } yield Perfs.Leaderboards(
      ultraBullet = ultraBullet,
      bullet = bullet,
      blitz = blitz,
      rapid = rapid,
      classical = classical,
      crazyhouse = crazyhouse,
      chess960 = chess960,
      kingOfTheHill = kingOfTheHill,
      threeCheck = threeCheck,
      fiveCheck = fiveCheck,
      antichess = antichess,
      atomic = atomic,
      horde = horde,
      racingKings = racingKings,
      noCastling = noCastling,
      monster = monster,
      linesOfAction = linesOfAction,
      scrambledEggs = scrambledEggs,
      international = international,
      frisian = frisian,
      frysk = frysk,
      antidraughts = antidraughts,
      breakthrough = breakthrough,
      russian = russian,
      brazilian = brazilian,
      pool = pool,
      portuguese = portuguese,
      english = english,
      shogi = shogi,
      xiangqi = xiangqi,
      minishogi = minishogi,
      minixiangqi = minixiangqi,
      flipello = flipello,
      flipello10 = flipello10,
      amazons = amazons,
      breakthroughtroyka = breakthroughtroyka,
      minibreakthroughtroyka = minibreakthroughtroyka,
      oware = oware,
      togyzkumalak = togyzkumalak,
      go9x9 = go9x9,
      go13x13 = go13x13,
      go19x19 = go19x19,
      backgammon = backgammon,
      nackgammon = nackgammon
    )

  object weeklyStableRanking {

    private type Rank = Int

    def of(userId: User.ID): Fu[Map[PerfType, Rank]] =
      cache.getUnit map { all =>
        all.flatMap { case (pt, ranking) =>
          ranking get userId map (pt -> _)
        }
      }

    private val cache = cacheApi.unit[Map[PerfType, Map[User.ID, Rank]]] {
      _.refreshAfterWrite(15 minutes)
        .buildAsyncFuture { _ =>
          lila.common.Future
            .linear(PerfType.leaderboardable) { pt =>
              compute(pt) dmap (pt -> _)
            }
            .dmap(_.toMap)
        }
    }

    private def compute(pt: PerfType): Fu[Map[User.ID, Rank]] =
      coll
        .find(
          $doc("perf" -> pt.id, "stable" -> true),
          $doc("_id" -> true).some
        )
        .sort($doc("rating" -> -1))
        .cursor[Bdoc](readPreference = ReadPreference.secondaryPreferred)
        .fold(1 -> Map.newBuilder[User.ID, Rank]) { case (state @ (rank, b), doc) =>
          doc.string("_id").fold(state) { id =>
            val user = id takeWhile (':' !=)
            b += (user -> rank)
            (rank + 1) -> b
          }
        }
        .map(_._2.result())
  }

  object weeklyRatingDistribution {

    private type NbUsers = Int

    def apply(pt: PerfType) = cache.get(pt.id)

    private val cache = mongoCache[Perf.ID, List[NbUsers]](
      PerfType.leaderboardable.size,
      "user:rating:distribution",
      179 minutes,
      _.toString
    ) { loader =>
      _.refreshAfterWrite(180 minutes)
        .buildAsyncFuture {
          loader(compute)
        }
    }

    // from 600 to 2800 by Stat.group
    private def compute(perfId: Perf.ID): Fu[List[NbUsers]] =
      lila.rating.PerfType(perfId).exists(lila.rating.PerfType.leaderboardable.contains) ?? {
        coll
          .aggregateList(
            maxDocs = Int.MaxValue,
            ReadPreference.secondaryPreferred
          ) { framework =>
            import framework._
            Match($doc("perf" -> perfId)) -> List(
              Project(
                $doc(
                  "_id" -> false,
                  "r" -> $doc(
                    "$subtract" -> $arr(
                      "$rating",
                      $doc("$mod" -> $arr("$rating", Stat.group))
                    )
                  )
                )
              ),
              GroupField("r")("nb" -> SumAll)
            )
          }
          .map { res =>
            val hash: Map[Int, NbUsers] = res.view
              .flatMap { obj =>
                for {
                  rating <- obj.int("_id")
                  nb     <- obj.getAsOpt[NbUsers]("nb")
                } yield rating -> nb
              }
              .to(Map)
            (Glicko.minRating to 2800 by Stat.group).map { r =>
              hash.getOrElse(r, 0)
            }.toList
          } addEffect monitorRatingDistribution(perfId)
      }

    /* monitors cumulated ratio of players in each rating group, for a perf
     *
     * rating.distribution.bullet.600 => 0.0003
     * rating.distribution.bullet.800 => 0.0012
     * rating.distribution.bullet.825 => 0.0057
     * rating.distribution.bullet.850 => 0.0102
     * ...
     * rating.distribution.bullet.1500 => 0.5 (hopefully)
     * ...
     * rating.distribution.bullet.2800 => 0.9997
     */
    private def monitorRatingDistribution(perfId: Perf.ID)(nbUsersList: List[NbUsers]): Unit = {
      val total = nbUsersList.sum
      (Stat.minRating to 2800 by Stat.group).toList
        .zip(nbUsersList)
        .foldLeft(0) { case (prev, (rating, nbUsers)) =>
          val acc = prev + nbUsers
          PerfType(perfId) foreach { pt =>
            lila.mon.rating.distribution(pt.key, rating).update(prev.toDouble / total)
          }
          acc
        }
        .unit
    }
  }
}

object RankingApi {

  private case class Ranking(_id: String, rating: Int, prog: Option[Int]) {
    def user = _id.takeWhile(':' !=)
  }
}
