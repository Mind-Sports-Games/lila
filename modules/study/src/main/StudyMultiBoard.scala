package lila.study

import BSONHandlers._
import strategygames.{ Player => PlayerIndex }
import strategygames.format.pgn.Tags
import strategygames.format.{ FEN, Uci }
import com.github.blemale.scaffeine.AsyncLoadingCache
import JsonView._
import play.api.libs.json._
import reactivemongo.api.bson._
import scala.concurrent.duration._

import lila.common.config.MaxPerPage
import lila.common.paginator.AdapterLike
import lila.common.paginator.{ Paginator, PaginatorJson }
import lila.db.dsl._

final class StudyMultiBoard(
    chapterRepo: ChapterRepo,
    cacheApi: lila.memo.CacheApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  private val maxPerPage = MaxPerPage(9)

  import StudyMultiBoard._
  import handlers._

  def json(studyId: Study.Id, page: Int, playing: Boolean): Fu[JsObject] = {
    if (page == 1 && !playing) firstPageCache.get(studyId)
    else fetch(studyId, page, playing)
  } map { PaginatorJson(_) }

  def invalidate(studyId: Study.Id): Unit = firstPageCache.synchronous().invalidate(studyId)

  private val firstPageCache: AsyncLoadingCache[Study.Id, Paginator[ChapterPreview]] =
    cacheApi.scaffeine
      .refreshAfterWrite(4 seconds)
      .expireAfterAccess(10 minutes)
      .buildAsyncFuture[Study.Id, Paginator[ChapterPreview]] { fetch(_, 1, playing = false) }

  private val playingSelector = $doc("tags" -> "Result:*", "relay.path" $ne "")

  private def fetch(studyId: Study.Id, page: Int, playing: Boolean): Fu[Paginator[ChapterPreview]] =
    Paginator[ChapterPreview](
      new ChapterPreviewAdapter(studyId, playing),
      currentPage = page,
      maxPerPage = maxPerPage
    )

  final private class ChapterPreviewAdapter(studyId: Study.Id, playing: Boolean)
      extends AdapterLike[ChapterPreview] {

    private val selector = $doc("studyId" -> studyId) ++ playing.??(playingSelector)

    def nbResults: Fu[Int] = chapterRepo.coll(_.countSel(selector))

    def slice(offset: Int, length: Int): Fu[Seq[ChapterPreview]] =
      chapterRepo
        .coll {
          _.aggregateList(length, readPreference = readPref) { framework =>
            import framework._
            Match(selector) -> List(
              Sort(Ascending("order")),
              Skip(offset),
              Limit(length),
              Project(
                $doc(
                  "comp" -> $doc(
                    "$function" -> $doc(
                      "lang" -> "js",
                      "args" -> $arr("$root", "$tags"),
                      "body" -> """function(root, tags) {
                    |tags = tags.filter(t => t.startsWith('P1') || t.startsWith('P2') || t.startsWith('Result'));
                    |const node = tags.length ? Object.keys(root).reduce((acc, i) => (root[i].p > acc.p) ? root[i] : acc, root['_']) : root['_'];
                    |return {node:{fen:node.f,uci:node.u},tags} }""".stripMargin
                    )
                  ),
                  "orientation" -> "$setup.orientation",
                  "name"        -> true
                )
              )
            )
          }
        }
        .map { r =>
          for {
            doc     <- r
            preview <- chapterPreview(doc)
          } yield preview
        }
  }

  private object handlers {

    implicit val previewPlayerWriter: Writes[ChapterPreview.Player] = Writes[ChapterPreview.Player] { p =>
      Json
        .obj("name" -> p.name)
        .add("title" -> p.title)
        .add("rating" -> p.rating)
    }

    implicit val previewPlayersWriter: Writes[ChapterPreview.Players] = Writes[ChapterPreview.Players] {
      players =>
        Json.obj("p1" -> players.p1, "p2" -> players.p2)
    }

    implicit val previewWriter: Writes[ChapterPreview] = Json.writes[ChapterPreview]
  }
}

object StudyMultiBoard {

  case class ChapterPreview(
      id: Chapter.Id,
      name: Chapter.Name,
      players: Option[ChapterPreview.Players],
      orientation: PlayerIndex,
      fen: FEN,
      lastMove: Option[Uci],
      playing: Boolean
  )

  object ChapterPreview {

    case class Player(name: String, title: Option[String], rating: Option[Int])

    type Players = PlayerIndex.Map[Player]

    def players(tags: Tags): Option[Players] =
      for {
        wName <- tags(_.P1)
        bName <- tags(_.P2)
      } yield PlayerIndex.Map(
        p1 = Player(wName, tags(_.P1Title), tags(_.P1Elo) flatMap (_.toIntOption)),
        p2 = Player(bName, tags(_.P2Title), tags(_.P2Elo) flatMap (_.toIntOption))
      )
  }
}
