package lila.study

import strategygames.variant.Variant

import lila.memo.CacheApi
import lila.memo.CacheApi.*

// the studies a crawler may see (Study.notable), by the variants their chapters play;
// the site's own studies first, then by rank
final class NotableStudies(
    studyRepo: StudyRepo,
    chapterRepo: ChapterRepo,
    cacheApi: CacheApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  private val byVariantCache = cacheApi.unit[Map[String, List[Study.Notable]]] {
    _.refreshAfterWrite(1.day)
      .buildAsyncFuture { _ =>
        studyRepo.notable(1000) flatMap { studies =>
          chapterRepo.variantKeysByStudyIds(studies.map(_.id)) map { keysOf =>
            val (bySite, others) = studies.partition(_.bySite)
            (bySite ::: others)
              .flatMap(s => keysOf.getOrElse(s.id, Set.empty).map(_ -> s))
              .groupMap(_._1)(_._2)
          }
        }
      }
  }

  def byVariant(variant: Variant, nb: Int): Fu[List[Study.Notable]] =
    byVariantCache.getUnit.map(_.getOrElse(variant.key, Nil).take(nb))
}
