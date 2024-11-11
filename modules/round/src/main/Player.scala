package lila.round

import strategygames.format.{ Forsyth, Uci }
import strategygames.{
  Action,
  Centis,
  MoveMetrics,
  Pos,
  Role,
  Status,
  Drop => StratDrop,
  Move => StratMove,
  Pass => StratPass,
  Lift => StratLift,
  EndTurn => StratEndTurn,
  DiceRoll => StratRollDice,
  Undo => StratUndo,
  SelectSquares => StratSelectSquares
}
import strategygames.chess

import actorApi.round.{ DrawNo, ForecastPlay, HumanPlay, TakebackNo, TooManyPlies }
import lila.game.actorApi.MoveGameEvent
import lila.common.Bus
import lila.game.{ Game, Pov, Progress, UciMemo }
import lila.game.Game.PlayerId
import cats.data.Validated

final private class Player(
    fishnetPlayer: lila.fishnet.FishnetPlayer,
    finisher: Finisher,
    scheduleExpiration: ScheduleExpiration,
    scheduleActionExpiration: ScheduleActionExpiration,
    uciMemo: UciMemo
)(implicit ec: scala.concurrent.ExecutionContext) {

  sealed private trait ActionResult
  private case object Flagged                                          extends ActionResult
  private case class ActionApplied(progress: Progress, action: Action) extends ActionResult

  private[round] def human(play: HumanPlay, round: RoundDuct)(
      pov: Pov
  )(implicit proxy: GameProxy): Fu[Events] =
    play match {
      case HumanPlay(_, uci, blur, lag, _, finalSquare) =>
        pov match {
          case Pov(game, _) if game.plies > Game.maxPlies =>
            round ! TooManyPlies
            fuccess(Nil)
          case Pov(game, _) if game.selectedSquaresOfferDoesNotMatchUci(uci) =>
            fufail(
              ClientError(s"$pov game selected squares (${game.selectedSquares}) doesn't match uci ($uci)")
            )
          case Pov(game, playerIndex) if game playableBy playerIndex =>
            applyUci(game, uci, blur, lag, finalSquare)
              .leftMap(e => s"$pov $e")
              .fold(errs => fufail(ClientError(errs)), fuccess)
              .flatMap {
                case Flagged => finisher.outOfTime(game)
                case ActionApplied(progress, action) =>
                  proxy.save(progress) >>
                    postHumanOrBotPlay(round, pov, progress, action)
              }
          case Pov(game, _) if game.finished => fufail(ClientError(s"$pov game is finished"))
          case Pov(game, _) if game.aborted  => fufail(ClientError(s"$pov game is aborted"))
          case Pov(game, playerIndex) if !game.turnOf(playerIndex) =>
            fufail(ClientError(s"$pov not your turn"))
          case _ => fufail(ClientError(s"$pov move refused for some reason"))
        }
    }

  private[round] def bot(uci: Uci, round: RoundDuct)(pov: Pov)(implicit proxy: GameProxy): Fu[Events] =
    pov match {
      case Pov(game, _) if game.plies > Game.maxPlies =>
        round ! TooManyPlies
        fuccess(Nil)
      case Pov(game, _) if game.selectedSquaresOfferDoesNotMatchUci(uci) =>
        fufail(ClientError(s"$pov game selected squares (${game.selectedSquares}) doesn't match uci ($uci)"))
      case Pov(game, playerIndex) if game playableBy playerIndex =>
        //NOTE: Step 3: Apply UCI
        applyUci(game, uci, blur = false, botLag)
          .fold(errs => fufail(ClientError(errs)), fuccess)
          .flatMap {
            // NOTE: Step 4: If we were unable to apply the move due to being out of time, report it here.
            case Flagged => finisher.outOfTime(game)
            case ActionApplied(progress, action) =>
              proxy.save(progress) >> postHumanOrBotPlay(round, pov, progress, action)
          }
      case Pov(game, _) if game.finished                       => fufail(GameIsFinishedError(pov))
      case Pov(game, _) if game.aborted                        => fufail(ClientError(s"$pov game is aborted"))
      case Pov(game, playerIndex) if !game.turnOf(playerIndex) => fufail(ClientError(s"$pov not your turn"))
      case _                                                   => fufail(ClientError(s"$pov move refused for some reason"))
    }

  private def postHumanOrBotPlay(
      round: RoundDuct,
      pov: Pov,
      progress: Progress,
      action: Action
  )(implicit proxy: GameProxy): Fu[Events] = {
    if (pov.game.hasAi) uciMemo.add(pov.game, action)
    notifyMove(action, progress.game)
    if (progress.game.finished) moveFinish(progress.game) dmap { progress.events ::: _ }
    else {
      if (progress.game.playableByAi) requestFishnet(progress.game, round)
      if (pov.opponent.isOfferingDraw) round ! DrawNo(PlayerId(pov.player.id))
      if (pov.player.isProposingTakeback) round ! TakebackNo(PlayerId(pov.player.id))
      if (progress.game.forecastable) {
        (action match {
          case m: StratMove => Some(m)
          case _            => None
        }).map { move =>
          round ! ForecastPlay(move)
        }
      }
      scheduleExpiration(progress.game)
      if (progress.game.selectSquaresPossible) scheduleActionExpiration(progress.game)
      fuccess(progress.events)
    }
  }

  private[round] def fishnet(game: Game, ply: Int, uci: Uci)(implicit proxy: GameProxy): Fu[Events] =
    if (game.playable && game.player.isAi && game.playedPlies == ply) {
      applyUci(game, uci, blur = false, metrics = fishnetLag)
        .fold(errs => fufail(ClientError(errs)), fuccess)
        .flatMap {
          case Flagged => finisher.outOfTime(game)
          case ActionApplied(progress, action) =>
            proxy.save(progress) >>-
              uciMemo.add(progress.game, action) >>-
              lila.mon.fishnet.move(~game.aiLevel).increment().unit >>-
              notifyMove(action, progress.game) >> {
                if (progress.game.finished) moveFinish(progress.game) dmap { progress.events ::: _ }
                else
                  fuccess(progress.events)
              }
        }
    } else
      fufail(
        FishnetError(
          s"Not AI turn move: $uci id: ${game.id} playable: ${game.playable} player: ${game.player} hasAI: ${game.player.isAi} game.playedTurns: ${game.playedTurns} ply: $ply"
        )
      )

  private[round] def requestFishnet(game: Game, round: RoundDuct): Funit =
    game.playableByAi ?? {
      if (game.turnCount <= fishnetPlayer.maxTurns) fishnetPlayer(game)
      else fuccess(round ! actorApi.round.ResignAi)
    }

  private val fishnetLag = MoveMetrics(clientLag = Centis(5).some)
  private val botLag     = MoveMetrics(clientLag = Centis(10).some)

  private def applyUci(
      game: Game,
      uci: Uci,
      blur: Boolean,
      metrics: MoveMetrics,
      finalSquare: Boolean = false
  ): Validated[String, ActionResult] =
    // NOTE: Step 5: Apply the UCI in strategy games (which includes updating the clock)
    game.stratGame.applyUci(uci, metrics, finalSquare).map {
      // NOTE:: Step 6: at this point the clock has had the move time applied to it
      // taking into account lag, etc. Double check again. Are we out of time?
      case (nsg, _) if nsg.clock.exists(_.outOfTime(game.turnPlayerIndex, withGrace = false)) =>
        // NOTE: Step 7: After fixing the bug, this is correctly reached, and the game ends.
        Flagged
      case (newStratGame, action) =>
        // NOTE: Step 8: Before fixing the bug, this was incorrectly reached and the game continued
        // TODO: Step 8: There was definitely a bug in the strategy games code that was causing
        //               this branch to be reached. However, I would have expected the game to just
        //               continue with the bot seemingly able to continue without being flagged. I
        //               would not expect the player to be flagged as out of time. So it seems
        //               there is a second bug, and I want to know if you think it's worthwhile for
        //               me to look into it.
        ActionApplied(
          game.update(newStratGame, action, blur),
          action
        )
    }

  private def notifyMove(action: Action, game: Game): Unit = {
    import lila.hub.actorApi.round.{ CorresMoveEvent, MoveEvent, SimulMoveEvent }
    val playerIndex = action.player
    val moveEvent = MoveEvent(
      gameId = game.id,
      fen = Forsyth.exportBoard(game.board.variant.gameLogic, game.board),
      move = action match {
        case m: StratMove           => m.toUci.keys
        case d: StratDrop           => d.toUci.uci
        case p: StratPass           => p.toUci.uci
        case l: StratLift           => l.toUci.uci
        case et: StratEndTurn       => et.toUci.uci
        case r: StratRollDice       => r.toUci.uci
        case u: StratUndo           => u.toUci.uci
        case ss: StratSelectSquares => ss.toUci.uci
      }
    )

    // I checked and the bus doesn't do much if there's no subscriber for a classifier,
    // so we should be good here.
    // also used for targeted TvBroadcast subscription
    Bus.publish(MoveGameEvent(game, moveEvent.fen, moveEvent.move), MoveGameEvent makeChan game.id)

    // publish correspondence moves
    if (game.isCorrespondence && game.nonAi)
      Bus.publish(
        CorresMoveEvent(
          move = moveEvent,
          playerUserId = game.player(playerIndex).userId,
          mobilePushable = game.mobilePushable,
          alarmable = game.alarmable,
          unlimited = game.isUnlimited
        ),
        "moveEventCorres"
      )

    // publish simul moves
    for {
      simulId        <- game.simulId
      opponentUserId <- game.player(!playerIndex).userId
    } Bus.publish(
      SimulMoveEvent(move = moveEvent, simulId = simulId, opponentUserId = opponentUserId),
      "moveEventSimul"
    )
  }

  private def moveFinish(game: Game)(implicit proxy: GameProxy): Fu[Events] =
    game.status match {
      case Status.Mate           => finisher.other(game, _.Mate, game.situation.winner)
      case Status.PerpetualCheck => finisher.other(game, _.PerpetualCheck, game.situation.winner)
      case Status.VariantEnd     => finisher.other(game, _.VariantEnd, game.situation.winner)
      case Status.SingleWin      => finisher.other(game, _.SingleWin, game.situation.winner)
      case Status.GammonWin      => finisher.other(game, _.GammonWin, game.situation.winner)
      case Status.BackgammonWin  => finisher.other(game, _.BackgammonWin, game.situation.winner)
      case Status.Stalemate if !game.variant.stalemateIsDraw =>
        finisher.other(game, _.Stalemate, game.situation.winner)
      case status @ (Status.Stalemate | Status.Draw) => finisher.other(game, _ => status, None)
      case _                                         => fuccess(Nil)
    }
}
