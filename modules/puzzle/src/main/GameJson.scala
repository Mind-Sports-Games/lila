package lila.puzzle

import strategygames.format.Forsyth
import strategygames.format.UciCharPair
import play.api.libs.json._
import scala.concurrent.duration._

import lila.game.{ Game, GameRepo, PerfPicker }
import strategygames.variant.Variant
import lila.i18n.defaultLang

final private class GameJson(
    gameRepo: GameRepo,
    cacheApi: lila.memo.CacheApi,
    lightUserApi: lila.user.LightUserApi
)(implicit ec: scala.concurrent.ExecutionContext) {

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
        "variant" -> variantJson(game),
        "rated"   -> game.rated,
        "players" -> playersJson(game),
        //can flatten whilst puzzles are just chess
        "actionStrs" -> game.actionStrs.flatten.take(plies + 1).mkString(" ")
      )
      .add("clock", game.clock.map(_.config.show))

  private def perfJson(game: Game) = {
    val perfType = lila.rating.PerfType orDefault PerfPicker.key(game)
    Json.obj(
      "icon" -> perfType.iconChar.toString,
      "name" -> perfType.trans(defaultLang)
    )
  }

  //TODO wil need to support draughts differently (see game/jsonView example)
  private def variantJson(game: Game) = Json.obj(
    "key"       -> game.variant.key,
    "boardSize" -> boardSize(game.variant)
  )

  def boardSize(variant: Variant) = variant match {
    case Variant.Draughts(v) =>
      Json.obj(
        "size" -> Json.arr(v.boardSize.width, v.boardSize.height),
        "key"  -> v.boardSize.key
      )
    case Variant.FairySF(fairyVariant) =>
      Json.obj(
        "width"  -> fairyVariant.boardSize.width,
        "height" -> fairyVariant.boardSize.height
      )
    case Variant.Samurai(samuraiVariant) =>
      Json.obj(
        "width"  -> samuraiVariant.boardSize.width,
        "height" -> samuraiVariant.boardSize.height
      )
    case Variant.Togyzkumalak(togyzkumalakVariant) =>
      Json.obj(
        "width"  -> togyzkumalakVariant.boardSize.width,
        "height" -> togyzkumalakVariant.boardSize.height
      )
    case Variant.Go(goVariant) =>
      Json.obj(
        "width"  -> goVariant.boardSize.width,
        "height" -> goVariant.boardSize.height
      )
    case Variant.Backgammon(backgammonVariant) =>
      Json.obj(
        "width"  -> backgammonVariant.boardSize.width,
        "height" -> backgammonVariant.boardSize.height
      )
    case Variant.Abalone(abaloneVariant) =>
      Json.obj(
        "width"  -> abaloneVariant.boardSize.width,
        "height" -> abaloneVariant.boardSize.height
      )
    case _ =>
      Json.obj(
        "width"  -> 8,
        "height" -> 8
      )
  }

  private def playersJson(game: Game) = JsArray(game.players.map { p =>
    val userId = p.userId | "anon"
    val user   = lightUserApi.syncFallback(userId)
    Json
      .obj(
        "userId"      -> userId,
        "name"        -> s"${user.name}${p.rating.??(r => s" ($r)")}",
        "playerIndex" -> p.playerIndex.name,
        "playerColor" -> game.variant.playerColors(p.playerIndex)
      )
      .add("title" -> user.title)
  })

  private def generateBc(game: Game, turns: Int): JsObject =
    Json
      .obj(
        "id"      -> game.id,
        "perf"    -> perfJson(game),
        "players" -> playersJson(game),
        "variant" -> variantJson(game),
        "rated"   -> game.rated,
        "treeParts" -> {
          val actionStrs = game.actionStrs.take(turns + 1)
          val lib        = game.variant.gameLogic
          for {
            //TODO: multiaction ok for now as just chess puzzles
            lastPly <- actionStrs.flatten.lastOption
            situation <- strategygames.Replay
              .situations(lib, actionStrs, None, game.variant)
              .valueOr { err =>
                sys.error(s"GameJson.generateBc ${game.id} $err")
              }
              .lastOption
            uciMove <- situation.board.history.lastAction
          } yield Json.obj(
            "fen" -> Forsyth.>>(lib, situation).value,
            "ply" -> (turns + 1),
            "san" -> lastPly,
            "id"  -> UciCharPair(lib, uciMove).toString,
            "uci" -> uciMove.uci
          )
        }
      )
      .add("clock", game.clock.map(_.config.show))
}
