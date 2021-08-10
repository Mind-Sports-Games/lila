package lila.challenge

import strategygames.{ Black, Color, GameLib, Mode, Situation, White }
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

  def apply(c: Challenge, destUser: Option[User], color: Option[Color]): Fu[Option[Pov]] =
    gameRepo exists c.id flatMap {
      case true                                                           => fuccess(None)
      case _ if color.map(Challenge.ColorChoice.apply).has(c.colorChoice) => fuccess(None)
      case _ =>
        c.challengerUserId.??(userRepo.byId) flatMap { origUser =>
          val game = ChallengeJoiner.createGame(c, origUser, destUser, color)
          (gameRepo insertDenormalized game) >>- onStart(game.id) inject Pov(game, !c.finalColor).some
        }
    }
}

private object ChallengeJoiner {

  def createGame(
      c: Challenge,
      origUser: Option[User],
      destUser: Option[User],
      color: Option[Color]
  ): Game = {
    def makeChess(variant: Variant): strategygames.Game =
      strategygames.Game(variant.gameLib, situation = Situation(variant.gameLib, variant), clock = c.clock.map(_.config.toClock))

    val baseState = c.initialFen.ifTrue(c.variant.fromPosition || c.variant.chess960) flatMap {
      Forsyth.<<<@(c.variant.gameLib, c.variant, _)
    }
    val (chessGame, state) = baseState.fold(makeChess(c.variant) -> none[SituationPlus]) {
      case sp @ SituationPlus(sit, _) =>
        val game = strategygames.Game(
          lib = c.variant.gameLib,
          situation = sit,
          turns = sp.turns,
          startedAtTurn = sp.turns,
          clock = c.clock.map(_.config.toClock)
        )
        if (c.variant.fromPosition && Forsyth.>>(c.variant.gameLib, game).initial)
          makeChess(Variant.libStandard(c.variant.gameLib)) -> none
        else game                           -> baseState
    }
    //TODO: microMatch consider customStartingPosition
    //val microMatch = c.isMicroMatch && c.customStartingPosition option "micromatch"
    val microMatch = c.isMicroMatch option "micromatch"
    val perfPicker = (perfs: lila.user.Perfs) => perfs(c.perfType)
    Game
      .make(
        chess = chessGame,
        whitePlayer = Player.make(White, c.finalColor.fold(origUser, destUser), perfPicker),
        blackPlayer = Player.make(Black, c.finalColor.fold(destUser, origUser), perfPicker),
        mode = if (chessGame.board.variant.fromPosition) Mode.Casual else c.mode,
        source = Source.Friend,
        daysPerTurn = c.daysPerTurn,
        pgnImport = None,
        microMatch = microMatch
      )
      .withId(c.id)
      .pipe { g =>
        state.fold(g) { case sit @ SituationPlus(s, _) =>
          g.copy(
            chess = g.chess.copy(
              situation = g.situation.copy(
                board = g.board.copy(history = s.board.history)
              ),
              turns = sit.turns
            )
          )
        }
      }
      .start
  }
}
