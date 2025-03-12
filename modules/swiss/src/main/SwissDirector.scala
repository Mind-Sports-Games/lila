package lila.swiss

import strategygames.{ P2, Player => PlayerIndex, P1, GameLogic, GameFamily, Centis, Status }
import strategygames.variant.Variant
import strategygames.format.FEN
import org.joda.time.DateTime
import scala.util.chaining._
import scala.util.Random

import lila.db.dsl._
import lila.game.{ Game, Handicaps, MultiPointState }
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

  private def playersAndStartingFen(
      swiss: Swiss,
      players: List[SwissPlayer],
      w: User.ID,
      b: User.ID
  ): (User.ID, User.ID, Option[FEN]) = {
    //weaker player must be p1 in handicap go games
    val wRating = players.filter(_.userId == w).map(_.actualRating).headOption.getOrElse(1500)
    val bRating = players.filter(_.userId == b).map(_.actualRating).headOption.getOrElse(1500)
    val p1Id    = if (wRating <= bRating) w else b
    val p2Id    = if (wRating <= bRating) b else w

    //handle mcmahon handicaped games
    val wPoints: Float     = players.filter(_.userId == w).map(_.points.value).headOption.getOrElse(0)
    val bPoints: Float     = players.filter(_.userId == b).map(_.points.value).headOption.getOrElse(0)
    val scoreDiff          = Math.abs(wPoints - bPoints).toInt
    val mcMahonHandicapped = swiss.settings.mcmahon && scoreDiff >= 2
    val mmp1Id             = if (wPoints <= bPoints) w else b
    val mmp2Id             = if (wPoints <= bPoints) b else w

    if (swiss.settings.handicapped) {
      (
        p1Id,
        p2Id,
        Handicaps.startingFen(
          swiss.roundVariant.some,
          if (p1Id == w) wRating else bRating,
          if (p2Id == w) wRating else bRating
        )
      )
    } else if (mcMahonHandicapped) {
      (mmp1Id, mmp2Id, Handicaps.startingFenMcMahon(swiss.roundVariant.some, scoreDiff))
    } else if (
      swiss.settings.backgammonPoints.getOrElse(1) > 1 && swiss.variant.gameFamily == GameFamily.Backgammon()
    ) {
      (w, b, Some(FEN(swiss.variant.gameLogic, swiss.variant.toBackgammon.fenFromSetupConfig(true).value)))
    } else {
      (w, b, None)
    }
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
                val p1p2sf = playersAndStartingFen(swiss, players, w, b)

                SwissPairing(
                  id = id,
                  swissId = swiss.id,
                  round = swiss.round,
                  p1 = p1p2sf._1,
                  p2 = p1p2sf._2,
                  bbpPairingP1 = w,
                  status = Left(SwissPairing.Ongoing),
                  matchStatus = Left(SwissPairing.Ongoing),
                  None,
                  None,
                  isMatchScore = swiss.settings.isMatchScore,
                  isBestOfX = swiss.settings.isBestOfX,
                  isPlayX = swiss.settings.isPlayX,
                  nbGamesPerRound = swiss.settings.nbGamesPerRound,
                  if (p1p2sf._3 != None) p1p2sf._3
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

  private def makeClock(swiss: Swiss, prevGame: Option[Game]) =
    prevGame.fold(swiss.clock.toClock.some)(pg =>
      if (pg.metadata.multiPointState.nonEmpty)
        pg.clock.fold(swiss.clock.toClock.some)(pgc => {
          swiss.clock.toClock
            .giveTime(P1, -pgc.clockPlayer(P1).elapsed)
            .giveTime(P2, -pgc.clockPlayer(P2).elapsed)
            .giveTime(
              pg.situation.player,
              if (Status.resigned.contains(pg.status)) Centis.ofSeconds(swiss.clock.graceSeconds)
              else Centis(0)
            )
            .some
        })
      else swiss.clock.toClock.some
    )

  private[swiss] def makeGame(
      swiss: Swiss,
      players: Map[User.ID, SwissPlayer],
      prevGame: Option[Game] = None
  )(
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
          fen = prevGame.fold(pairing.openingFEN)(pairing.fenForNextGame)
        ) pipe { g =>
          val turns = g.player.fold(0, 1)
          g.copy(
            clock = makeClock(swiss, prevGame),
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
              prevGame.nonEmpty && pairing.multiMatchGameIds
                .fold(false)(ids => ids.size % 2 == 1) && swiss.roundVariant.gameLogic != GameLogic
                .Backgammon() && !swiss.settings.handicapped
            ) pairing.p2
            else pairing.p1
          ) err s"Missing pairing p1 $pairing"
        ),
        p2Player = makePlayer(
          P2,
          players.get(
            if (
              prevGame.nonEmpty && pairing.multiMatchGameIds
                .fold(false)(ids => ids.size % 2 == 1) && swiss.roundVariant.gameLogic != GameLogic
                .Backgammon() && !swiss.settings.handicapped
            ) pairing.p1
            else pairing.p2
          ) err s"Missing pairing p2 $pairing"
        ),
        mode = strategygames.Mode(swiss.settings.rated),
        source = lila.game.Source.Swiss,
        pgnImport = None,
        multiMatch =
          if (prevGame.nonEmpty)
            s"${pairing.multiMatchGameIds.fold(1)(ids => ids.size + 1)}:${pairing.id}".some // link to first mm game
          else if (swiss.settings.nbGamesPerRound > 1 || swiss.settings.backgammonPoints.getOrElse(1) > 1)
            s"1:${pairing.id}".some
          else none
      )
      .withId(
        if (prevGame.nonEmpty) pairing.multiMatchGameIds.fold(pairing.gameId)(l => l.last) else pairing.id
      )
      .withSwissId(swiss.id.value)
      .withHandicappedTournament(
        swiss.settings.handicapped || (swiss.settings.mcmahon && pairing.openingFEN.nonEmpty)
      )
      .withMultiPointState(
        if (prevGame.isEmpty) MultiPointState(swiss.settings.backgammonPoints)
        else
          prevGame.flatMap { g =>
            g.metadata.multiPointState.flatMap(
              _.updateMultiPointState(
                g.pointValue,
                g.winnerPlayerIndex
              )
            )
          }
      )
      .start

  private def makePlayer(playerIndex: PlayerIndex, player: SwissPlayer) =
    lila.game.Player.make(
      playerIndex,
      player.userId,
      player.actualRating,
      player.inputRating.fold(player.provisional)(_ => false),
      player.inputRating.fold(false)(_ => true)
    )
}
