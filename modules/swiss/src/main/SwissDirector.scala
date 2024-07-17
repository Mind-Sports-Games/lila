package lila.swiss

import strategygames.{ P2, Player => PlayerIndex, P1, GameLogic, GameFamily }
import strategygames.variant.Variant
import strategygames.format.FEN
import strategygames.draughts.variant.{ Variant => DraughtsVariant }
import org.joda.time.DateTime
import scala.util.chaining._
import scala.util.Random

import lila.db.dsl._
import lila.game.{ Game, Handicaps }
import lila.user.User

final private class SwissDirector(
    colls: SwissColls,
    pairingSystem: PairingSystem,
    gameRepo: lila.game.GameRepo,
    onStart: Game.ID => Unit
)(implicit
    ec: scala.concurrent.ExecutionContext,
    idGenerator: lila.game.IdGenerator
) {
  import BsonHandlers._

  private def availableDrawTables(variant: Variant) =
    variant match {
      case Variant.Draughts(variant) =>
        strategygames.draughts.OpeningTable.fensForVariant(variant).map(FEN.Draughts)
      case _ => List()
    }

  private def randomDrawForVariant(variant: Variant)(): Option[FEN] = {
    val tables: List[FEN] = availableDrawTables(variant)
    if (tables.isEmpty) None
    else tables.lift(Random.nextInt(tables.size))
  }

  // sequenced by SwissApi
  private[swiss] def startRound(from: Swiss): Fu[Option[Swiss]] =
    pairingSystem(from)
      .flatMap { pendings =>
        val pendingPairings = pendings.collect { case Right(p) => p }
        if (pendingPairings.isEmpty) fuccess(none) // terminate
        else {
          val swiss            = from.startRound
          val randomPos        = randomDrawForVariant(swiss.roundVariant) _
          val randomPairingPos = swiss.settings.usePerPairingDrawTables
          val randomRoundPos   = swiss.settings.useDrawTables && !randomPairingPos
          val perSwissPos      = swiss.settings.position
          val perRoundPos      = if (randomRoundPos) randomPos().orElse(perSwissPos) else perSwissPos
          for {
            players <- SwissPlayer.fields { f =>
              colls.player.list[SwissPlayer]($doc(f.swissId -> swiss.id))
            }
            ids <- idGenerator.games(pendingPairings.size)
            pairings = pendingPairings.zip(ids).map {
              case (SwissPairing.Pending(w, b), id) => {
                //weaker player must be p1 in handicap go games
                val wRating = players.filter(_.userId == w).map(_.rating).headOption.getOrElse(1500)
                val bRating = players.filter(_.userId == b).map(_.rating).headOption.getOrElse(1500)
                val p1Id    = if (wRating <= bRating) w else b
                val p2Id    = if (wRating <= bRating) b else w

                SwissPairing(
                  id = id,
                  swissId = swiss.id,
                  round = swiss.round,
                  p1 = if (swiss.settings.handicapped) p1Id else w,
                  p2 = if (swiss.settings.handicapped) p2Id else b,
                  status = Left(SwissPairing.Ongoing),
                  matchStatus = Left(SwissPairing.Ongoing),
                  None,
                  isMatchScore = swiss.settings.isMatchScore,
                  isBestOfX = swiss.settings.isBestOfX,
                  isPlayX = swiss.settings.isPlayX,
                  nbGamesPerRound = swiss.settings.nbGamesPerRound,
                  if (swiss.settings.handicapped)
                    Handicaps.startingFen(
                      swiss.roundVariant.some,
                      if (p1Id == w) wRating else bRating,
                      if (p2Id == w) wRating else bRating
                    )
                  else if (randomPairingPos) randomPos().orElse(perRoundPos)
                  else perRoundPos,
                  swiss.roundVariant.some
                )
              }
            }
            _ <-
              colls.swiss.update
                .one(
                  $id(swiss.id),
                  $unset("nextRoundAt") ++ $set(
                    "round"       -> swiss.round,
                    "nbOngoing"   -> pairings.size,
                    "lastRoundAt" -> DateTime.now
                  )
                )
                .void
            date = DateTime.now
            byes = pendings.collect { case Left(bye) => bye.player }
            _ <- SwissPlayer.fields { f =>
              colls.player.update
                .one($doc(f.userId $in byes, f.swissId -> swiss.id), $addToSet(f.byes -> swiss.round))
                .void
            }
            _ <- colls.pairing.insert.many(pairings).void
            games = pairings.map(makeGame(swiss, SwissPlayer.toMap(players)))
            _ <- lila.common.Future.applySequentially(games) { game =>
              gameRepo.insertDenormalized(game) >>- onStart(game.id)
            }
          } yield swiss.some
        }
      }
      .recover { case PairingSystem.BBPairingException(msg, input) =>
        if (msg contains "The number of rounds is larger than the reported number of rounds.") none
        else {
          logger.warn(s"BBPairing ${from.id} $msg")
          logger.info(s"BBPairing ${from.id} $input")
          from.some
        }
      }
      .monSuccess(_.swiss.startRound)

  private[swiss] def makeGame(swiss: Swiss, players: Map[User.ID, SwissPlayer], rematch: Boolean = false)(
      pairing: SwissPairing
  ): Game =
    Game
      .make(
        stratGame = strategygames.Game(
          swiss.roundVariant.gameLogic,
          variant = Some {
            if (swiss.settings.position.isEmpty) swiss.roundVariant
            else
              Variant
                .byName(swiss.roundVariant.gameLogic, "From Position")
                .getOrElse(Variant.orDefault(swiss.roundVariant.gameLogic, 3))
          },
          fen = pairing.openingFEN
        ) pipe { g =>
          val turns = g.player.fold(0, 1)
          g.copy(
            clock = swiss.clock.toClock.some,
            //Its ok to set all of these to turns - we're just saying we're starting at a non standard
            //place (if 1) and its all normal if 0. We don't necessarily know about how many turns/plies
            //made up the history of a position but it doesnt really matter
            plies = turns,
            turnCount = turns,
            startedAtPly = turns,
            startedAtTurn = turns
          )
        },
        p1Player = makePlayer(
          P1,
          players.get(
            if (
              rematch && pairing.multiMatchGameIds
                .fold(false)(ids => ids.size % 2 == 1) && swiss.roundVariant.gameLogic != GameLogic
                .Backgammon()
            ) pairing.p2
            else pairing.p1
          ) err s"Missing pairing p1 $pairing"
        ),
        p2Player = makePlayer(
          P2,
          players.get(
            if (
              rematch && pairing.multiMatchGameIds
                .fold(false)(ids => ids.size % 2 == 1) && swiss.roundVariant.gameLogic != GameLogic
                .Backgammon()
            ) pairing.p1
            else pairing.p2
          ) err s"Missing pairing p2 $pairing"
        ),
        mode = strategygames.Mode(swiss.settings.rated),
        source = lila.game.Source.Swiss,
        pgnImport = None,
        multiMatch =
          if (rematch)
            s"${pairing.multiMatchGameIds.fold(1)(ids => ids.size + 1)}:${pairing.id}".some // link to first mm game
          else if (swiss.settings.nbGamesPerRound > 1) s"1:${pairing.id}".some
          else none
      )
      .withId(if (rematch) pairing.multiMatchGameIds.fold(pairing.gameId)(l => l.last) else pairing.id)
      .withSwissId(swiss.id.value)
      .start

  private def makePlayer(playerIndex: PlayerIndex, player: SwissPlayer) =
    lila.game.Player.make(playerIndex, player.userId, player.rating, player.provisional)
}
