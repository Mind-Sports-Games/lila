package lila.bot

import play.api.i18n.Lang
import play.api.libs.json._

import lila.common.Json.jodaWrites
import lila.game.JsonView._
import lila.game.{ Game, GameRepo, Pov }

import strategygames.GameLogic

final class BotJsonView(
    lightUserApi: lila.user.LightUserApi,
    gameRepo: GameRepo,
    rematches: lila.game.Rematches
)(implicit ec: scala.concurrent.ExecutionContext) {

  def gameFull(game: Game)(implicit lang: Lang): Fu[JsObject] = gameRepo.withInitialFen(game) flatMap gameFull

  def gameFull(wf: Game.WithInitialFen)(implicit lang: Lang): Fu[JsObject] =
    gameState(wf) map { state =>
      gameImmutable(wf) ++ Json.obj(
        "type"  -> "gameFull",
        "state" -> state
      )
    }

  def gameImmutable(wf: Game.WithInitialFen)(implicit lang: Lang): JsObject = {
    import wf._
    Json
      .obj(
        "id"      -> game.id,
        "variant" -> game.variant,
        "clock"   -> game.clock.map(_.config),
        "speed"   -> game.speed.key,
        "perf" -> game.perfType.map { p =>
          Json.obj("name" -> p.trans)
        },
        "rated"      -> game.rated,
        "createdAt"  -> game.createdAt,
        "p1"         -> playerJson(game.p1Pov),
        "p2"         -> playerJson(game.p2Pov),
        "initialFen" -> fen.fold("startpos")(_.value)
      )
      .add("tournamentId" -> game.tournamentId)
  }

  def gameState(wf: Game.WithInitialFen): Fu[JsObject] = {
    // NOTE: this uses UciDump to generate the moves for the bot
    // while the round game json uses the round.StepBuilder object.
    // not sure why the difference.
    import wf._
    strategygames.format.UciDump(game.variant.gameLogic, game.pgnMoves, fen, game.variant).toFuture map {
      uciMoves =>
        Json
          .obj(
            "type"   -> "gameState",
            "moves"  -> uciMoves.mkString(" "),
            "wtime"  -> millisOf(game.p1Pov),
            "btime"  -> millisOf(game.p2Pov),
            "winc"   -> game.clock.??(_.config.increment.millis),
            "binc"   -> game.clock.??(_.config.increment.millis),
            "wdraw"  -> game.p1Player.isOfferingDraw,
            "bdraw"  -> game.p2Player.isOfferingDraw,
            "status" -> game.status.name
          )
          .add("winner" -> game.winnerPlayerIndex)
          .add("rematch" -> rematches.of(game.id))
    }
  }

  def chatLine(username: String, text: String, player: Boolean) =
    Json.obj(
      "type"     -> "chatLine",
      "room"     -> (if (player) "player" else "spectator"),
      "username" -> username,
      "text"     -> text
    )

  private def playerJson(pov: Pov) = {
    val light = pov.player.userId flatMap lightUserApi.sync
    Json
      .obj()
      .add("aiLevel" -> pov.player.aiLevel)
      .add("id" -> light.map(_.id))
      .add("name" -> light.map(_.name))
      .add("title" -> light.map(_.title))
      .add("rating" -> pov.player.rating)
      .add("provisional" -> pov.player.provisional)
  }

  private def millisOf(pov: Pov): Int =
    pov.game.clock
      .map(_.remainingTime(pov.playerIndex).millis.toInt)
      .orElse(pov.game.correspondenceClock.map(_.remainingTime(pov.playerIndex).toInt * 1000))
      .getOrElse(Int.MaxValue)

  implicit private val clockConfigWriter: OWrites[strategygames.Clock.Config] = OWrites { c =>
    Json.obj(
      "initial"   -> c.limit.millis,
      "increment" -> c.increment.millis
    )
  }
}
