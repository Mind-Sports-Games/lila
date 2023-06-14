package lila.puzzle

import strategygames.GameLogic
import strategygames.format.Forsyth
import strategygames.format.UciCharPair
import play.api.libs.json._
import scala.concurrent.duration._

import lila.game.{ Game, GameRepo, PerfPicker }
import lila.i18n.defaultLang

final private class GameJson(
    gameRepo: GameRepo,
    cacheApi: lila.memo.CacheApi,
    lightUserApi: lila.user.LightUserApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  val chessLib = GameLogic.Chess()

  def apply(gameId: Game.ID, plies: Int, bc: Boolean): Fu[JsObject] =
    (if (bc) bcCache else cache) get writeKey(gameId, plies)

  def noCacheBc(game: Game, plies: Int): Fu[JsObject] =
    lightUserApi preloadMany game.userIds map { _ =>
      generateBc(game, plies)
    }

  private def readKey(k: String): (Game.ID, Int) =
    k.drop(Game.gameIdSize).toIntOption match {
      case Some(ply) => (k take Game.gameIdSize, ply)
      case _         => sys error s"puzzle.GameJson invalid key: $k"
    }
  private def writeKey(id: Game.ID, ply: Int) = s"$id$ply"

  private val cache = cacheApi[String, JsObject](4096, "puzzle.gameJson") {
    _.expireAfterAccess(5 minutes)
      .maximumSize(1024)
      .buildAsyncFuture(key =>
        readKey(key) match {
          case (id, plies) => generate(id, plies, false)
        }
      )
  }

  private val bcCache = cacheApi[String, JsObject](64, "puzzle.bc.gameJson") {
    _.expireAfterAccess(5 minutes)
      .maximumSize(1024)
      .buildAsyncFuture(key =>
        readKey(key) match {
          case (id, plies) => generate(id, plies, true)
        }
      )
  }

  private def generate(gameId: Game.ID, plies: Int, bc: Boolean): Fu[JsObject] =
    gameRepo game gameId orFail s"Missing puzzle game $gameId!" flatMap { game =>
      lightUserApi preloadMany game.userIds map { _ =>
        if (bc) generateBc(game, plies)
        else generate(game, plies)
      }
    }

  private def generate(game: Game, plies: Int): JsObject =
    Json
      .obj(
        "id"      -> game.id,
        "perf"    -> perfJson(game),
        "rated"   -> game.rated,
        "players" -> playersJson(game),
        //can flatten whilst puzzles are just chess
        "pgn" -> game.actions.flatten.take(plies + 1).mkString(" ")
      )
      .add("clock", game.clock.map(_.config.show))

  private def perfJson(game: Game) = {
    val perfType = lila.rating.PerfType orDefault PerfPicker.key(game)
    Json.obj(
      "icon" -> perfType.iconChar.toString,
      "name" -> perfType.trans(defaultLang)
    )
  }

  private def playersJson(game: Game) = JsArray(game.players.map { p =>
    val userId = p.userId | "anon"
    val user   = lightUserApi.syncFallback(userId)
    Json
      .obj(
        "userId"      -> userId,
        "name"        -> s"${user.name}${p.rating.??(r => s" ($r)")}",
        "playerIndex" -> p.playerIndex.name
      )
      .add("title" -> user.title)
  })

  private def generateBc(game: Game, turns: Int): JsObject =
    Json
      .obj(
        "id"      -> game.id,
        "perf"    -> perfJson(game),
        "players" -> playersJson(game),
        "rated"   -> game.rated,
        "treeParts" -> {
          val actions = game.actions.take(turns + 1)
          for {
            lastPly <- actions.flatten.lastOption
            situation <- strategygames.Replay
              .situations(chessLib, actions, None, game.variant)
              .valueOr { err =>
                sys.error(s"GameJson.generateBc ${game.id} $err")
              }
              .lastOption
            uciMove <- situation.board.history.lastMove
          } yield Json.obj(
            "fen" -> Forsyth.>>(chessLib, situation).value,
            "ply" -> (turns + 1),
            "san" -> lastPly,
            "id"  -> UciCharPair(chessLib, uciMove).toString,
            "uci" -> uciMove.uci
          )
        }
      )
      .add("clock", game.clock.map(_.config.show))
}
