package lila.api

import strategygames.P1
import strategygames.variant.Variant
import strategygames.format.Forsyth

import play.api.libs.json.{ JsArray, JsObject, Json }

import lila.game.Pov
import lila.lobby.SeekApi
import lila.i18n.VariantKeys

final class LobbyApi(
    lightUserApi: lila.user.LightUserApi,
    seekApi: SeekApi,
    gameProxyRepo: lila.round.GameProxyRepo
)(implicit ec: scala.concurrent.ExecutionContext) {

  def apply(implicit ctx: Context): Fu[(JsObject, List[Pov])] =
    ctx.me.fold(seekApi.forAnon)(seekApi.forUser).mon(_.lobby segment "seeks") zip
      (ctx.me ?? gameProxyRepo.urgentGames).mon(_.lobby segment "urgentGames") flatMap { case (seeks, povs) =>
        val displayedPovs = povs take 9
        lightUserApi.preloadMany(displayedPovs.flatMap(_.opponent.userId)) inject {
          implicit val lang = ctx.lang
          Json.obj(
            "me" -> ctx.me.map { u =>
              Json.obj("username" -> u.username).add("isBot" -> u.isBot)
            },
            "seeks"        -> JsArray(seeks map (_.render)),
            "nowPlaying"   -> JsArray(displayedPovs map nowPlaying),
            "nbNowPlaying" -> povs.size
          ) -> displayedPovs
        }
      }

  def boardSize(variant: Variant) = variant match {
    case Variant.Draughts(v) =>
      Some(
        Json.obj(
          "size" -> Json.arr(v.boardSize.width, v.boardSize.height),
          "key"  -> v.boardSize.key
        )
      )
    case _ => None
  }

  def nowPlaying(pov: Pov) =
    Json
      .obj(
        "fullId"      -> pov.fullId,
        "gameId"      -> pov.gameId,
        "fen"         -> Forsyth.exportBoard(pov.game.variant.gameLogic, pov.game.board),
        "playerIndex" -> (if (pov.game.variant.key == "racingKings") P1 else pov.playerIndex).name,
        "lastMove"    -> ~pov.game.lastMoveKeys,
        "variant" -> Json.obj(
          "gameLogic" -> Json.obj(
            "id"   -> pov.game.variant.gameLogic.id,
            "name" -> pov.game.variant.gameLogic.name
          ),
          "gameFamily" -> pov.game.variant.gameFamily.key,
          "key"        -> pov.game.variant.key,
          "name"       -> VariantKeys.variantName(pov.game.variant),
          "boardSize"  -> boardSize(pov.game.variant)
        ),
        "speed"    -> pov.game.speed.key,
        "perf"     -> lila.game.PerfPicker.key(pov.game),
        "rated"    -> pov.game.rated,
        "hasMoved" -> pov.hasMoved,
        "opponent" -> Json
          .obj(
            "id" -> pov.opponent.userId,
            "username" -> lila.game.Namer
              .playerTextBlocking(pov.opponent, withRating = false)(lightUserApi.sync)
          )
          .add("rating" -> pov.opponent.rating)
          .add("ai" -> pov.opponent.aiLevel),
        "isMyTurn" -> pov.isMyTurn
      )
      .add("secondsLeft" -> pov.remainingSeconds)
      .add("tournamentId" -> pov.game.tournamentId)
      .add("swissId" -> pov.game.tournamentId)
}
