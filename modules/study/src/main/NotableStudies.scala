package lila.study

import strategygames.variant.Variant

import lila.memo.CacheApi
import lila.memo.CacheApi.*

// the studies of the library pages (Study.onLibraryPages) by variant: pinned first, then by
// likes; one playing several variants only when pinned
final class NotableStudies(
    studyRepo: StudyRepo,
    chapterRepo: ChapterRepo,
    pager: StudyPager,
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

  // a library page's tiles, rechecked against the studies as they are now: crawlers sweep these pages
  private val tilesCache = cacheApi[String, List[Study.WithChaptersAndLiked]](64, "study.libraryTiles") {
    _.expireAfterWrite(10.minutes)
      .buildAsyncFuture { key =>
        byVariantCache.getUnit.map(_.getOrElse(key, Nil).take(tilesPerPage)) flatMap { notable =>
          studyRepo.byOrderedIds(notable.map(_.id)) flatMap { studies =>
            pager
              .withChaptersAndLiking(none)(studies.filter(s => s.isPublic && s.onLibraryPages))
              .dmap(_.toList)
          }
        }
      }
  }

  // one more than the page shows: it leaves out the tutorial, linked above
  private val tilesPerPage = 7

  // after a study is pinned, hidden or unfeatured, rather than wait for the day to turn
  def refresh(): Unit = {
    byVariantCache.invalidateUnit()
    tilesCache.invalidateAll()
  }

  def byVariant(variant: Variant, nb: Int): Fu[List[Study.Notable]] =
    byVariantCache.getUnit.map(_.getOrElse(variant.key, Nil).take(nb))

  // the tiles, with the viewer's own likes
  def libraryTiles(variant: Variant, me: Option[lila.user.User]): Fu[List[Study.WithChaptersAndLiked]] =
    tilesCache.get(variant.key) flatMap { tiles =>
      me.fold(fuccess(tiles)) { u =>
        studyRepo.filterLiked(u, tiles.map(_.study.id)) dmap { liked =>
          tiles.map(t => t.copy(liked = liked(t.study.id)))
        }
      }
    }
}
