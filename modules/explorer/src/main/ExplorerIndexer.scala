package lila.explorer

import akka.stream.scaladsl._
import strategygames.{ Player => PlayerIndex, P2, P1 }
import strategygames.GameLogic
import strategygames.format.pgn.Tag
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.ws.DefaultBodyWritables._
import lila.common.ThreadLocalRandom.nextFloat
import scala.util.{ Failure, Success, Try }

import lila.common.LilaStream
import lila.db.dsl._
import lila.game.{ Game, GameRepo, PgnDump, Player, Query }
import lila.user.{ User, UserRepo }
import lila.i18n.VariantKeys

final private class ExplorerIndexer(
    gameRepo: GameRepo,
    userRepo: UserRepo,
    getBotUserIds: lila.user.GetBotIds,
    ws: play.api.libs.ws.StandaloneWSClient,
    internalEndpoint: InternalEndpoint
)(implicit
    ec: scala.concurrent.ExecutionContext,
    mat: akka.stream.Materializer
) {

  private val separator           = "\n\n\n"
  private val datePattern         = "yyyy-MM-dd"
  private val dateFormatter       = DateTimeFormat forPattern datePattern
  private val pgnDateFormat       = DateTimeFormat forPattern "yyyy.MM.dd"
  private val internalEndPointUrl = s"$internalEndpoint/import/playstrategy"

  private def parseDate(str: String): Option[DateTime] =
    Try(dateFormatter parseDateTime str).toOption

  def apply(sinceStr: String): Funit =
    getBotUserIds() flatMap { botUserIds =>
      parseDate(sinceStr).fold(fufail[Unit](s"Invalid date $sinceStr")) { since =>
        logger.info(s"Start indexing since $since")
        val query =
          Query.createdSince(since) ++
            Query.rated ++
            Query.finished ++
            Query.turnsGt(8) ++
            Query.noProvisional ++
            Query.bothRatingsGreaterThan(1501)

        gameRepo
          .sortedCursor(query, Query.sortChronological)
          .documentSource()
          .via(LilaStream.logRate[Game]("fetch")(logger))
          .mapAsyncUnordered(8) { makeFastPgn(_, botUserIds) }
          .via(LilaStream.collect)
          .via(LilaStream.logRate("index")(logger))
          .grouped(50)
          .map(_ mkString separator)
          .mapAsyncUnordered(2) { pgn =>
            ws.url(internalEndPointUrl).put(pgn).flatMap {
              case res if res.status == 200 => funit
              case res                      => fufail(s"Stop import because of status ${res.status}")
            }
          }
          .toMat(Sink.ignore)(Keep.right)
          .run()
          .void
      }
    }

  def apply(game: Game): Funit =
    getBotUserIds() flatMap { botUserIds =>
      makeFastPgn(game, botUserIds) map {
        _ foreach flowBuffer.apply
      }
    }

  private object flowBuffer {
    private val max = 30
    private val buf = scala.collection.mutable.ArrayBuffer.empty[String]
    def apply(pgn: String): Unit = {
      buf += pgn
      val startAt = nowMillis
      if (buf.sizeIs >= max) {
        ws.url(internalEndPointUrl).put(buf mkString separator) andThen {
          case Success(res) if res.status == 200 =>
            lila.mon.explorer.index.time.record((nowMillis - startAt) / max)
            lila.mon.explorer.index.count(true).increment(max)
          case Success(res) =>
            logger.warn(s"[${res.status}]")
            lila.mon.explorer.index.count(false).increment(max)
          case Failure(err) =>
            logger.warn(s"$err", err)
            lila.mon.explorer.index.count(false).increment(max)
        }
        buf.clear()
      }
    }
  }

  private def valid(game: Game) =
    game.finished &&
      game.rated &&
      game.turnCount >= 10 &&
      game.variant != strategygames.chess.variant.FromPosition

  private def stableRating(player: Player) = player.rating ifFalse player.provisional

  // probability of the game being indexed, between 0 and 1
  private def probability(game: Game, rating: Int) = {
    game.perfType match {
      case Some(pt) =>
        pt.key match {
          case "correspondence"                        => 1
          case "rapid" | "classical" if rating >= 2000 => 1
          case "rapid" | "classical" if rating >= 1800 => 2 / 5f
          case "rapid" | "classical"                   => 1 / 8f
          case "blitz" if rating >= 2000               => 1
          case "blitz" if rating >= 1800               => 1 / 4f
          case "blitz"                                 => 1 / 15f
          case "bullet" if rating >= 2300              => 1
          case "bullet" if rating >= 2200              => 4 / 5f
          case "bullet" if rating >= 2000              => 1 / 4f
          case "bullet" if rating >= 1800              => 1 / 7f
          case "bullet"                                => 1 / 20f
          case _ if rating >= 1600                     => 1      // variant games
          case _                                       => 1 / 2f // noob variant games
        }
      case _ => 1 / 2f
    }
  }

  private def makeFastPgn(game: Game, botUserIds: Set[User.ID]): Fu[Option[String]] =
    ~(for {
      p1Rating <- stableRating(game.p1Player)
      p2Rating <- stableRating(game.p2Player)
      minPlayerRating  = if (game.variant.exotic) 1400 else 1500
      minAverageRating = if (game.variant.exotic) 1520 else 1600
      if p1Rating >= minPlayerRating
      if p2Rating >= minPlayerRating
      averageRating = (p1Rating + p2Rating) / 2
      if averageRating >= minAverageRating
      if probability(game, averageRating) > nextFloat()
      if !game.userIds.exists(botUserIds.contains)
      if valid(game)
    } yield gameRepo initialFen game flatMap { initialFen =>
      userRepo.usernamesByIds(game.userIds) map { usernames =>
        def username(playerIndex: PlayerIndex) =
          game.player(playerIndex).userId flatMap { id =>
            usernames.find(_.toLowerCase == id)
          } orElse game.player(playerIndex).userId getOrElse "?"
        val fenTags = initialFen.?? { fen =>
          List(Tag(_.FEN, fen))
        }
        val otherTags = List(
          Tag("PlayStrategyID", game.id),
          Tag(_.Variant, VariantKeys.variantName(game.variant)),
          Tag.timeControl(game.clock.map(_.config)),
          Tag(_.P1, username(P1)),
          Tag(_.P2, username(P2)),
          Tag(_.P1Elo, p1Rating),
          Tag(_.P2Elo, p2Rating),
          Tag(
            _.Result,
            PgnDump.result(
              game,
              game.variant.gameLogic match {
                case GameLogic.Draughts() => lila.pref.Pref.default.draughtsResult
                case _                    => false
              }
            )
          ),
          Tag(_.Date, pgnDateFormat.print(game.createdAt))
        )
        val allTags = fenTags ::: otherTags
        //this uses maxPlies as maxTurns
        val allActionStrs = game.actionStrs.map(_.mkString(",")).take(maxPlies).mkString(" ")
        s"${allTags.mkString("\n")}\n\n${allActionStrs}".some
      }
    })

  private val logger = lila.log("explorer")
}
