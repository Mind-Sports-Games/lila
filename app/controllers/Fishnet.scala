package controllers

import akka.util.ByteString
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import play.api.libs.json.*
import play.api.mvc.*
import scala.util.{ Failure, Success, Try, Using }

import lila.app.*
import lila.common.HTTPRequest
import lila.fishnet.JsonApi.readers.*
import lila.fishnet.JsonApi.writers.*
import lila.fishnet.{ JsonApi, Work }

final class Fishnet(env: Env) extends LilaController(env) {

  private def api    = env.fishnet.api
  private val logger = lila.log("fishnet")

  def acquire(slow: Boolean = false) =
    ClientAction[JsonApi.Request.Acquire] { data => client =>
      api.acquire(client, slow, data.fishnet.variants) addEffect { jobOpt =>
        val _ = lila.mon.fishnet.http.request(jobOpt.isDefined).increment()
      } map Right.apply
    }

  def analysis(workId: String, slow: Boolean = false, stop: Boolean = false) =
    ClientAction[JsonApi.Request.PostAnalysisLexicalUci] { data => client =>
      import lila.fishnet.FishnetApi.*
      def onComplete =
        if (stop) fuccess(Left(NoContent))
        else api.acquire(client, slow, data.fishnet.variants) map Right.apply
      api
        .postAnalysis(Work.Id(workId), client, data)
        .flatMap {
          case PostAnalysisResult.Complete(analysis) =>
            env.round.proxyRepo.updateIfPresent(analysis.id)(_.setAnalysed)
            onComplete
          case PostAnalysisResult.CompleteBackgammon(id) =>
            env.round.proxyRepo.updateIfPresent(id)(_.setAnalysed)
            fuccess(Left(NoContent))
          case _: PostAnalysisResult.Partial    => fuccess(Left(NoContent))
          case PostAnalysisResult.UnusedPartial => fuccess(Left(NoContent))
        }
        .recoverWith {
          case WorkNotFound    => onComplete
          case GameNotFound    => onComplete
          case NotAcquired     => onComplete
          case WeakAnalysis(_) => onComplete
          case e: Exception    =>
            fuccess(Left(InternalServerError(e.getMessage)))
        }
    }

  def abort(workId: String) =
    ClientAction[JsonApi.Request.Acquire] { _ => client =>
      api.abort(Work.Id(workId), client) inject Right(none)
    }

  def keyExists(key: String) =
    Action.async { _ =>
      api.keyExists(lila.fishnet.Client.Key(key)) map {
        case true  => Ok
        case false => NotFound
      }
    }

  def status =
    Action.async {
      api.status map { Ok(_) }
    }

  private def ClientAction[A <: JsonApi.Request](
      f: A => lila.fishnet.Client => Fu[Either[Result, Option[JsonApi.Work]]]
  )(implicit reads: Reads[A]): Action[JsValue] = ClientAction(parse.DefaultMaxTextLength)(f)

  private def ClientAction[A <: JsonApi.Request](maxBodyLength: Long)(
      f: A => lila.fishnet.Client => Fu[Either[Result, Option[JsonApi.Work]]]
  )(implicit reads: Reads[A]): Action[JsValue] =
    Action.async(jsonMaybeGzipped(maxBodyLength)) { req =>
      req.body
        .validate[A]
        .fold(
          err => {
            logger.warn(s"Malformed request: $err\n${req.body}")
            BadRequest(jsonError(JsError toJson err)).fuccess
          },
          data =>
            api.authenticateClient(data, HTTPRequest.ipAddress(req)) flatMap {
              case Failure(msg)    => Unauthorized(jsonError(msg.getMessage)).fuccess
              case Success(client) =>
                f(data)(client).map {
                  case Right(Some(work)) => Accepted(Json toJson work)
                  case Right(None)       => NoContent
                  case Left(result)      => result
                }
            }
        )
    }

  private def jsonMaybeGzipped(maxBodyLength: Long): BodyParser[JsValue] =
    parse.using { req =>
      if (req.headers.get(CONTENT_ENCODING).exists(_.equalsIgnoreCase("gzip")))
        parse.byteString(maxBodyLength) validate { gzipped =>
          gunzip(gzipped, maxBodyLength).flatMap { plain =>
            Try(Json parse plain.toArray).toEither.left.map { e =>
              BadRequest(s"Malformed gzipped JSON: ${e.getMessage}")
            }
          }
        }
      else parse.tolerantJson(maxBodyLength)
    }

  private def gunzip(gzipped: ByteString, maxLength: Long): Either[Result, ByteString] =
    Using(new GZIPInputStream(new ByteArrayInputStream(gzipped.toArray))) { in =>
      val out    = ByteString.newBuilder
      val buffer = new Array[Byte](16 * 1024)
      var total  = 0L
      var read   = in.read(buffer)
      while (read >= 0 && total <= maxLength) {
        total += read
        if (total <= maxLength) out.putBytes(buffer, 0, read)
        read = in.read(buffer)
      }
      (total <= maxLength).option(out.result())
    }.toEither.left
      .map(e => BadRequest(s"Could not gunzip request: ${e.getMessage}"))
      .flatMap(_.toRight(EntityTooLarge(s"Gzipped body inflates beyond $maxLength bytes")))
}
