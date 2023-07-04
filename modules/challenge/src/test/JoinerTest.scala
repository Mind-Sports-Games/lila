package lila.challenge

import strategygames.chess.variant.Variant
import strategygames.format.FEN
import strategygames.{ Clock, GameLogic, Mode }
import org.specs2.mutable._

import lila.game.Game

final class JoinerTest extends Specification {

  /*
  "create empty game" should {
    "started at turn 0" in {
      val challenge = Challenge.make(
        variant = Standard,
        initialFen = None,
        timeControl = Challenge.TimeControl.Clock(Clock.Config(300, 0)),
        mode = Mode.Casual,
        playerIndex = "p1",
        challenger = Challenge.Challenger.Anonymous("secret"),
        destUser = None,
        rematchOf = None
      )
      ChallengeJoiner.createGame(challenge, None, None, None) must beLike { case g: Game =>
        g.chess.startedAtTurn must_== 0
      }
    }
    "started at turn from position" in {
      val position = "r1bqkbnr/ppp2ppp/2npp3/8/8/2NPP3/PPP2PPP/R1BQKBNR w KQkq - 2 4"
      val challenge = Challenge.make(
        variant = FromPosition,
        initialFen = Some(FEN(GameLogic.Chess(), position)),
        timeControl = Challenge.TimeControl.Clock(Clock.Config(300, 0)),
        mode = Mode.Casual,
        playerIndex = "p1",
        challenger = Challenge.Challenger.Anonymous("secret"),
        destUser = None,
        rematchOf = None
      )
      ChallengeJoiner.createGame(challenge, None, None, None) must beLike { case g: Game =>
        g.chess.startedAtTurn must_== 6
      }
    }
  }
  */
}
