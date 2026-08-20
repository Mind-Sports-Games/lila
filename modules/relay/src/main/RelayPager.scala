package lila.relay

import reactivemongo.api.ReadPreference

import lila.common.config.MaxPerPage
import lila.common.paginator.{ AdapterLike, Paginator }
import lila.db.dsl.*

final class RelayPager(tourRepo: RelayTourRepo, roundRepo: RelayRoundRepo)(implicit
    ec: scala.concurrent.ExecutionContext
) {

  import BSONHandlers.*

  def inactive(page: Int): Fu[Paginator[RelayTour.WithLastRound]] =
    Paginator(
      adapter = new AdapterLike[RelayTour.WithLastRound] {

        private val selector = tourRepo.selectors.official ++ tourRepo.selectors.inactive

        def nbResults: Fu[Int] = tourRepo.coll.countSel(selector)

        def slice(offset: Int, length: Int): Fu[List[RelayTour.WithLastRound]] =
          tourRepo.coll
            .aggregateList(maxDocs = length, ReadPreference.secondaryPreferred) { framework =>
              import framework.*
              Match(selector) -> List(
                Sort(Descending("syncedAt")),
                Skip(offset),
                Limit(length),
                PipelineOperator(
                  $doc(
                    "$lookup" -> $doc(
                      "from"     -> roundRepo.coll.name,
                      "as"       -> "round",
                      "let"      -> $doc("id" -> "$_id"),
                      "pipeline" -> $arr(
                        $doc(
                          "$match" -> $doc(
                            "$expr" -> $doc(
                              $doc("$eq" -> $arr("$tourId", "$$id"))
                            )
                          )
                        ),
                        $doc(
                          "$sort" -> $doc(
                            "startedAt" -> -1,
                            "startsAt"  -> -1,
                            "name"      -> -1
                          )
                        ),
                        $doc("$limit"     -> 1),
                        $doc("$addFields" -> $doc("sync.log" -> $arr()))
                      )
                    )
                  )
                ),
                UnwindField("round")
              )
            }
            .map { docs =>
              for {
                doc   <- docs
                tour  <- doc.asOpt[RelayTour]
                round <- doc.getAsOpt[RelayRound]("round")
              } yield RelayTour.WithLastRound(tour, round)
            }
      },
      currentPage = page,
      maxPerPage = MaxPerPage(20)
    )
}
