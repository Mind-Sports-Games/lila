package lila.lobby

import lila.game.{ Pov, Source }

final private class AbortListener(
    userRepo: lila.user.UserRepo,
    seekApi: SeekApi,
    lobbyTrouper: LobbyTrouper
)(implicit ec: scala.concurrent.ExecutionContext) {

  def apply(pov: Pov): Funit =
    (pov.game.isCorrespondence so recreateSeek(pov)).andDo(cancelPlayerIndexIncrement(pov)).andDo(lobbyTrouper.registerAbortedGame(pov.game))

  private def cancelPlayerIndexIncrement(pov: Pov): Unit =
    if (pov.game.source.exists(s => s == Source.Lobby || s == Source.Pool)) pov.game.userIds match {
      case List(u1, u2) =>
        userRepo.incPlayerIndex(u1, -1)
        userRepo.incPlayerIndex(u2, 1)
      case _ =>
    }

  private def recreateSeek(pov: Pov): Funit =
    pov.player.userId so { aborterId =>
      seekApi.findArchived(pov.gameId) flatMap {
        _ so { seek =>
          (seek.user.id != aborterId) so {
            worthRecreating(seek) flatMap {
              _ so seekApi.insert(Seek renew seek)
            }
          }
        }
      }
    }

  private def worthRecreating(seek: Seek): Fu[Boolean] =
    userRepo byId seek.user.id map {
      _ exists { u =>
        u.enabled && !u.lame
      }
    }
}
