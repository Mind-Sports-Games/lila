package lila.game

import strategygames.{ Player => SGPlayer }
import lila.user.User

case class Pov(game: Game, sgPlayer: SGPlayer) {

  def player = game player sgPlayer

  def playerId = player.id

  def typedPlayerId = Game.PlayerId(player.id)

  def fullId = game fullIdOf sgPlayer

  def gameId = game.id

  def opponent = game player !sgPlayer

  def unary_! = Pov(game, !sgPlayer)

  def flip = Pov(game, !sgPlayer)

  def ref = PovRef(game.id, sgPlayer)

  def withGame(g: Game)   = copy(game = g)
  def withSGPlayer(c: SGPlayer) = copy(sgPlayer = c)

  lazy val isMyTurn = game.started && game.playable && game.turnSGPlayer == sgPlayer

  lazy val remainingSeconds: Option[Int] =
    game.clock.map(c => c.remainingTime(sgPlayer).roundSeconds).orElse {
      game.playableCorrespondenceClock.map(_.remainingTime(sgPlayer).toInt)
    }

  def hasMoved = game playerHasMoved sgPlayer

  def moves = game playerMoves sgPlayer

  def win = game wonBy sgPlayer

  def loss = game lostBy sgPlayer

  def forecastable = game.forecastable && game.turnSGPlayer != sgPlayer

  override def toString = ref.toString
}

object Pov {

  def apply(game: Game): List[Pov] = game.players.map { apply(game, _) }

  def naturalOrientation(game: Game) = apply(game, game.naturalOrientation)

  def player(game: Game) = apply(game, game.player)
  def opponent(game: Game) = apply(game, game.opponent(game.player))

  def apply(game: Game, player: Player) = new Pov(game, player.sgPlayer)

  def apply(game: Game, playerId: Player.ID): Option[Pov] =
    game player playerId map { apply(game, _) }

  def apply(game: Game, user: User): Option[Pov] =
    game player user map { apply(game, _) }

  def ofUserId(game: Game, userId: User.ID): Option[Pov] =
    game playerByUserId userId map { apply(game, _) }

  def opponentOfUserId(game: Game, userId: String): Option[Player] =
    ofUserId(game, userId) map (_.opponent)

  private def orInf(i: Option[Int]) = i getOrElse Int.MaxValue
  private def isFresher(a: Pov, b: Pov) = {
    val aDate = a.game.movedAt.getSeconds
    val bDate = b.game.movedAt.getSeconds
    if (aDate == bDate) a.gameId < b.gameId
    else aDate > bDate
  }

  def priority(a: Pov, b: Pov) =
    if (!a.isMyTurn && !b.isMyTurn) isFresher(a, b)
    else if (!a.isMyTurn && b.isMyTurn) false
    else if (a.isMyTurn && !b.isMyTurn) true
    // first move has priority over games with more than 30s left
    else if (!a.hasMoved && orInf(b.remainingSeconds) > 30) true
    else if (!b.hasMoved && orInf(a.remainingSeconds) > 30) false
    else if (orInf(a.remainingSeconds) < orInf(b.remainingSeconds)) true
    else if (orInf(b.remainingSeconds) < orInf(a.remainingSeconds)) false
    else isFresher(a, b)
}

case class PovRef(gameId: Game.ID, sgPlayer: SGPlayer) {

  def unary_! = PovRef(gameId, !sgPlayer)

  override def toString = s"$gameId/${sgPlayer.name}"
}

case class PlayerRef(gameId: Game.ID, playerId: String)

object PlayerRef {

  def apply(fullId: String): PlayerRef = PlayerRef(Game takeGameId fullId, Game takePlayerId fullId)
}

case class LightPov(game: LightGame, sgPlayer: SGPlayer) {
  def gameId   = game.id
  def player   = game player sgPlayer
  def opponent = game player !sgPlayer
  def win      = game wonBy sgPlayer
}

object LightPov {

  def apply(game: LightGame, player: Player) = new LightPov(game, player.sgPlayer)

  def ofUserId(game: LightGame, userId: User.ID): Option[LightPov] =
    game playerByUserId userId map { apply(game, _) }
}
