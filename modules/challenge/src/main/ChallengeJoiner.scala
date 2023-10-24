package lila.challenge

import strategygames.{ P2, Player => PlayerIndex, GameLogic, GameFamily, Mode, Situation, P1 }
import strategygames.format.Forsyth
import strategygames.format.Forsyth.SituationPlus
import strategygames.variant.Variant
import scala.util.chaining._

import lila.game.{ Game, Player, Pov, Source }
import lila.user.User

final private class ChallengeJoiner(
    gameRepo: lila.game.GameRepo,
    userRepo: lila.user.UserRepo,
    onStart: lila.round.OnStart
)(implicit ec: scala.concurrent.ExecutionContext) {

  def apply(c: Challenge, destUser: Option[User], playerIndex: Option[PlayerIndex]): Fu[Option[Pov]] =
    gameRepo exists c.id flatMap {
      case true                                                                             => fuccess(None)
      case _ if playerIndex.map(Challenge.PlayerIndexChoice.apply).has(c.playerIndexChoice) => fuccess(None)
      case _ =>
        c.challengerUserId.??(userRepo.byId) flatMap { origUser =>
          val game = ChallengeJoiner.createGame(c, origUser, destUser, playerIndex)
          (gameRepo.insertDenormalized(game, c.initialFen)) >>- onStart(game.id) inject Pov(
            game,
            !c.finalPlayerIndex
          ).some
        }
    }
}

private object ChallengeJoiner {

  def createGame(
      c: Challenge,
      origUser: Option[User],
      destUser: Option[User],
      playerIndex: Option[PlayerIndex]
  ): Game = {
    def makeChess(variant: Variant): strategygames.Game =
      strategygames.Game(
        variant.gameLogic,
        situation = Situation(variant.gameLogic, variant),
        clock = c.clock.map(_.config.toClock)
      )

    val baseState = c.initialFen
      .ifTrue(
        c.variant.fromPosition || c.variant.chess960 || c.variant.gameFamily == GameFamily.Go()
      ) flatMap {
      Forsyth.<<<@(c.variant.gameLogic, c.variant, _)
    }
    val (stratGame, state) = baseState.fold(makeChess(c.variant) -> none[SituationPlus]) {
      case sp @ SituationPlus(sit, _) =>
        val game = strategygames.Game(
          lib = c.variant.gameLogic,
          situation = sit,
          plies = sp.plies,
          turnCount = sp.turnCount,
          startedAtPlies = sp.plies,
          startedAtTurn = sp.turnCount,
          clock = c.clock.map(_.config.toClock)
        )
        if (c.variant.fromPosition && Forsyth.>>(c.variant.gameLogic, game).initial)
          makeChess(Variant.libStandard(c.variant.gameLogic)) -> none
        else game                                             -> baseState
    }
    val pieces     = chessGame.situation.board.pieces
    val multiMatch = c.isMultiMatch && c.customStartingPosition option "multiMatch"
    val perfPicker = (perfs: lila.user.Perfs) => perfs(c.perfType)
    Game
      .make(
        stratGame = stratGame,
        p1Player = Player.make(P1, c.finalPlayerIndex.fold(origUser, destUser), perfPicker),
        p2Player = Player.make(P2, c.finalPlayerIndex.fold(destUser, origUser), perfPicker),
        mode = if (stratGame.board.variant.fromPosition) Mode.Casual else c.mode,
        source = Source.Friend,
        daysPerTurn = c.daysPerTurn,
        pgnImport = None,
        multiMatch = multiMatch
      )
      .withId(c.id)
      .pipe { g =>
        state.fold(g) { case sp @ SituationPlus(sit, _) =>
          g.copy(
            stratGame = g.stratGame.copy(
              situation = g.situation.copy(
                board = g.board.copy(history = sit.board.history)
              ),
              plies = sp.plies,
              turnCount = sp.turnCount
            )
          )
        }
      }
      .start
  }
}
