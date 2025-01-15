package lila.challenge

import Challenge.TimeControl
import lila.game.{ Game, Pov }
import lila.user.User

import strategygames.GameLogic
import strategygames.variant.Variant
import strategygames.format.FEN

final class ChallengeMaker(
    userRepo: lila.user.UserRepo,
    gameRepo: lila.game.GameRepo
)(implicit ec: scala.concurrent.ExecutionContext) {

  def makeRematchFor(gameId: Game.ID, dest: User): Fu[Option[Challenge]] =
    gameRepo game gameId flatMap {
      _ ?? { game =>
        game.opponentByUserId(dest.id).flatMap(_.userId) ?? userRepo.byId flatMap {
          _ ?? { challenger =>
            Pov(game, challenger) ?? { pov =>
              makeRematch(pov, challenger, dest) dmap some
            }
          }
        }
      }
    }

  def makeRematchOf(game: Game, challenger: User): Fu[Option[Challenge]] =
    Pov.ofUserId(game, challenger.id) ?? { pov =>
      pov.opponent.userId ?? userRepo.byId flatMap {
        _ ?? { dest =>
          makeRematch(pov, challenger, dest) dmap some
        }
      }
    }

  //when rematching we want the same fen unless we are backgammon and the players
  //aren't flipping colour, but we want the start player to be randomized again
  //and the cube data to be retained. Will want to reconsider this approach when
  //enabling fromPosition for Backgammon
  private def generateRematchFen(variant: Variant, initialFen: Option[FEN]): Option[FEN] =
    variant match {
      case Variant.Backgammon(v) =>
        Some(
          FEN.Backgammon(
            v.fenFromSetupConfig(
              initialFen.exists(f =>
                f match {
                  case FEN.Backgammon(f) => f.cubeData.nonEmpty
                  case _                 => false
                }
              )
            )
          )
        )
      case _ => initialFen
    }

  // pov of the challenger
  private def makeRematch(pov: Pov, challenger: User, dest: User): Fu[Challenge] =
    gameRepo initialFen pov.game map { initialFen =>
      val timeControl = (pov.game.clock, pov.game.daysPerTurn) match {
        case (Some(clock), _) => TimeControl.Clock(clock.config)
        case (_, Some(days))  => TimeControl.Correspondence(days)
        case _                => TimeControl.Unlimited
      }
      val playerIndexName =
        if (pov.game.variant.gameLogic == GameLogic.Backgammon()) {
          pov.playerIndex.name
        } else (!pov.playerIndex).name
      Challenge.make(
        variant = pov.game.variant,
        fenVariant = pov.game.variant.some,
        initialFen = generateRematchFen(pov.game.variant, initialFen),
        timeControl = timeControl,
        mode = pov.game.mode,
        playerIndex = playerIndexName,
        challenger = Challenge.toRegistered(pov.game.variant, timeControl)(challenger),
        destUser = dest.some,
        rematchOf = pov.gameId.some,
        multiMatch = pov.game.metadata.multiMatchGameNr.fold(false)(x => x >= 2),
        backgammonPoints = pov.game.metadata.multiPointState.map(_.target)
      )
    }
}
