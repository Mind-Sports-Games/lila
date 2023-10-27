package lila.challenge

//import strategygames.chess.variant.Variant
import strategygames.variant.Variant
import strategygames.format.FEN
import strategygames.{ FischerClock, GameLogic, Mode }
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

  "create go from position game" should {
    "have non-standard initial fen" in {
      val position = "9/9/2S3S2/9/9/9/9/9/9[SSSSSSSSSSssssssssss] w - 81 4 4 1"
      val challenge = Challenge.make(
        variant = Variant.Go(strategygames.go.variant.Go9x9),
        fenVariant = Some(Variant.Go(strategygames.go.variant.Go9x9)),
        initialFen = Some(FEN(GameLogic.Go(), position)),
        timeControl = Challenge.TimeControl.Clock(FischerClock.Config(300, 0)),
        mode = Mode.Casual,
        playerIndex = "p2",
        challenger = Challenge.Challenger.Anonymous("secret"),
        destUser = None,
        rematchOf = None,
        name = None,
        multiMatch = false
      )
      ChallengeJoiner.createGame(challenge, None, None, Some(challenge.finalPlayerIndex)) must beLike {
        case g: Game =>
          g.stratGame.startedAtTurn must_== 1
          g.stratGame.situation.dropsAsDrops.size must_== 79
      }
    }
  }

}
