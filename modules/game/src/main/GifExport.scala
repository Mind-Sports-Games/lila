package lila.game

import akka.stream.scaladsl._
import akka.util.ByteString
import strategygames.format.{ FEN, Forsyth, Uci }
import strategygames.{ Centis, Player => PlayerIndex, Replay, Situation, Game => ChessGame }
import play.api.libs.json._
import play.api.libs.ws.JsonBodyWritables._
import play.api.libs.ws.StandaloneWSClient

import lila.common.config.BaseUrl
import lila.common.Json._
import lila.common.Maths

final class GifExport(
    ws: StandaloneWSClient,
    lightUserApi: lila.user.LightUserApi,
    baseUrl: BaseUrl,
    url: String
)(implicit ec: scala.concurrent.ExecutionContext) {
  private val targetMedianTime = Centis(80)
  private val targetMaxTime    = Centis(200)

  def fromPov(pov: Pov, initialFen: Option[FEN]): Fu[Source[ByteString, _]] =
    lightUserApi preloadMany pov.game.userIds flatMap { _ =>
      ws.url(s"$url/game.gif")
        .withMethod("POST")
        .addHttpHeaders("Content-Type" -> "application/json")
        .withBody(
          Json.obj(
            "p1"          -> Namer.playerTextBlocking(pov.game.p1Player, withRating = true)(lightUserApi.sync),
            "p2"          -> Namer.playerTextBlocking(pov.game.p2Player, withRating = true)(lightUserApi.sync),
            "comment"     -> s"${baseUrl.value}/${pov.game.id} rendered with https://github.com/niklasf/lila-gif",
            "orientation" -> pov.playerIndex.name,
            "delay"       -> targetMedianTime.centis, // default delay for frames
            "frames"      -> frames(pov.game, initialFen)
          )
        )
        .stream() flatMap {
        case res if res.status != 200 =>
          logger.warn(s"GifExport pov ${pov.game.id} ${res.status}")
          fufail(res.statusText)
        case res => fuccess(res.bodyAsSource)
      }
    }

  def gameThumbnail(game: Game): Fu[Source[ByteString, _]] = {
    val query = List(
      "fen"         -> (Forsyth.>>(game.variant.gameLogic, game.stratGame)).value,
      "p1"          -> Namer.playerTextBlocking(game.p1Player, withRating = true)(lightUserApi.sync),
      "p2"          -> Namer.playerTextBlocking(game.p2Player, withRating = true)(lightUserApi.sync),
      "orientation" -> game.naturalOrientation.name
    ) ::: List(
      game.lastMoveKeys.map { "lastMove" -> _ },
      game.situation.checkSquare.map { "check" -> _.key }
    ).flatten

    lightUserApi preloadMany game.userIds flatMap { _ =>
      ws.url(s"$url/image.gif")
        .withMethod("GET")
        .withQueryStringParameters(query: _*)
        .stream() flatMap {
        case res if res.status != 200 =>
          logger.warn(s"GifExport gameThumbnail ${game.id} ${res.status}")
          fufail(res.statusText)
        case res => fuccess(res.bodyAsSource)
      }
    }
  }

  def thumbnail(fen: FEN, lastMove: Option[String], orientation: PlayerIndex): Fu[Source[ByteString, _]] = {
    val query = List(
      "fen"         -> fen.value,
      "orientation" -> orientation.name
    ) ::: List(
      lastMove.map { "lastMove" -> _ }
    ).flatten

    ws.url(s"$url/image.gif")
      .withMethod("GET")
      .withQueryStringParameters(query: _*)
      .stream() flatMap {
      case res if res.status != 200 =>
        logger.warn(s"GifExport thumbnail $fen ${res.status}")
        fufail(res.statusText)
      case res => fuccess(res.bodyAsSource)
    }
  }

  private def scaleMoveTimes(plyTimes: Vector[Centis]): Vector[Centis] = {
    // goal for bullet: close to real-time
    // goal for classical: speed up to reach target median, avoid extremely
    // fast moves, unless they were actually played instantly
    Maths.median(plyTimes.map(_.centis)).map(Centis.apply).filter(_ >= targetMedianTime) match {
      case Some(median) =>
        val scale = targetMedianTime.centis.toDouble / median.centis.atLeast(1).toDouble
        plyTimes.map { t =>
          if (t * 2 < median) t atMost (targetMedianTime *~ 0.5)
          else t *~ scale atLeast (targetMedianTime *~ 0.5) atMost targetMaxTime
        }
      case None => plyTimes.map(_ atMost targetMaxTime)
    }
  }

  private def frames(game: Game, initialFen: Option[FEN]) = {
    Replay.gamePlyWhileValid(
      game.variant.gameLogic,
      game.actionStrs,
      game.startPlayerIndex,
      game.activePlayer,
      initialFen | game.variant.initialFen,
      game.variant
    ) match {
      case (init, games, _) =>
        val steps = (init, None) :: (games map {
          case (g, Uci.ChessWithSan(strategygames.chess.format.Uci.WithSan(uci, _))) =>
            (g, Uci.wrap(uci).some)
          case _ => sys.error("Need to implement draughts version") // TODO: DRAUGHTS - implement this.
        })
        framesRec(
          steps.zip(scaleMoveTimes(~game.plyTimes).map(_.some).padTo(steps.length, None)),
          Json.arr()
        )
    }
  }

  @annotation.tailrec
  private def framesRec(games: List[((ChessGame, Option[Uci]), Option[Centis])], arr: JsArray): JsArray =
    games match {
      case Nil =>
        arr
      case ((game, uci), scaledMoveTime) :: tail =>
        // longer delay for last frame
        val delay = if (tail.isEmpty) Centis(500).some else scaledMoveTime
        framesRec(tail, arr :+ frame(game.situation, uci, delay))
    }

  private def frame(situation: Situation, uci: Option[Uci], delay: Option[Centis]) =
    Json
      .obj(
        "fen"      -> (Forsyth.>>(situation.board.variant.gameLogic, situation).value),
        "lastMove" -> uci.map(_.uci)
      )
      .add("check", situation.checkSquare.map(_.key))
      .add("delay", delay.map(_.centis))
}
