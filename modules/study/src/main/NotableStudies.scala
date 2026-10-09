package lila.study

import strategygames.variant.Variant

import lila.memo.CacheApi
import lila.memo.CacheApi.*

// the studies of the library pages (Study.onLibraryPages), by the variants their chapters play: the
// pinned ones first, then the most liked. A study whose chapters play several variants, which no
// single game's page describes, is left out unless it is pinned.
final class NotableStudies(
    studyRepo: StudyRepo,
    chapterRepo: ChapterRepo,
    cacheApi: CacheApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  private val byVariantCache = cacheApi.unit[Map[String, List[Study.Notable]]] {
    _.refreshAfterWrite(1.day)
      .buildAsyncFuture { _ =>
        studyRepo.libraryPinned zip studyRepo.libraryPopular(1000) flatMap { case (pinned, popular) =>
          val pinnedIds = pinned.map(_.id).toSet
          val ordered   = pinned ::: popular.filterNot(s => pinnedIds(s.id))
          chapterRepo.variantKeysByStudyIds(ordered.map(_.id)) map { keysOf =>
            ordered
              .flatMap { s =>
                val keys = keysOf.getOrElse(s.id, Set.empty)
                if (keys.sizeIs > 1 && !pinnedIds(s.id)) Nil else keys.map(_ -> s)
              }
              .groupMap(_._1)(_._2)
          }
        }
      }
  }

  // after a study is pinned, kept off or unfeatured, rather than wait for the day to turn
  def refresh(): Unit = byVariantCache.invalidateUnit()

  def byVariant(variant: Variant, nb: Int): Fu[List[Study.Notable]] =
    byVariantCache.getUnit.map(_.getOrElse(variant.key, Nil).take(nb))
}
