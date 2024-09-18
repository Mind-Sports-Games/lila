package lila.swiss

import akka.stream.scaladsl._
import org.joda.time.DateTime
import ornicar.scalalib.Zero
import reactivemongo.akkastream.cursorProducer
import reactivemongo.api._
import reactivemongo.api.bson._
import scala.concurrent.duration._
import scala.util.chaining._

import lila.chat.Chat
import lila.common.config.MaxPerSecond
import lila.common.{ Bus, GreatPlayer, LightUser }
import lila.db.dsl._
import lila.game.{ Game, Handicaps, Pov }
import lila.hub.LightTeam.TeamID
import lila.round.actorApi.round.QuietFlag
import lila.user.{ User, UserRepo }
import lila.i18n.VariantKeys

import strategygames.GameLogic

final class SwissApi(
    colls: SwissColls,
    cache: SwissCache,
    userRepo: UserRepo,
    socket: SwissSocket,
    director: SwissDirector,
    scoring: SwissScoring,
    rankingApi: SwissRankingApi,
    standingApi: SwissStandingApi,
    boardApi: SwissBoardApi,
    verify: SwissCondition.Verify,
    chatApi: lila.chat.ChatApi,
    lightUserApi: lila.user.LightUserApi,
    trophyApi: lila.user.TrophyApi,
    roundSocket: lila.round.RoundSocket,
    gameRepo: lila.game.GameRepo,
    onStart: Game.ID => Unit,
    idGenerator: lila.game.IdGenerator
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem,
    mode: play.api.Mode
) {

  private val sequencer =
    new lila.hub.DuctSequencers(
      maxSize = 1024, // queue many game finished events
      expiration = 20 minutes,
      timeout = 10 seconds,
      name = "swiss.api"
    )

  import BsonHandlers._

  def byId(id: Swiss.Id)            = colls.swiss.byId[Swiss](id.value)
  def finishedById(id: Swiss.Id)    = byId(id).dmap(_.filter(_.isFinished))
  def notFinishedById(id: Swiss.Id) = byId(id).dmap(_.filter(_.isNotFinished))
  def createdById(id: Swiss.Id)     = byId(id).dmap(_.filter(_.isCreated))
  def startedById(id: Swiss.Id)     = byId(id).dmap(_.filter(_.isStarted))

  def create(data: SwissForm.SwissData, me: User, teamId: TeamID): Fu[Swiss] = {
    val swiss = Swiss(
      _id = Swiss.makeId,
      name = data.name | GreatPlayer.randomName,
      clock = data.clock,
      variant = data.realVariant,
      round = SwissRound.Number(0),
      nbPlayers = 0,
      nbOngoing = 0,
      createdAt = DateTime.now,
      createdBy = me.id,
      teamId = teamId,
      nextRoundAt = data.realStartsAt.some,
      startsAt = data.realStartsAt,
      finishedAt = none,
      winnerId = none,
      trophy1st = none,
      trophy2nd = none,
      trophy3rd = none,
      settings = Swiss.Settings(
        nbRounds = data.nbRounds,
        rated = data.realPosition.isEmpty && data.isRated,
        mcmahon = data.isMcMahon,
        mcmahonCutoff = ~data.mcmahonCutoff,
        handicapped = data.isHandicapped,
        inputPlayerRatings = ~data.inputPlayerRatings,
        isMatchScore = data.isMatchScore,
        isBestOfX = data.isBestOfX,
        isPlayX = data.isPlayX,
        nbGamesPerRound = data.nbGamesPerRound,
        description = data.description,
        useDrawTables = data.useDrawTables,
        usePerPairingDrawTables = data.usePerPairingDrawTables,
        position = data.realPosition,
        chatFor = data.realChatFor,
        roundInterval = data.realRoundInterval,
        halfwayBreak = data.realHalfwayBreak,
        password = data.password,
        conditions = data.conditions.all,
        forbiddenPairings = ~data.forbiddenPairings,
        medleyVariants = data.medleyVariants,
        minutesBeforeStartToJoin = data.realMinutesBeforeStartToJoin
      )
    )
    colls.swiss.insert.one(addFeaturable(swiss)) >>-
      cache.featuredInTeam.invalidate(swiss.teamId) inject swiss
  }

  def update(swiss: Swiss, data: SwissForm.SwissData): Funit =
    Sequencing(swiss.id)(byId) { old =>
      val position =
        if (old.isCreated || old.settings.position.isDefined)
          data.realVariant.standardVariant ?? data.realPosition
        else old.settings.position
      val swiss =
        old.copy(
          name = data.name | old.name,
          clock = if (old.isCreated) data.clock else old.clock,
          variant = if (old.isCreated && data.variant.isDefined) data.realVariant else old.variant,
          startsAt = data.startsAt.ifTrue(old.isCreated) | old.startsAt,
          nextRoundAt =
            if (old.isCreated) Some(data.startsAt | old.startsAt)
            else old.nextRoundAt,
          settings = old.settings.copy(
            nbRounds = data.nbRounds,
            rated = position.isEmpty && (data.rated | old.settings.rated),
            mcmahon = data.isMcMahon | old.settings.mcmahon,
            mcmahonCutoff =
              if (data.mcmahonCutoff.isDefined) ~data.mcmahonCutoff else old.settings.mcmahonCutoff,
            handicapped = data.isHandicapped | old.settings.handicapped,
            inputPlayerRatings =
              if (data.inputPlayerRatings.isDefined) ~data.inputPlayerRatings
              else old.settings.inputPlayerRatings,
            isMatchScore = data.isMatchScore,
            isBestOfX = data.isBestOfX,
            isPlayX = data.isPlayX,
            nbGamesPerRound = data.nbGamesPerRound,
            description = data.description orElse old.settings.description,
            useDrawTables = data.useDrawTables | old.settings.useDrawTables,
            usePerPairingDrawTables = data.usePerPairingDrawTables | old.settings.usePerPairingDrawTables,
            position = position,
            chatFor = data.chatFor | old.settings.chatFor,
            roundInterval =
              if (data.roundInterval.isDefined) data.realRoundInterval
              else old.settings.roundInterval,
            halfwayBreak =
              if (data.halfwayBreak.isDefined) data.realHalfwayBreak
              else old.settings.halfwayBreak,
            password = data.password,
            conditions = data.conditions.all,
            forbiddenPairings = ~data.forbiddenPairings,
            minutesBeforeStartToJoin = data.realMinutesBeforeStartToJoin,
            medleyVariants =
              if (
                old.medleyGameGroups != Some(
                  data.medleyGameFamilies.ggList.sortWith(_.name < _.name)
                ) || old.settings.nbRounds < data.nbRounds
              )
                data.medleyVariants
              else old.settings.medleyVariants
          )
        ) pipe { s =>
          if (
            s.isStarted && s.nbOngoing == 0 && (s.nextRoundAt.isEmpty || old.settings.manualRounds) && !s.settings.manualRounds
          )
            if (s.isHalfway) {
              s.copy(nextRoundAt =
                DateTime.now
                  .plusSeconds(
                    s.settings.roundInterval.toSeconds.toInt + s.settings.halfwayBreak.toSeconds.toInt
                  )
                  .some
              )
            } else {
              s.copy(nextRoundAt = DateTime.now.plusSeconds(s.settings.roundInterval.toSeconds.toInt).some)
            }
          else if (s.settings.manualRounds && !old.settings.manualRounds)
            s.copy(nextRoundAt = none)
          else s
        }
      if (
        (swiss.settings.handicapped || swiss.settings.mcmahon) && old.settings.inputPlayerRatings != ~data.inputPlayerRatings
      ) {
        val playerRatingMap = Handicaps.playerInputRatings(swiss.settings.inputPlayerRatings)
        playerRatingMap.toList.map { case (u, r) =>
          colls.player
            .updateField(
              $id(SwissPlayer.makeId(swiss.id, u)),
              SwissPlayer.Fields.inputRating,
              r
            )
            .void
        }.sequenceFu >> unsetAllPlayerInputRating( // to reset removed players
          swiss.id,
          playerRatingMap.keys.toList.map(u => SwissPlayer.Id(s"${swiss.id}:${u}"))
        ) >>
          recomputeAndUpdateAll(swiss.id)
      } else if (
        !(swiss.settings.handicapped || swiss.settings.mcmahon) && (old.settings.handicapped || old.settings.mcmahon)
      ) {
        unsetAllPlayerInputRating(swiss.id) >>
          recomputeAndUpdateAll(swiss.id)
      }
      colls.swiss.update.one($id(old.id), addFeaturable(swiss)).void >>- {
        cache.roundInfo.put(swiss.id, fuccess(swiss.roundInfo.some))
        socket.reload(swiss.id)
      }
    } >> {
      if (swiss.settings.mcmahon)
        recomputeAndUpdateAll(swiss.id) //need to update player points preStart
      else funit
    }

  private def unsetAllPlayerInputRating(
      swissId: Swiss.Id,
      retainedPlayerIds: List[SwissPlayer.Id] = List()
  ): Funit = {
    colls.player.list[SwissPlayer]($doc(SwissPlayer.Fields.swissId -> swissId)) map { players =>
      players
        .filter(p => !retainedPlayerIds.contains(p.id))
        .map { p => unsetPlayerInputRating(p.id) }
        .sequenceFu
        .unit
    }
  }

  private def unsetPlayerInputRating(playerId: SwissPlayer.Id): Funit =
    colls.player.update
      .one(
        $id(playerId),
        $unset(SwissPlayer.Fields.inputRating)
      )
      .void

  def scheduleNextRound(swiss: Swiss, date: DateTime): Funit =
    Sequencing(swiss.id)(notFinishedById) { old =>
      old.settings.manualRounds ?? {
        if (old.isCreated) colls.swiss.updateField($id(old.id), "startsAt", date).void
        else if (old.isStarted && old.nbOngoing == 0)
          colls.swiss.updateField($id(old.id), "nextRoundAt", date).void >>- {
            val show = org.joda.time.format.DateTimeFormat.forStyle("MS") print date
            systemChat(swiss.id, s"Round ${swiss.round.value + 1} scheduled at $show UTC")
          }
        else funit
      } >>- socket.reload(swiss.id)
    }

  def verdicts(swiss: Swiss, me: Option[User]): Fu[SwissCondition.All.WithVerdicts] =
    me match {
      case None       => fuccess(swiss.settings.conditions.accepted)
      case Some(user) => verify(swiss, user)
    }

  def join(id: Swiss.Id, me: User, isInTeam: TeamID => Boolean, password: Option[String]): Fu[Boolean] =
    Sequencing(id)(notFinishedById) { swiss =>
      if (swiss.settings.password.exists(_ != ~password)) fuFalse
      else
        colls.player // try a rejoin first
          .updateField($id(SwissPlayer.makeId(swiss.id, me.id)), SwissPlayer.Fields.absent, false)
          .flatMap { rejoin =>
            fuccess(rejoin.n == 1) >>| { // if the match failed (not the update!), try a join
              (swiss.isEnterable && isInTeam(swiss.teamId)) ?? {
                colls.player.insert.one(
                  SwissPlayer.make(
                    swiss.id,
                    me,
                    swiss.roundPerfType,
                    Handicaps.playerInputRatings(swiss.settings.inputPlayerRatings).get(me.username)
                  )
                ) zip
                  colls.swiss.update.one($id(swiss.id), $inc("nbPlayers" -> 1)) inject true
              }
            }
          }
    } flatMap { res =>
      recomputeAndUpdateAll(id) inject res
    }

  def gameIdSource(
      swissId: Swiss.Id,
      batchSize: Int = 0,
      readPreference: ReadPreference = ReadPreference.secondaryPreferred
  ): Source[Game.ID, _] =
    SwissPairing.fields { f =>
      colls.pairing
        .find($doc(f.swissId -> swissId), $id(true).some)
        .sort($sort asc f.round)
        .batchSize(batchSize)
        .cursor[Bdoc](readPreference)
        .documentSource()
        .mapConcat(_.string("_id").toList)
    }

  def featuredInTeam(teamId: TeamID): Fu[List[Swiss]] =
    cache.featuredInTeam.get(teamId) flatMap { ids =>
      colls.swiss.byOrderedIds[Swiss, Swiss.Id](ids)(_.id)
    }

  def visibleByTeam(teamId: TeamID, nbPast: Int, nbSoon: Int): Fu[Swiss.PastAndNext] =
    (nbPast > 0).?? {
      colls.swiss
        .find($doc("teamId" -> teamId, "finishedAt" $exists true))
        .sort($sort desc "startsAt")
        .cursor[Swiss]()
        .list(nbPast)
    } zip
      (nbSoon > 0).?? {
        colls.swiss
          .find($doc("teamId" -> teamId, "finishedAt" $exists false))
          .sort($sort asc "startsAt")
          .cursor[Swiss]()
          .list(nbSoon)
      } map
      (Swiss.PastAndNext.apply _).tupled

  def playerInfo(swiss: Swiss, userId: User.ID): Fu[Option[SwissPlayer.ViewExt]] =
    userRepo named userId flatMap {
      _ ?? { user =>
        colls.player.byId[SwissPlayer](SwissPlayer.makeId(swiss.id, user.id).value) flatMap {
          _ ?? { player =>
            SwissPairing.fields { f =>
              colls.pairing
                .find($doc(f.swissId -> swiss.id, f.players -> player.userId))
                .sort($sort asc f.round)
                .cursor[SwissPairing]()
                .list()
            } flatMap {
              pairingViews(_, player)
            } flatMap { pairings =>
              SwissPlayer.fields { f =>
                colls.player.countSel($doc(f.swissId -> swiss.id, f.score $gt player.score)).dmap(1.+)
              } map { rank =>
                val pairingMap = pairings.view.map { p =>
                  p.pairing.round -> p
                }.toMap
                SwissPlayer
                  .ViewExt(
                    player,
                    rank,
                    user.light,
                    pairingMap,
                    SwissSheet.one(swiss, pairingMap.view.mapValues(_.pairing).toMap, player)
                  )
                  .some
              }
            }
          }
        }
      }
    }

  def pairingViews(pairings: Seq[SwissPairing], player: SwissPlayer): Fu[Seq[SwissPairing.View]] =
    pairings.headOption ?? { first =>
      colls.player
        .list[SwissPlayer]($inIds(pairings.map(_ opponentOf player.userId).map {
          SwissPlayer.makeId(first.swissId, _)
        }))
        .flatMap { opponents =>
          lightUserApi asyncMany opponents.map(_.userId) map { users =>
            opponents.zip(users) map { case (o, u) =>
              SwissPlayer.WithUser(o, u | LightUser.fallback(o.userId))
            }
          } map { opponents =>
            pairings flatMap { pairing =>
              opponents.find(_.player.userId == pairing.opponentOf(player.userId)) map {
                SwissPairing.View(pairing, _)
              }
            }
          }
        }
    }

  def searchPlayers(id: Swiss.Id, term: String, nb: Int): Fu[List[User.ID]] =
    User.validateId(term) ?? { valid =>
      SwissPlayer.fields { f =>
        colls.player.primitive[User.ID](
          selector = $doc(
            f.swissId -> id,
            f.userId $startsWith valid
          ),
          sort = $sort desc f.score,
          nb = nb,
          field = f.userId
        )
      }
    }

  def pageOf(swiss: Swiss, userId: User.ID): Fu[Option[Int]] =
    rankingApi(swiss) map {
      _ get userId map { rank =>
        (rank - 1) / 10 + 1
      }
    }

  def gameView(pov: Pov): Fu[Option[GameView]] =
    (pov.game.swissId.map(Swiss.Id.apply) ?? byId) flatMap {
      _ ?? { swiss =>
        getGameRanks(swiss, pov.game) dmap {
          GameView(swiss, _).some
        }
      }
    }

  private def getGameRanks(swiss: Swiss, game: Game): Fu[Option[GameRanks]] =
    ~ {
      game.p1Player.userId.ifTrue(swiss.isStarted) flatMap { p1Id =>
        game.p2Player.userId map { p2Id =>
          rankingApi(swiss) map { ranking =>
            import cats.implicits._
            (ranking.get(p1Id), ranking.get(p2Id)) mapN { (p1R, p2R) =>
              GameRanks(p1R, p2R)
            }
          }
        }
      }
    }

  private[swiss] def kickFromTeam(teamId: TeamID, userId: User.ID) =
    colls.swiss.secondaryPreferred
      .primitive[Swiss.Id]($doc("teamId" -> teamId, "featurable" -> true), "_id")
      .flatMap { swissIds =>
        colls.player.distinctEasy[Swiss.Id, Seq](
          "s",
          $inIds(swissIds.map { SwissPlayer.makeId(_, userId) }),
          ReadPreference.secondaryPreferred
        )
      }
      .flatMap { kickFromSwissIds(userId, _) }

  private[swiss] def kickLame(userId: User.ID) =
    Bus
      .ask[List[TeamID]]("teamJoinedBy")(lila.hub.actorApi.team.TeamIdsJoinedBy(userId, _))
      .flatMap { teamIds =>
        colls.swiss.aggregateList(100) { framework =>
          import framework._
          Match($doc("teamId" $in teamIds, "featurable" -> true)) -> List(
            PipelineOperator(
              $doc(
                "$lookup" -> $doc(
                  "as"   -> "player",
                  "from" -> colls.player.name,
                  "let"  -> $doc("s" -> "$_id"),
                  "pipeline" -> $arr(
                    $doc(
                      "$match" -> $doc(
                        "$expr" -> $doc(
                          "$and" -> $arr(
                            $doc("$eq" -> $arr("$u", userId)),
                            $doc("$eq" -> $arr("$s", "$$s"))
                          )
                        )
                      )
                    )
                  )
                )
              )
            ),
            Match("player" $ne $arr()),
            Project($id(true))
          )
        }
      }
      .map(_.flatMap(_.getAsOpt[Swiss.Id]("_id")))
      .flatMap { kickFromSwissIds(userId, _) }

  private def kickFromSwissIds(userId: User.ID, swissIds: Seq[Swiss.Id]): Funit =
    swissIds.map { withdraw(_, userId) }.sequenceFu.void

  def withdraw(id: Swiss.Id, userId: User.ID): Funit =
    Sequencing(id)(notFinishedById) { swiss =>
      SwissPlayer.fields { f =>
        val selId = $id(SwissPlayer.makeId(swiss.id, userId))
        if (swiss.isStarted)
          colls.player.updateField(selId, f.absent, true)
        else
          colls.player.delete.one(selId) flatMap { res =>
            (res.n == 1) ?? colls.swiss.update.one($id(swiss.id), $inc("nbPlayers" -> -1)).void
          }
      }.void
    } >> recomputeAndUpdateAll(id)

  def disqualify(id: String, userId: User.ID) =
    Sequencing(Swiss.Id(id))(finishedById) { swiss =>
      SwissPlayer.fields { f =>
        val selId = $id(SwissPlayer.makeId(swiss.id, userId))
        colls.player.updateField(selId, f.disqualified, true)
      }.void >>- {
        getWinner(swiss.id).flatMap { winnerUserId =>
          colls.swiss.update
            .one(
              $id(swiss.id),
              $set("winnerId" -> winnerUserId)
            )
            .void
        }.unit
        trophyApi
          .trophiesByUrl(Swiss.swissUrl(swiss.id))
          .map(_.filter(_.user == userId))
          .flatMap { trophyList =>
            trophyList.headOption ?? { trophy =>
              trophyApi.removeTrophiesByUrl(Swiss.swissUrl(swiss.id)) >>
                awardTrophies(swiss, trophy.date)
            }
          }
          .unit
      } >>
        recomputeAndUpdateAll(swiss.id) >>-
        socket.reload(swiss.id)
    }

  def recomputeScore(id: String): Funit =
    recomputeAndUpdateAll(Swiss.Id(id))

  private[swiss] def toGamesMap(pairs: List[(Game.ID, Option[Game])]): Map[Game.ID, Game] =
    pairs.collect { case (id, Some(g)) =>
      (id, g)
    } toMap

  private[swiss] def getGamesMap(ids: List[Game.ID]): Fu[Map[Game.ID, Game]] =
    roundSocket.getGames(ids) map toGamesMap

  private[swiss] def toSwissPairingGames(
      swissId: Swiss.Id,
      ids: SwissPairingGameIds,
      gamesById: Map[Game.ID, Game]
  ): Option[SwissPairingGames] =
    gamesById
      .get(ids.id)
      .map(g =>
        SwissPairingGames(
          swissId,
          g,
          ids.multiMatchGameIds.fold[Option[List[Game]]](None)(l => Some(l.flatMap(gamesById.get))),
          ids.isMatchScore,
          ids.isBestOfX,
          ids.isPlayX,
          ids.nbGamesPerRound,
          ids.openingFEN
        )
      )

  private[swiss] def toGameIds(ids: List[SwissPairingGameIds]): List[Game.ID] =
    ids.flatMap(g => g.multiMatchGameIds.foldLeft(List(g.id))(_ ++ _))

  private[swiss] def toSwissPairingGames(
      swissId: Swiss.Id,
      ids: List[SwissPairingGameIds]
  ): Fu[List[SwissPairingGames]] =
    getGamesMap(toGameIds(ids)) map { gamesById =>
      ids.flatMap(ids => toSwissPairingGames(swissId, ids, gamesById))
    }

  def getSwissPairingGamesForGame(game: Game): Fu[Option[SwissPairingGames]] =
    getSwissPairingForGame(game).flatMap {
      _ ?? { pairing =>
        getGamesMap(pairing.multiMatchGameIds.foldLeft(List(pairing.id))(_ ++ _)) map { gamesById =>
          toSwissPairingGames(pairing.swissId, pairing, gamesById).head.some
        }
      }
    }

  private[swiss] def getSwissPairingForGame(game: Game): Fu[Option[SwissPairing]] =
    SwissPairing.fields { f =>
      colls.pairing
        .find(
          $or(
            $doc(f.id                -> game.id),
            $doc(f.multiMatchGameIds -> game.id)
          )
        )
        .one[SwissPairing]
    }

  private[swiss] def updateMultiMatchProgress(game: Game): Funit = {
    getSwissPairingForGame(game).flatMap {
      _ ?? { pairing =>
        getGamesMap(pairing.multiMatchGameIds.foldLeft(List(pairing.id))(_ ++ _)) map { gamesById =>
          toSwissPairingGames(pairing.swissId, pairing, gamesById) map updateMultiMatchProgress
        }
      }
    }
    funit
  }

  private[swiss] def rematchForMultiGame(game: SwissPairingGames): Funit =
    getSwissPairingForGame(game.game) map { pairing =>
      {
        pairing.map { pairing =>
          SwissPlayer.fields { f =>
            colls.player.list[SwissPlayer]($doc(f.swissId -> pairing.swissId)) map { players =>
              val playerMap = SwissPlayer.toMap(players)
              byId(game.swissId).map(
                _.map(swiss =>
                  idGenerator.game.map(rematchId =>
                    SwissPairing.fields { f2 =>
                      colls.pairing.update
                        .one(
                          $doc(f2.id                 -> pairing.id),
                          $push(f2.multiMatchGameIds -> rematchId)
                        ) >> {
                        val gameIds: List[Game.ID] = pairing.multiMatchGameIds.fold(List(rematchId))(
                          _ ++ List(rematchId)
                        )
                        val pairingUpdated = pairing.copy(multiMatchGameIds = Some(gameIds))
                        val nextGame =
                          director.makeGame(swiss, playerMap, true)(pairingUpdated)
                        gameRepo.insertDenormalized(nextGame) >> recomputeAndUpdateAll(
                          pairing.swissId
                        ) >>- onStart(nextGame.id)
                      }
                    }
                  )
                )
              )
            }
          }
        }.unit
      }
    }

  private[swiss] def updateMultiMatchProgress(game: SwissPairingGames): Funit =
    (
      game.finishedOrAborted,
      game.isBestOfX || game.isPlayX,
      game.multiMatchGames
    ) match {
      case (true, _, _)        => finishGame(game)
      case (false, true, None) => rematchForMultiGame(game)
      case (false, true, Some(g)) => {
        if (g.length + 1 < game.nbGamesPerRound) // actual logic in SwissPairing (game.finishedOrAborted)
          rematchForMultiGame(game)
        else funit // This will be called by checkOngoingGames
      }
      case (false, false, _) =>
        sys.error("Why is this being called when the game isn't finished and not a multimatch!?")
    }

  private[swiss] def finishGame(game: SwissPairingGames): Funit =
    Sequencing(game.swissId)(byId) { swiss =>
      if (!swiss.isStarted) {
        logger.info(s"Removing pairing ${game.game.id} finished after swiss ${swiss.id}")
        colls.pairing.delete.one($id(game.game.id)) inject false
      } else {
        colls.pairing.update
          .one(
            $id(game.game.id),
            $set(
              SwissPairing.Fields.matchStatus -> pairingMatchStatusHandler
                .writeTry(Right(game.matchOutcome))
                .get,
              SwissPairing.Fields.status -> pairingStatusHandler
                .writeTry(Right(game.winnerPlayerIndex))
                .get
            )
          )
          .flatMap { result =>
            if (result.nModified == 0) fuccess(false) // dedup
            else {
              if (swiss.nbOngoing > 0)
                colls.swiss.update.one($id(swiss.id), $inc("nbOngoing" -> -1))
              else
                fuccess {
                  logger.warn(s"swiss ${swiss.id} nbOngoing = ${swiss.nbOngoing}")
                }
            } >>
              game.playersWhoDidNotMove
                .map(_.userId)
                .map { absent =>
                  SwissPlayer.fields { f =>
                    colls.player
                      .updateField($doc(f.swissId -> swiss.id, f.userId -> absent), f.absent, true)
                      .void
                  }
                }
                .sequenceFu >> {
                (swiss.nbOngoing <= 1) ?? {
                  if (swiss.round.value == swiss.settings.nbRounds) doFinish(swiss)
                  else if (swiss.settings.manualRounds) fuccess {
                    systemChat(swiss.id, s"Round ${swiss.round.value + 1} needs to be scheduled.")
                  }
                  else
                    colls.swiss
                      .updateField(
                        $id(swiss.id),
                        "nextRoundAt",
                        swiss.settings.dailyInterval match {
                          case Some(days) => game.createdAt plusDays days
                          case None =>
                            DateTime.now.plusSeconds(
                              swiss.settings.roundInterval.toSeconds.toInt + (if (swiss.isHalfway)
                                                                                swiss.settings.halfwayBreak.toSeconds.toInt
                                                                              else 0)
                            )
                        }
                      )
                      .void >>-
                      systemChat(
                        swiss.id,
                        s"Round ${swiss.round.value + 1}${medleyRoundText(swiss, 1)} will start soon."
                      )
                }
              } inject true
          }
      }
    }.flatMap {
      case true => recomputeAndUpdateAll(game.swissId)
      case _    => funit
    }

  private def medleyRoundText(swiss: Swiss, offset: Int = 0) =
    if (swiss.isMedley)
      s" [${VariantKeys.variantName(swiss.variantForRound(swiss.round.value + offset))}]"
    else ""

  private[swiss] def destroy(swiss: Swiss): Funit =
    colls.swiss.delete.one($id(swiss.id)) >>
      colls.pairing.delete.one($doc(SwissPairing.Fields.swissId -> swiss.id)) >>
      colls.player.delete.one($doc(SwissPairing.Fields.swissId -> swiss.id)).void >>-
      socket.reload(swiss.id)

  private[swiss] def finish(oldSwiss: Swiss): Funit =
    Sequencing(oldSwiss.id)(startedById) { swiss =>
      colls.pairing.exists($doc(SwissPairing.Fields.swissId -> swiss.id)) flatMap {
        if (_) doFinish(swiss)
        else destroy(swiss)
      }
    }

  private def getWinner(id: Swiss.Id) =
    SwissPlayer
      .fields { f =>
        colls.player.primitiveOne[User.ID](
          $doc(f.swissId -> id, f.disqualified $ne true),
          $sort desc f.score,
          f.userId
        )
      }

  private def doFinish(swiss: Swiss): Funit =
    getWinner(swiss.id)
      .flatMap { winnerUserId =>
        colls.swiss.update
          .one(
            $id(swiss.id),
            $unset("nextRoundAt", "lastRoundAt", "featurable") ++ $set(
              "settings.n" -> swiss.round,
              "finishedAt" -> DateTime.now,
              "winnerId"   -> winnerUserId
            )
          )
          .void zip
          SwissPairing.fields { f =>
            colls.pairing.delete.one($doc(f.swissId -> swiss.id, f.status -> true)) map { res =>
              if (res.n > 0) logger.warn(s"Swiss ${swiss.id} finished with ${res.n} ongoing pairings")
            }
          } void
      } >>- {
      systemChat(swiss.id, s"Tournament completed!")
      socket.reload(swiss.id)
      system.scheduler
        .scheduleOnce(10 seconds) {
          // we're delaying this to make sure the ranking has been recomputed
          // since doFinish is called by finishGame before that
          rankingApi(swiss) foreach { ranking =>
            Bus.publish(SwissFinish(swiss.id, ranking), "swissFinish")
          }
          awardTrophies(swiss).unit
        }
        .unit
    }

  def kill(swiss: Swiss): Funit = {
    if (swiss.isStarted)
      finish(swiss) >>- systemChat(swiss.id, s"Tournament cancelled by its creator.")
    else if (swiss.isCreated) destroy(swiss)
    else funit
  } >>- cache.featuredInTeam.invalidate(swiss.teamId)

  private def awardTrophies(swiss: Swiss, date: DateTime = DateTime.now): Funit = {
    SwissPlayer
      .fields { f =>
        colls.player.primitive[User.ID](
          $doc(f.swissId -> swiss.id, f.disqualified $ne true),
          $sort desc f.score,
          3,
          f.userId
        )
      }
      .map(userIds =>
        swiss.trophies
          .zip(userIds)
          .map { case (t, p) => t.zip(p.some) }
          .flatten
          .map { case (trophyKind, userId) =>
            trophyApi.award(
              Swiss.swissUrl(swiss.id),
              userId.toString,
              trophyKind,
              swiss.name.some,
              swiss.trophyExpiryDays,
              date
            )
          }
          .unit
      )
  }

  def winnersByTrophy(trophy: String): Fu[List[Swiss]] =
    colls.swiss
      .find($doc("trophy1st" -> trophy, "finishedAt" $exists true))
      .sort($sort desc "startsAt")
      .cursor[Swiss]()
      .list()

  def nextByTrophy(trophy: String): Fu[Option[Swiss]] =
    colls.swiss
      .find($doc("trophy1st" -> trophy, "finishedAt" $exists false))
      .sort($sort asc "startsAt")
      .cursor[Swiss]()
      .headOption

  def roundInfo = cache.roundInfo.get _

  def byTeamCursor(teamId: TeamID) =
    colls.swiss
      .find($doc("teamId" -> teamId))
      .sort($sort desc "startsAt")
      .cursor[Swiss]()

  def teamOf(id: Swiss.Id): Fu[Option[TeamID]] =
    colls.swiss.primitiveOne[TeamID]($id(id), "teamId")

  private def recomputeAndUpdateAll(id: Swiss.Id): Funit =
    scoring(id).flatMap {
      _ ?? { res =>
        rankingApi.update(res)
        standingApi.update(res) >>
          boardApi.update(res) >>-
          socket.reload(id)
      }
    }

  private[swiss] def startPendingRounds: Funit =
    colls.swiss
      .find($doc("nextRoundAt" $lt DateTime.now), $id(true).some)
      .cursor[Bdoc]()
      .list(10)
      .map(_.flatMap(_.getAsOpt[Swiss.Id]("_id")))
      .flatMap { ids =>
        lila.common.Future.applySequentially(ids) { id =>
          Sequencing(id)(notFinishedById) { swiss =>
            if (swiss.round.value >= swiss.settings.nbRounds) doFinish(swiss)
            else if (swiss.nbPlayers >= 2)
              director.startRound(swiss).flatMap {
                _.fold {
                  systemChat(swiss.id, "All possible pairings were played.")
                  doFinish(swiss)
                } {
                  case s if s.nextRoundAt.isEmpty =>
                    systemChat(s.id, s"Round ${s.round.value}${medleyRoundText(s)} started.")
                    funit
                  case s =>
                    systemChat(s.id, s"Round ${s.round.value} failed.", volatile = true)
                    colls.swiss.update
                      .one($id(s.id), $set("nextRoundAt" -> DateTime.now.plusSeconds(61)))
                      .void
                }
              }
            else {
              if (swiss.startsAt isBefore DateTime.now.minusMinutes(60)) destroy(swiss)
              else {
                systemChat(swiss.id, "Not enough players for first round; delaying start.", volatile = true)
                colls.swiss.update
                  .one($id(swiss.id), $set("nextRoundAt" -> DateTime.now.plusSeconds(121)))
                  .void
              }
            }
          } >> recomputeAndUpdateAll(id)
        }
      }
      .monSuccess(_.swiss.tick)

  private[swiss] def checkOngoingGames: Funit =
    SwissPairing
      .fields { f =>
        colls.pairing.ext
          .aggregateList(100) { framework =>
            import framework._
            Match($doc(f.status -> SwissPairing.ongoing)) -> List(
              GroupField(f.swissId)(
                "ids" -> Push(
                  $doc(
                    "id"                -> f.id,
                    "multiMatchGameIds" -> f.multiMatchGameIds,
                    "isMatchScore"      -> f.isMatchScore,
                    "isBestOfX"         -> f.isBestOfX,
                    "isPlayX"           -> f.isPlayX,
                    "nbGamesPerRound"   -> f.nbGamesPerRound
                  )
                )
              )
            )
          }
      }
      .map {
        _.flatMap { doc =>
          for {
            swissId        <- doc.getAsOpt[Swiss.Id]("_id")
            pairingGameIds <- doc.getAsOpt[List[SwissPairingGameIds]]("ids")
          } yield swissId -> pairingGameIds
        }
      }
      .flatMap {
        _.map { case (swissId, pairingGameIds) =>
          Sequencing[List[SwissPairingGames]](swissId)(byId) { _ =>
            roundSocket.getGames(toGameIds(pairingGameIds)) map { pairs =>
              val gamesById           = toGamesMap(pairs)
              val pairingGames        = pairingGameIds.flatMap(ids => toSwissPairingGames(swissId, ids, gamesById))
              val games               = pairs.collect { case (_, Some(g)) => g }
              val (finished, ongoing) = pairingGames.partition(_.finishedOrAborted)
              val flagged             = ongoing.flatMap(_.outoftime)
              val missingIds          = pairs.collect { case (id, None) => id }
              lila.mon.swiss.games("finished").record(finished.size)
              lila.mon.swiss.games("ongoing").record(ongoing.size)
              lila.mon.swiss.games("flagged").record(flagged.size)
              lila.mon.swiss.games("missing").record(missingIds.size)
              if (flagged.nonEmpty)
                Bus.publish(lila.hub.actorApi.map.TellMany(flagged.map(_.id), QuietFlag), "roundSocket")
              ongoing.foreach(updateMultiMatchProgress)
              if (missingIds.nonEmpty)
                colls.pairing.delete.one($inIds(missingIds))
              finished
            }
          } flatMap {
            _.map(finishGame).sequenceFu.void
          }
        }.sequenceFu.void
      }

  private def systemChat(id: Swiss.Id, text: String, volatile: Boolean = false): Unit =
    chatApi.userChat.service(Chat.Id(id.value), text, _.Swiss, volatile)

  def withdrawAll(user: User, teamIds: List[TeamID]): Funit =
    colls.swiss
      .aggregateList(Int.MaxValue, readPreference = ReadPreference.secondaryPreferred) { implicit framework =>
        import framework._
        Match($doc("finishedAt" $exists false, "nbPlayers" $gt 0, "teamId" $in teamIds)) -> List(
          PipelineOperator(
            $doc(
              "$lookup" -> $doc(
                "from" -> colls.player.name,
                "let"  -> $doc("s" -> "$_id"),
                "pipeline" -> $arr(
                  $doc(
                    "$match" -> $doc(
                      "$expr" -> $doc(
                        "$and" -> $arr(
                          $doc("$eq" -> $arr("$u", user.id)),
                          $doc("$eq" -> $arr("$s", "$$s"))
                        )
                      )
                    )
                  )
                ),
                "as" -> "player"
              )
            )
          ),
          Match("player" $ne $arr()),
          Project($id(true))
        )
      }
      .map(_.flatMap(_.getAsOpt[Swiss.Id]("_id")))
      .flatMap {
        _.map { withdraw(_, user.id) }.sequenceFu.void
      }

  def isUnfinished(id: Swiss.Id): Fu[Boolean] =
    colls.swiss.exists($id(id) ++ $doc("finishedAt" $exists false))

  def filterPlaying(id: Swiss.Id, userIds: Seq[User.ID]): Fu[List[User.ID]] =
    userIds.nonEmpty ??
      colls.swiss.exists($id(id) ++ $doc("finishedAt" $exists false)) flatMap {
        _ ?? SwissPlayer.fields { f =>
          colls.player.distinctEasy[User.ID, List](
            f.userId,
            $doc(
              f.id $in userIds.map(SwissPlayer.makeId(id, _)),
              f.absent $ne true
            )
          )
        }
      }

  def resultStream(swiss: Swiss, perSecond: MaxPerSecond, nb: Int): Source[SwissPlayer.WithRank, _] =
    SwissPlayer.fields { f =>
      colls.player
        .find($doc(f.swissId -> swiss.id))
        .sort($sort desc f.score)
        .batchSize(perSecond.value)
        .cursor[SwissPlayer](ReadPreference.secondaryPreferred)
        .documentSource(nb)
        .throttle(perSecond.value, 1 second)
        .zipWithIndex
        .map { case (player, index) =>
          SwissPlayer.WithRank(player, index.toInt + 1)
        }
    }

  private val idNameProjection = $doc("name" -> true)

  def idNames(ids: List[Swiss.Id]): Fu[List[Swiss.IdName]] =
    colls.swiss.find($inIds(ids), idNameProjection.some).cursor[Swiss.IdName]().list()

  private def Sequencing[A: Zero](
      id: Swiss.Id
  )(fetch: Swiss.Id => Fu[Option[Swiss]])(run: Swiss => Fu[A]): Fu[A] =
    sequencer(id.value) {
      fetch(id) flatMap {
        _ ?? run
      }
    }
}
