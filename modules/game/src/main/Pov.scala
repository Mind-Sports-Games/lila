package lila.game

import strategygames.{ Player => PlayerIndex }
import lila.user.User

case class Pov(game: Game, playerIndex: PlayerIndex) {

  def player = game player playerIndex

  def playerId = player.id

  def typedPlayerId = Game.PlayerId(player.id)

  def fullId = game fullIdOf playerIndex

  def gameId = game.id

  def opponent = game player !playerIndex

  def unary_! = Pov(game, !playerIndex)

  def flip = Pov(game, !playerIndex)

  def ref = PovRef(game.id, playerIndex)

  def withGame(g: Game)   = copy(game = g)
  def withPlayerIndex(c: PlayerIndex) = copy(playerIndex = c)

  lazy val isMyTurn = game.started && game.playable && game.turnPlayerIndex == playerIndex

  lazy val remainingSeconds: Option[Int] =
    game.clock.map(c => c.remainingTime(playerIndex).roundSeconds).orElse {
      game.playableCorrespondenceClock.map(_.remainingTime(playerIndex).toInt)
    }

  def hasMoved = game playerHasMoved playerIndex

  def moves = game playerMoves playerIndex

  def win = game wonBy playerIndex

  def loss = game lostBy playerIndex

  def forecastable = game.forecastable && game.turnPlayerIndex != playerIndex

  override def toString = ref.toString
}

object Pov {

  def apply(game: Game): List[Pov] = game.players.map { apply(game, _) }

  def naturalOrientation(game: Game) = apply(game, game.naturalOrientation)

  def player(game: Game) = apply(game, game.player)
  def opponent(game: Game) = apply(game, game.opponent(game.player))

  def apply(game: Game, player: Player) = new Pov(game, player.playerIndex)

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
    val aDate = a.game.updatedAt.getSeconds
    val bDate = b.game.updatedAt.getSeconds
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

case class PovRef(gameId: Game.ID, playerIndex: PlayerIndex) {

  def unary_! = PovRef(gameId, !playerIndex)

  override def toString = s"$gameId/${playerIndex.name}"
}

case class PlayerRef(gameId: Game.ID, playerId: String)

object PlayerRef {

  def apply(fullId: String): PlayerRef = PlayerRef(Game takeGameId fullId, Game takePlayerId fullId)
}

case class LightPov(game: LightGame, playerIndex: PlayerIndex) {
  def gameId   = game.id
  def player   = game player playerIndex
  def opponent = game player !playerIndex
  def win      = game wonBy playerIndex
}

object LightPov {

  def apply(game: LightGame, player: Player) = new LightPov(game, player.playerIndex)

  def ofUserId(game: LightGame, userId: User.ID): Option[LightPov] =
    game playerByUserId userId map { apply(game, _) }
}
