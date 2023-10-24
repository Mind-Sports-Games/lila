package lila.bot

import play.api.i18n.Lang
import play.api.libs.json._

import lila.common.Json.jodaWrites
import lila.game.JsonView._
import lila.game.{ DeadStoneOfferState, Game, GameRepo, Pov }

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
    strategygames.format.UciDump(game.variant.gameLogic, game.actions, fen, game.variant).toFuture map {
      uciMoves =>
        Json
          .obj(
            "type"            -> "gameState",
            "moves"           -> uciMoves.mkString(" "),
            "wtime"           -> millisOf(game.p1Pov),
            "btime"           -> millisOf(game.p2Pov),
            "winc"            -> game.clock.??(_.config.increment.millis),
            "binc"            -> game.clock.??(_.config.increment.millis),
            "wdraw"           -> game.p1Player.isOfferingDraw,
            "bdraw"           -> game.p2Player.isOfferingDraw,
            "status"          -> game.status.name,
            "selectedsquares" -> selectedSquaresJson(game)
          )
          .add("winner" -> game.winnerPlayerIndex)
          .add("rematch" -> rematches.of(game.id))
    }
  }

  def playerOffering(deadStoneOfferState: Option[DeadStoneOfferState]): Option[String] =
    deadStoneOfferState match {
      case Some(DeadStoneOfferState.P1Offering)      => Some("p1")
      case Some(DeadStoneOfferState.P2Offering)      => Some("p2")
      case Some(DeadStoneOfferState.AcceptedP1Offer) => Some("p1")
      case Some(DeadStoneOfferState.AcceptedP2Offer) => Some("p2")
      case _                                         => None
    }

  def selectedSquaresJson(game: Game) =
    ssStatus(game).map(s =>
      Json
        .obj(
          "status"         -> s,
          "squares"        -> game.selectedSquares.map(_.map(_.toString).mkString(" ")),
          "playerOffering" -> playerOffering(game.deadStoneOfferState)
        )
    )

  def ssStatus(game: Game): Option[String] =
    game.deadStoneOfferState
      .flatMap(s =>
        s match {
          case DeadStoneOfferState.RejectedOffer    => Some("rejected")
          case DeadStoneOfferState.P1Offering       => Some("offered")
          case DeadStoneOfferState.P2Offering       => Some("offered")
          case DeadStoneOfferState.ChooseFirstOffer => Some("pending")
          case DeadStoneOfferState.AcceptedP1Offer  => Some("accepted")
          case DeadStoneOfferState.AcceptedP2Offer  => Some("accepted")
          case _                                    => None
        }
      )

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

  implicit private val clockConfigWriter: OWrites[strategygames.ClockConfig] = OWrites { c =>
    c match {
      case c: strategygames.FischerClock.Config =>
        Json.obj(
          "initial"   -> c.limit.millis,
          "increment" -> c.increment.millis
        )
      case c: strategygames.ByoyomiClock.Config =>
        Json.obj(
          "initial"   -> c.limit.millis,
          "increment" -> c.increment.millis,
          "byoyomi"   -> c.byoyomi.millis,
          "periods"   -> c.periodsTotal
        )
    }
  }
}
