package lila.mod

import lila.rating.{ Perf, PerfType }
import lila.user.{ RankingApi, User, UserRepo }

// writes a perf changed outside of a game: the user's perfs, their rating history and the leaderboard
final private class RatingWriter(
    userRepo: UserRepo,
    historyApi: lila.history.HistoryApi,
    rankingApi: RankingApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  def apply(user: User, pt: PerfType, perf: Perf): Funit =
    userRepo.setPerf(user.id, pt, perf) >>
      historyApi.setPerfRating(user, pt, perf.intRating) >>
      rankingApi.save(user, pt, perf)
}
