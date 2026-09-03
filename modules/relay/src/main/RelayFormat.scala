package lila.relay

import io.lemonlabs.uri.*
import play.api.libs.json.*
import play.api.libs.ws.StandaloneWSClient
import play.api.libs.ws.DefaultBodyReadables.*
import scala.concurrent.duration.*

import strategygames.variant.Variant
import strategygames.chess.variant.Variant as ChessVariant
import lila.study.MultiPgn
import lila.memo.CacheApi
import lila.memo.CacheApi.*

final private class RelayFormatApi(ws: StandaloneWSClient, cacheApi: CacheApi)(implicit
    ec: scala.concurrent.ExecutionContext
) {

  import RelayFormat.*
  import RelayRound.Sync.UpstreamUrl

  /* Deliberately no refreshAfterWrite. The upstream URL is user supplied, and a
   * failed refresh leaves the entry stale, so an unreachable source refreshes
   * again on the very next read - once per sync tick, each one several requests
   * from guessFormat. Loading on demand instead reports failures through the
   * sync log, where the broadcaster can see them.
   * The cached value is an Either, so guessFormat never fails - it reports 
   * "not found yet" as a Left, which get() turns into a failed future for callers,
   * invalidating the entry so the next sync tick tries again from scratch.
   * Use refresh() to re-guess the format of a source that has changed. */
  private val cache = cacheApi[UpstreamUrl.WithRound, Either[String, RelayFormat]](8, "relay.format") {
    _.expireAfterAccess(20 minutes)
      .buildAsyncFuture(guessFormat)
  }

  def get(upstream: UpstreamUrl.WithRound): Fu[RelayFormat] =
    cache get upstream flatMap {
      case Right(format) => fuccess(format)
      case Left(reason)  =>
        cache.invalidate(upstream)
        fufail(reason)
    }

  def refresh(upstream: UpstreamUrl.WithRound): Unit = cache.invalidate(upstream)

  private def guessFormat(upstream: UpstreamUrl.WithRound): Fu[Either[String, RelayFormat]] = {

    val originalUrl = Url parse upstream.url

    // http://view.livechesscloud.com/ed5fb586-f549-4029-a470-d590f8e30c76
    // The tournament is often linked days ahead of the round starting, so the
    // index is reachable long before it lists any games: that is expected and
    // gets its own reason, distinct from a source that is simply wrong.
    def guessLcc(url: Url): Fu[Option[Either[String, RelayFormat]]] =
      url.toString match {
        case UpstreamUrl.LccRegex(id) =>
          guessManyFilesDetailed(
            Url.parse(
              s"http://1.pool.livechesscloud.com/get/$id/round-${upstream.round | 1}/index.json"
            )
          ) map {
            case IndexResult.Found(format)    => Right(format).some
            case IndexResult.NoGamesYet       => Left(noGamesYetReason).some
            case IndexResult.IndexUnreachable => Left(notFoundReason).some
          }
        case _ => fuccess(none)
      }

    def guessSingleFile(url: Url): Fu[Option[RelayFormat]] =
      lila.common.LilaFuture.find(
        List(
          url.some,
          (!url.path.parts.contains(mostCommonSingleFileName)).option(addPart(url, mostCommonSingleFileName))
        ).flatten.distinct
      )(looksLikePgn) dmap2 { (u: Url) =>
        SingleFile(pgnDoc(u))
      }

    def guessManyFilesDetailed(url: Url): Fu[IndexResult] =
      lila.common.LilaFuture.find(
        List(url) ::: mostCommonIndexNames.filterNot(url.path.parts.contains).map(addPart(url, _))
      )(looksLikeJson) flatMap {
        case None => fuccess(IndexResult.IndexUnreachable)
        case Some(index) =>
          val jsonUrl = (n: Int) => jsonDoc(replaceLastPart(index, s"game-$n.json"))
          val pgnUrl  = (n: Int) => pgnDoc(replaceLastPart(index, s"game-$n.pgn"))
          looksLikeJson(jsonUrl(1).url)
            .map(_.option(jsonUrl))
            .orElse(looksLikePgn(pgnUrl(1).url).map(_.option(pgnUrl))) map {
            case Some(doc) => IndexResult.Found(ManyFiles(index, doc))
            case None      => IndexResult.NoGamesYet
          }
      }

    def guessManyFiles(url: Url): Fu[Option[RelayFormat]] =
      guessManyFilesDetailed(url) map {
        case IndexResult.Found(format) => format.some
        case _                         => none
      }

    guessLcc(originalUrl) flatMap {
      case Some(outcome) => fuccess(outcome)
      case None =>
        guessSingleFile(originalUrl)
          .orElse(guessManyFiles(originalUrl))
          .map {
            case Some(format) => Right(format)
            case None         => Left(notFoundReason)
          }
    }
  } addEffect {
    case Right(format) => logger.info(s"guessed format of $upstream: $format")
    case Left(_)       => ()
  }

  private def httpGet(url: Url): Fu[Option[String]] =
    ws.url(url.toString)
      .withRequestTimeout(4.seconds)
      .get()
      .map {
        case res if res.status == 200 => res.body[String].some
        case _                        => none
      }

  private def looksLikePgn(body: String): Boolean = {
    // TODO: Only support chess PGN for now.
    implicit val variant: Variant = Variant.Chess(ChessVariant.default)
    MultiPgn.split(body, 1).value.headOption so { pgn =>
      RelayGame.isChessOnly(pgn) &&
      scala.util.Try(lila.study.PgnImport(pgn, Nil).isValid).getOrElse(false)
    }
  }
  private def looksLikePgn(url: Url): Fu[Boolean] = httpGet(url).map { _ exists looksLikePgn }

  private def looksLikeJson(body: String): Boolean =
    try {
      Json.parse(body) != JsNull
    } catch {
      case _: Exception => false
    }
  private def looksLikeJson(url: Url): Fu[Boolean] = httpGet(url).map { _ exists looksLikeJson }
}

sealed private trait RelayFormat

private object RelayFormat {

  sealed trait DocFormat
  object DocFormat {
    case object Json extends DocFormat
    case object Pgn  extends DocFormat
  }

  case class Doc(url: Url, format: DocFormat)

  def jsonDoc(url: Url) = Doc(url, DocFormat.Json)
  def pgnDoc(url: Url)  = Doc(url, DocFormat.Pgn)

  case class SingleFile(doc: Doc) extends RelayFormat

  type GameNumberToDoc = Int => Doc

  case class ManyFiles(jsonIndex: Url, game: GameNumberToDoc) extends RelayFormat {
    override def toString = s"Manyfiles($jsonIndex, ${game(0)})"
  }

  def addPart(url: Url, part: String)             = url.withPath(url.path addPart part)
  def replaceLastPart(url: Url, withPart: String) =
    if (url.path.isEmpty) addPart(url, withPart)
    else
      url.withPath {
        url.path.withParts {
          url.path.parts.init :+ withPart
        }
      }

  val mostCommonSingleFileName = "games.pgn"
  val mostCommonIndexNames     = List("round.json", "index.json")

  val notFoundReason   = "No games found, check your source URL"
  val noGamesYetReason = "Connected to LiveChessCloud, no games published yet"

  sealed trait IndexResult
  object IndexResult {
    case object IndexUnreachable extends IndexResult
    case object NoGamesYet       extends IndexResult
    case class Found(format: RelayFormat) extends IndexResult
  }
}
