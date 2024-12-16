package lila.lobby

import strategygames.{ Game => StratGame, P1, P2, Situation }

import actorApi.{ JoinHook, JoinSeek }
import lila.game.{ Game, PerfPicker, Player }
import lila.socket.Socket.Sri
import lila.user.User

final private class Biter(
    userRepo: lila.user.UserRepo,
    gameRepo: lila.game.GameRepo
)(implicit
    ec: scala.concurrent.ExecutionContext,
    idGenerator: lila.game.IdGenerator
) {

  def apply(hook: Hook, sri: Sri, user: Option[LobbyUser]): Fu[JoinHook] =
    if (canJoin(hook, user)) join(hook, sri, user)
    else fufail(s"$user cannot bite hook $hook")

  def apply(seek: Seek, user: LobbyUser): Fu[JoinSeek] =
    if (canJoin(seek, user)) join(seek, user)
    else fufail(s"$user cannot join seek $seek")

  private def join(hook: Hook, sri: Sri, lobbyUserOption: Option[LobbyUser]): Fu[JoinHook] =
    for {
      userOption         <- lobbyUserOption.map(_.id) ?? userRepo.byId
      ownerOption        <- hook.userId ?? userRepo.byId
      creatorPlayerIndex <- assignCreatorPlayerIndex(ownerOption, userOption, hook.realPlayerIndex)
      game <- makeGame(
        hook,
        p1User = creatorPlayerIndex.fold(ownerOption, userOption),
        p2User = creatorPlayerIndex.fold(userOption, ownerOption)
      ).withUniqueId
      _ <- gameRepo insertDenormalized game
    } yield {
      lila.mon.lobby.hook.join.increment()
      JoinHook(sri, hook, game, creatorPlayerIndex)
    }

  private def join(seek: Seek, lobbyUser: LobbyUser): Fu[JoinSeek] =
    for {
      user               <- userRepo byId lobbyUser.id orFail s"No such user: ${lobbyUser.id}"
      owner              <- userRepo byId seek.user.id orFail s"No such user: ${seek.user.id}"
      creatorPlayerIndex <- assignCreatorPlayerIndex(owner.some, user.some, seek.realPlayerIndex)
      game <- makeGame(
        seek,
        p1User = creatorPlayerIndex.fold(owner.some, user.some),
        p2User = creatorPlayerIndex.fold(user.some, owner.some)
      ).withUniqueId
      _ <- gameRepo insertDenormalized game
    } yield JoinSeek(user.id, seek, game, creatorPlayerIndex)

  private def assignCreatorPlayerIndex(
      creatorUser: Option[User],
      joinerUser: Option[User],
      playerIndex: PlayerIndex
  ): Fu[strategygames.Player] =
    playerIndex match {
      case PlayerIndex.Random =>
        userRepo.firstGetsP1(creatorUser.map(_.id), joinerUser.map(_.id)) map strategygames.Player.fromP1
      case PlayerIndex.P1 => fuccess(strategygames.P1)
      case PlayerIndex.P2 => fuccess(strategygames.P2)
    }

  private def makeGame(hook: Hook, p1User: Option[User], p2User: Option[User]) = {
    val clock      = hook.clock.toClock
    val perfPicker = PerfPicker.mainOrDefault(strategygames.Speed(clock.config), hook.realVariant, none)
    val stratSit   = Situation(hook.realVariant.gameLogic, hook.realVariant)
    Game
      .make(
        stratGame = StratGame(
          lib = hook.realVariant.gameLogic,
          situation = stratSit,
          clock = clock.some,
          //we have to do this to handle Backgammon variable start player
          plies = stratSit.player.fold(0, 1),
          turnCount = stratSit.player.fold(0, 1),
          startedAtPly = stratSit.player.fold(0, 1),
          startedAtTurn = stratSit.player.fold(0, 1)
        ),
        p1Player = Player.make(strategygames.P1, p1User, perfPicker),
        p2Player = Player.make(strategygames.P2, p2User, perfPicker),
        mode = hook.realMode,
        source = lila.game.Source.Lobby,
        pgnImport = None
      )
      .start
  }

  private def makeGame(seek: Seek, p1User: Option[User], p2User: Option[User]) = {
    val perfPicker = PerfPicker.mainOrDefault(strategygames.Speed(none), seek.realVariant, seek.daysPerTurn)
    val stratSit   = Situation(seek.realVariant.gameLogic, seek.realVariant)
    Game
      .make(
        stratGame = StratGame(
          lib = seek.realVariant.gameLogic,
          situation = stratSit,
          clock = none,
          //we have to do this to handle Backgammon variable start player
          plies = stratSit.player.fold(0, 1),
          turnCount = stratSit.player.fold(0, 1),
          startedAtPly = stratSit.player.fold(0, 1),
          startedAtTurn = stratSit.player.fold(0, 1)
        ),
        p1Player = Player.make(strategygames.P1, p1User, perfPicker),
        p2Player = Player.make(strategygames.P2, p2User, perfPicker),
        mode = seek.realMode,
        source = lila.game.Source.Lobby,
        daysPerTurn = seek.daysPerTurn,
        pgnImport = None
      )
      .start
  }

  def canJoin(hook: Hook, user: Option[LobbyUser]): Boolean =
    hook.isAuth == user.isDefined && user.fold(true) { u =>
      u.lame == hook.lame &&
      !hook.userId.contains(u.id) &&
      !hook.userId.??(u.blocking.contains) &&
      !hook.user.??(_.blocking).contains(u.id) &&
      hook.realRatingRange.fold(true) { range =>
        (hook.perfType map u.ratingAt) ?? range.contains
      }
    }

  def canJoin(seek: Seek, user: LobbyUser): Boolean =
    seek.user.id != user.id &&
      (seek.realMode.casual || user.lame == seek.user.lame) &&
      !(user.blocking contains seek.user.id) &&
      !(seek.user.blocking contains user.id) &&
      seek.realRatingRange.fold(true) { range =>
        (seek.perfType map user.ratingAt) ?? range.contains
      }

  def showHookTo(hook: Hook, member: LobbySocket.Member): Boolean =
    hook.sri == member.sri || canJoin(hook, member.user)
}
