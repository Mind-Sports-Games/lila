package lila.tournament

import strategygames.{ P2, Player => PlayerIndex, P1, Game => StratGame, GameLogic }
import strategygames.variant.Variant
import scala.util.chaining._

import lila.game.{ Game, Player => GamePlayer, GameRepo, Source }
import lila.user.User

final class AutoPairing(
    gameRepo: GameRepo,
    duelStore: DuelStore,
    lightUserApi: lila.user.LightUserApi,
    onStart: Game.ID => Unit
)(implicit ec: scala.concurrent.ExecutionContext) {

  def apply(
      tour: Tournament,
      variant: Variant,
      pairing: Pairing,
      playersMap: Map[User.ID, Player],
      ranking: Ranking
  ): Fu[Game] = {
    val player1 = playersMap get pairing.user1 err s"Missing pairing player1 $pairing"
    val player2 = playersMap get pairing.user2 err s"Missing pairing player2 $pairing"
    val clock   = tour.clock.toClock
    val game = Game
      .make(
        stratGame = StratGame(
          variant.gameLogic,
          Some {
            if (tour.position.isEmpty) variant
            else
              Variant
                .byName(variant.gameLogic, "From Position")
                .getOrElse(Variant.orDefault(variant.gameLogic, 3))
          },
          tour.position
        ) pipe { g =>
          val turns = g.player.fold(0, 1)
          g.copy(
            clock = clock.some,
            plies = turns,
            turnCount = turns,
            startedAtPly = turns,
            startedAtTurn = turns
          )
        },
        p1Player = makePlayer(P1, player1),
        p2Player = makePlayer(P2, player2),
        mode = tour.mode,
        source = Source.Tournament,
        pgnImport = None
      )
      .withId(pairing.gameId)
      .withTournamentId(tour.id)
      .start
    (gameRepo insertDenormalized game) >>- {
      onStart(game.id)
      duelStore.add(
        tour = tour,
        game = game,
        p1 = usernameOf(pairing.user1) -> ~game.p1Player.rating,
        p2 = usernameOf(pairing.user2) -> ~game.p2Player.rating,
        ranking = ranking
      )
    } inject game
  }

  private def makePlayer(playerIndex: PlayerIndex, player: Player) =
    GamePlayer.make(playerIndex, player.userId, player.rating, player.provisional)

  private def usernameOf(userId: User.ID) =
    lightUserApi.sync(userId).fold(userId)(_.name)
}
