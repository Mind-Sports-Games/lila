package lila.challenge

import strategygames.variant.Variant
import strategygames.format.FEN
import strategygames.{ Clock, GameLogic, Mode }
import strategygames.chess.variant.{ FromPosition, Standard }

class JoinerTest extends munit.FunSuite {

  val timeControl = Challenge.TimeControl.Clock(Clock.Config(300, 0))

  test("create empty game - started at turn 0") {
    val challenge = Challenge.make(
      variant = Variant.Chess(Standard),
      fenVariant = None,
      initialFen = None,
      timeControl = timeControl,
      mode = Mode.Casual,
      playerIndex = "p1",
      challenger = Challenge.Challenger.Anonymous("secret"),
      destUser = None,
      rematchOf = None
    )
    val g = ChallengeJoiner.createGame(challenge, None, None, None)
    assertEquals(g.stratGame.startedAtTurn, 0)
  }

  test("create from position game - started at turn from position") {
    val position  = "r1bqkbnr/ppp2ppp/2npp3/8/8/2NPP3/PPP2PPP/R1BQKBNR w KQkq - 2 4"
    val challenge = Challenge.make(
      variant = Variant.Chess(FromPosition),
      fenVariant = None,
      initialFen = Some(FEN(GameLogic.Chess(), position)),
      timeControl = timeControl,
      mode = Mode.Casual,
      playerIndex = "p1",
      challenger = Challenge.Challenger.Anonymous("secret"),
      destUser = None,
      rematchOf = None
    )
    val g = ChallengeJoiner.createGame(challenge, None, None, None)
    assertEquals(g.stratGame.startedAtTurn, 6)
  }

  test("create go from position game - have non-standard initial fen") {
    val position  = "9/9/2S3S2/9/9/9/9/9/9[SSSSSSSSSSssssssssss] w - 81 4 4 1"
    val challenge = Challenge.make(
      variant = Variant.Go(strategygames.go.variant.Go9x9),
      fenVariant = Some(Variant.Go(strategygames.go.variant.Go9x9)),
      initialFen = Some(FEN(GameLogic.Go(), position)),
      timeControl = timeControl,
      mode = Mode.Casual,
      playerIndex = "p2",
      challenger = Challenge.Challenger.Anonymous("secret"),
      destUser = None,
      rematchOf = None,
      name = None,
      multiMatch = false
    )
    val g = ChallengeJoiner.createGame(challenge, None, None, Some(challenge.finalPlayerIndex))
    assertEquals(g.stratGame.startedAtTurn, 1)
    assertEquals(g.stratGame.situation.dropsAsDrops.size, 79)
  }
}
