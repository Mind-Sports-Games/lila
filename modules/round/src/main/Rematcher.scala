package lila.round

import strategygames.format.Forsyth
import strategygames.format.FEN
import strategygames.chess.variant._
import strategygames.variant.Variant
import strategygames.{
  P2,
  GameFamily,
  GameLogic,
  Player => PlayerIndex,
  Game => ChessGame,
  Board,
  Situation,
  History,
  P1,
  Mode,
  Piece,
  PieceMap,
  Pos
}
import strategygames.chess.Castles
import com.github.blemale.scaffeine.Cache
import lila.memo.CacheApi
import scala.concurrent.duration._

import lila.common.Bus
import lila.game.{ AnonCookie, Event, Game, GameRepo, PerfPicker, Pov, Rematches, Source }
import lila.memo.ExpireSetMemo
import lila.user.{ User, UserRepo }
import lila.i18n.{ I18nKeys => trans, defaultLang }

final private class Rematcher(
    gameRepo: GameRepo,
    userRepo: UserRepo,
    idGenerator: lila.game.IdGenerator,
    messenger: Messenger,
    onStart: OnStart,
    rematches: Rematches
)(implicit ec: scala.concurrent.ExecutionContext) {

  implicit private val chatLang = defaultLang

  private val declined = new lila.memo.ExpireSetMemo(1 minute)

  private val rateLimit = new lila.memo.RateLimit[String](
    credits = 2,
    duration = 1 minute,
    key = "round.rematch",
    log = false
  )

  import Rematcher.Offers

  private val offers: Cache[Game.ID, Offers] = CacheApi.scaffeineNoScheduler
    .expireAfterWrite(20 minutes)
    .build[Game.ID, Offers]()

  private val chess960 = new ExpireSetMemo(3 hours)

  def isOffering(pov: Pov): Boolean = offers.getIfPresent(pov.gameId).exists(_(pov.playerIndex))

  def yes(pov: Pov): Fu[Events] =
    pov match {
      case Pov(game, playerIndex) if game.playerCouldRematch =>
        if (isOffering(!pov) || game.opponent(playerIndex).isAi)
          rematches.of(game.id).fold(rematchJoin(pov.game))(rematchExists(pov))
        else if (!declined.get(pov.flip.fullId) && rateLimit(pov.fullId)(true)(false))
          fuccess(rematchCreate(pov))
        else fuccess(List(Event.RematchOffer(by = none)))
      case _ => fuccess(List(Event.ReloadOwner))
    }

  def no(pov: Pov): Fu[Events] = {
    if (isOffering(pov)) messenger.system(pov.game, trans.rematchOfferCanceled.txt())
    else if (isOffering(!pov)) {
      declined put pov.fullId
      messenger.system(pov.game, trans.rematchOfferDeclined.txt())
    }
    offers invalidate pov.game.id
    fuccess(List(Event.RematchOffer(by = none)))
  }

  def multiMatch(game: Game): Fu[Events] = rematchJoin(game)

  private def rematchExists(pov: Pov)(nextId: Game.ID): Fu[Events] =
    gameRepo game nextId flatMap {
      _.fold(rematchJoin(pov.game))(g => fuccess(redirectEvents(g)))
    }

  private def rematchJoin(game: Game): Fu[Events] =
    rematches.of(game.id) match {
      case None =>
        for {
          nextGame <- returnGame(game) map (_.start)
          _ = offers invalidate game.id
          _ = rematches.cache.put(game.id, nextGame.id)
          _ = if (game.variant == Variant.Chess(Chess960) && !chess960.get(game.id)) chess960.put(nextGame.id)
          initialFen =
            if (game.variant.gameFamily == GameFamily.Go())
              Some(FEN.Go(nextGame.board.toGo.apiPosition.initialFen))
            else None
          _ <- gameRepo.insertDenormalized(nextGame, initialFen)
        } yield {
          if (nextGame.metadata.multiMatchGameNr.fold(false)(x => x >= 2))
            messenger.system(game, trans.multiMatchRematchStarted.txt())
          else messenger.system(game, trans.rematchOfferAccepted.txt())
          onStart(nextGame.id)
          redirectEvents(nextGame)
        }
      case Some(rematchId) => gameRepo game rematchId map { _ ?? redirectEvents }
    }

  private def rematchCreate(pov: Pov): Events = {
    messenger.system(pov.game, trans.rematchOfferSent.txt())
    pov.opponent.userId foreach { forId =>
      Bus.publish(lila.hub.actorApi.round.RematchOffer(pov.gameId), s"rematchFor:$forId")
    }
    offers.put(pov.gameId, Offers(p1 = pov.playerIndex.p1, p2 = pov.playerIndex.p2))
    List(Event.RematchOffer(by = pov.playerIndex.some))
  }

  //<game number>:<first game id in set>
  private def multiMatchEntry(g: Game): Option[String] =
    if (!g.aborted) {
      g.metadata.multiMatch.fold(g.metadata.multiMatch.isDefined option "multiMatch") { s =>
        if (s.contains("multiMatch")) {
          s"2:${g.id}".some
        } else if (s.substring(1, 2) == ":") {
          s"${s.take(1).toInt + 1}:${s.drop(2)}".some
        } else "multiMatch".some
      }
    } else g.metadata.multiMatch.isDefined option "multiMatch"

  private def returnGame(game: Game): Fu[Game] = {
    for {
      initialFen <- gameRepo.initialFen(game)
      situation = initialFen.flatMap { fen => Forsyth.<<<(game.variant.gameLogic, fen) }
      pieces: PieceMap = game.variant match {
        case Variant.Chess(Chess960) =>
          if (chess960 get game.id) Piece.pieceMapForChess(Chess960.pieces)
          else
            situation.fold(
              Piece.pieceMapForChess(Chess960.pieces)
            )(_.situation.board.pieces)
        case Variant.Chess(FromPosition) =>
          situation.fold(
            Variant.libStandard(game.variant.gameLogic).pieces
          )(_.situation.board.pieces)
        case variant => variant.pieces
      }
      users <- userRepo byIds game.userIds
      //Support go from position, i.e. handicapped start pos.
      board = (game.variant.gameLogic, game.variant, situation.map(_.situation)) match {
        case (GameLogic.Go(), Variant.Go(variant), Some(strategygames.Situation.Go(sit))) =>
          Board.Go(
            strategygames.go
              .Board(sit.board.pieces, variant)
              .withPosition(sit.board.position)
          )
        case _ =>
          Board(game.variant.gameLogic, pieces, variant = game.variant).withHistory(
            History(
              game.variant.gameLogic,
              lastMove = situation.flatMap(_.situation.board.history.lastMove),
              castles = situation.fold(Castles.init)(_.situation.board.history.castles)
            )
          )
      }
      game <- Game.make(
        chess = ChessGame(
          game.variant.gameLogic,
          situation = Situation(
            game.variant.gameLogic,
            board = board,
            player = situation.fold[PlayerIndex](P1)(_.situation.player)
          ),
          clock = game.clock map { c =>
            c.config.toClock
          },
          turns = situation ?? (_.turns),
          startedAtTurn = situation ?? (_.turns)
        ),
        p1Player = returnPlayer(game, P1, users),
        p2Player = returnPlayer(game, P2, users),
        mode = if (users.exists(_.lame)) Mode.Casual else game.mode,
        source = game.source | Source.Lobby,
        daysPerTurn = game.daysPerTurn,
        pgnImport = None,
        multiMatch = multiMatchEntry(game)
      ) withUniqueId idGenerator
    } yield game
  }

  private def returnPlayer(game: Game, playerIndex: PlayerIndex, users: List[User]): lila.game.Player =
    game.opponent(playerIndex).aiLevel match {
      case Some(ai) => lila.game.Player.make(playerIndex, ai.some)
      case None =>
        lila.game.Player.make(
          playerIndex,
          game.opponent(playerIndex).userId.flatMap { id =>
            users.find(_.id == id)
          },
          PerfPicker.mainOrDefault(game)
        )
    }

  private def redirectEvents(game: Game): Events = {
    val p1Id = game fullIdOf P1
    val p2Id = game fullIdOf P2
    List(
      Event.RedirectOwner(P1, p2Id, AnonCookie.json(game pov P2)),
      Event.RedirectOwner(P2, p1Id, AnonCookie.json(game pov P1)),
      // tell spectators about the rematch
      Event.RematchTaken(game.id)
    )
  }
}

private object Rematcher {

  case class Offers(p1: Boolean, p2: Boolean) {
    def apply(playerIndex: PlayerIndex) = playerIndex.fold(p1, p2)
  }
}
