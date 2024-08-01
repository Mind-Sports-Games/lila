package lila.game

import lila.common.LightUser

object Namer {

  def playerTextBlocking(player: Player, withRating: Boolean = false)(implicit
      lightUser: LightUser.GetterSync
  ): String =
    playerTextUser(player, player.userId flatMap lightUser, withRating)

  def playerText(player: Player, withRating: Boolean = false)(implicit
      lightUser: LightUser.Getter
  ): Fu[String] =
    player.userId.??(lightUser) dmap {
      playerTextUser(player, _, withRating)
    }

  private def playerTextUser(player: Player, user: Option[LightUser], withRating: Boolean = false): String =
    player.aiLevel.fold(
      user.fold(player.name | "Anon.") { u =>
        player.rating.ifTrue(withRating).fold(u.titleName) { r =>
          s"${u.titleName} ($r)"
        }
      }
    ) { level =>
      s"Stockfish level $level"
    }

  def gameVsTextBlocking(game: Game, withRatings: Boolean = false)(implicit
      lightUser: LightUser.GetterSync
  ): String =
    s"${playerTextBlocking(game.p1Player, withRatings)} - ${playerTextBlocking(game.p2Player, withRatings)}"

  def gameVsText(game: Game, withRatings: Boolean = false)(implicit lightUser: LightUser.Getter): Fu[String] =
    game.p1Player.userId.??(lightUser) zip
      game.p2Player.userId.??(lightUser) dmap { case (wu, bu) =>
        s"${playerTextUser(game.p1Player, wu, withRatings)} - ${playerTextUser(game.p2Player, bu, withRatings)}"
      }

  def ratingString(p: Player) =
    p.rating match {
      case Some(rating) => s"$rating${if (p.isInputRating) "*" else if (p.provisional) "?" else ""}"
      case _            => "?"
    }
}
