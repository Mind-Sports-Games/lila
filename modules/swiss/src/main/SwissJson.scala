package lila.swiss

import strategygames.format.{ Forsyth }
import strategygames.{ ByoyomiClock, Clock, GameFamily, Player => PlayerIndex, P1, P2 }
import strategygames.variant.Variant
import strategygames.draughts.Board.BoardSize

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.i18n.Lang
import play.api.libs.json._
import scala.concurrent.ExecutionContext

import lila.common.{ GreatPlayer, LightUser }
import lila.db.dsl._
import lila.game.{ Game, MultiPointState }
import lila.game.Handicaps.goRatingDisplay
import lila.quote.Quote
import lila.quote.Quote.quoteWriter
import lila.socket.Socket.SocketVersion
import lila.user.{ User, UserRepo }
import lila.i18n.{ I18nKeys => trans, VariantKeys }

final class SwissJson(
    colls: SwissColls,
    standingApi: SwissStandingApi,
    rankingApi: SwissRankingApi,
    boardApi: SwissBoardApi,
    statsApi: SwissStatsApi,
    userRepo: UserRepo,
    lightUserApi: lila.user.LightUserApi,
    swissApi: SwissApi,
)(implicit ec: ExecutionContext) {

  import SwissJson._
  import BsonHandlers._

  def api(swiss: Swiss) =
    swissJsonBase(swiss) ++ Json.obj(
      "rated" -> swiss.settings.rated
    )

  def apply(
      swiss: Swiss,
      me: Option[User],
      isInTeam: Boolean,
      verdicts: SwissCondition.All.WithVerdicts,
      reqPage: Option[Int] = None, // None = focus on me
      socketVersion: Option[SocketVersion] = None,
      playerInfo: Option[SwissPlayer.ViewExt] = None
  )(implicit lang: Lang): Fu[JsObject] = {
    for {
      myInfo <- me.?? { fetchMyInfo(swiss, _) }
      page = reqPage orElse myInfo.map(_.page) getOrElse 1
      standing <- standingApi(swiss, page)
      podium   <- podiumJson(swiss)
      boards   <- boardApi(swiss.id)
      player   <- playerJson(swiss, playerInfo)
      stats    <- statsApi(swiss)
    } yield swissJsonBase(swiss) ++ Json
      .obj(
        "canJoin" -> {
          {
            (swiss.isNotFinished && myInfo.exists(_.player.absent)) ||
            (myInfo.isEmpty && swiss.isEnterable && isInTeam)
          } && verdicts.accepted
        },
        "standing" -> standing,
        "boards"   -> boards.map(boardJson)
      ) ++ Json.obj(
    "me" -> myInfo.map(myInfoJson),
    "joinTeam" -> (!isInTeam).option(swiss.teamId),
    "socketVersion" -> socketVersion.map(_.value),
    "playerInfo" -> player,
    "podium" -> podium,
    "isRecentlyFinished" -> swiss.isRecentlyFinished,
    "password" -> swiss.settings.password.isDefined,
    "stats" -> stats,
    "greatPlayer" -> GreatPlayer.wikiUrl(swiss.name).map { url =>
      Json.obj("name" -> swiss.name, "url" -> url)
    })
  }.monSuccess(_.swiss.json)

  def fetchMyInfo(swiss: Swiss, me: User): Fu[Option[MyInfo]] =
    colls.player.byId[SwissPlayer](SwissPlayer.makeId(swiss.id, me.id).value) flatMap {
      _ ?? { player =>
        updatePlayerRating(swiss, player, me) >>
          SwissPairing.fields { f =>
            (swiss.nbOngoing > 0)
              .?? {
                colls.pairing
                  .find(
                    $doc(f.swissId -> swiss.id, f.players -> player.userId, f.status -> SwissPairing.ongoing),
                    $doc(
                      f.id                -> true,
                      f.multiMatchGameIds -> true,
                      f.isMatchScore      -> true,
                      f.isBestOfX         -> true,
                      f.isPlayX           -> true,
                      f.nbGamesPerRound   -> true
                    ).some
                  )
                  .one[SwissPairingGameIds]
              }
              .flatMap { gameIds =>
                rankingApi(swiss).dmap(_ get player.userId) map2 { rank =>
                  MyInfo(rank, gameIds, me, player)
                }
              }
          }
      }
    }

  private def updatePlayerRating(swiss: Swiss, player: SwissPlayer, user: User): Funit =
    swiss.settings.rated
      .option(user perfs swiss.roundPerfType)
      .filter(_.intRating != player.rating)
      .?? { perf =>
        SwissPlayer.fields { f =>
          colls.player.update
            .one(
              $id(SwissPlayer.makeId(swiss.id, user.id)),
              $set(f.rating -> perf.intRating)
            )
            .void
        }
      }

  private def podiumJson(swiss: Swiss): Fu[Option[JsArray]] =
    swiss.isFinished ?? {
      SwissPlayer.fields { f =>
        colls.player
          .find($doc(f.swissId -> swiss.id, f.disqualified $ne true))
          .sort($sort desc f.score)
          .cursor[SwissPlayer]()
          .list(3) flatMap { top3 =>
          // check that the winner is still correctly denormalized
          top3.headOption
            .map(_.userId)
            .filter(w => swiss.winnerId.fold(true)(w !=))
            .foreach {
              colls.swiss.updateField($id(swiss.id), "winnerId", _).void
            }
            .unit
          userRepo.filterEngine(top3.map(_.userId)) map { engines =>
            JsArray(
              top3.map { player =>
                playerJsonBase(
                  player,
                  lightUserApi.sync(player.userId) | LightUser.fallback(player.userId),
                  performance = true
                ).add("engine", engines(player.userId))
              }
            ).some
          }
        }
      }
    }

  private def playerJson(swiss: Swiss, playerInfo: Option[SwissPlayer.ViewExt]): Fu[Option[JsObject]] =
    playerInfo.fold[Fu[Option[JsObject]]](fuccess(None)) { playerView =>
      swissApi.playerToPairingGames(playerView).flatMap { pairingsWithGames =>
        fuccess(Some(playerJsonExt(swiss, playerView, swiss.settings.isMultiPoint option pairingsWithGames)))
      }
    }

  def playerResult(p: SwissPlayer.WithUserAndRank): JsObject = p match {
    case SwissPlayer.WithUserAndRank(player, user, rank) =>
      Json
        .obj(
          "rank"          -> rank,
          "points"        -> player.points.value,
          "tieBreak"      -> player.tieBreak,
          "tieBreak2"     -> player.tieBreak2,
          "rating"        -> player.rating,
          "inputRating"   -> player.inputRating,
          "ratingDisplay" -> player.inputRating.map(goRatingDisplay(_)),
          "username"      -> user.name
        )
        .add("title" -> user.title)
        .add("performance" -> player.performance)
        .add("absent" -> player.absent)
        .add("disqualified" -> player.disqualified)
  }
}

object SwissJson {

  private def formatDate(date: DateTime) = ISODateTimeFormat.dateTime print date

  private def swissJsonBase(swiss: Swiss)(implicit lang: Lang = lila.i18n.defaultLang) =
    Json
      .obj(
        "id"               -> swiss.id.value,
        "createdBy"        -> swiss.createdBy,
        "startsAt"         -> formatDate(swiss.startsAt),
        "name"             -> swiss.name,
        "clock"            -> swiss.clock,
        "variant"          -> swiss.variant.key,
        "isMedley"         -> swiss.isMedley,
        "isMcMahon"        -> swiss.settings.mcmahon,
        "mcmahonCutoff"    -> swiss.settings.mcmahonCutoff,
        "isHandicapped"    -> swiss.settings.handicapped,
        "backgammonPoints" -> swiss.settings.backgammonPoints,
        "p1Name" -> (if (swiss.isMedley || swiss.variant.recalcStartPlayerForStats) trans.p1.txt()
                     else swiss.variant.playerNames(P1)),
        "p2Name" -> (if (swiss.isMedley || swiss.variant.recalcStartPlayerForStats) trans.p2.txt()
                     else swiss.variant.playerNames(P2)),
        "round"                 -> swiss.round,
        "roundVariant"          -> swiss.roundVariant.key,
        "roundVariantName"      -> VariantKeys.variantName(swiss.roundVariant),
        "nbRounds"              -> swiss.actualNbRounds,
        "nbPlayers"             -> swiss.nbPlayers,
        "nbOngoing"             -> swiss.nbOngoing,
        "trophy1st"             -> swiss.trophy1st,
        "trophy2nd"             -> swiss.trophy2nd,
        "trophy3rd"             -> swiss.trophy3rd,
        "isMatchScore"          -> swiss.settings.isMatchScore,
        "isBestOfX"             -> swiss.settings.isBestOfX,
        "isPlayX"               -> swiss.settings.isPlayX,
        "nbGamesPerRound"       -> swiss.settings.nbGamesPerRound,
        "timeBeforeStartToJoin" -> swiss.settings.timeBeforeStartToJoin,
        "status" -> {
          if (swiss.isStarted) "started"
          else if (swiss.isFinished) "finished"
          else "created"
        }
      )
      .add("quote" -> swiss.isCreated.option(Quote.one(swiss.id.value, swiss.mainGameFamily)))
      .add("nextRound" -> swiss.nextRoundAt.map { next =>
        Json.obj(
          "at" -> formatDate(next),
          "in" -> (next.getSeconds - nowSeconds).toInt.atLeast(0)
        )
      })

  private[swiss] def playerJson(swiss: Swiss, view: SwissPlayer.View): JsObject =
    playerJsonBase(view, performance = false) ++ Json
      .obj(
        "sheetMin" -> swiss.allRounds
          .map(view.pairings.get)
          .zip(view.sheet.outcomes)
          .map {
            pairingJsonOrOutcome(view.player)
          }
          .mkString("|")
      ) ++ Json.obj(
      "mmStartingScore" -> (swiss.settings.mcmahon ?? view.player.mcMahonStartingScore(
        swiss.settings.mcmahonCutoffGrade
      ))
    )

  private def commonPlayerJsonExt(swiss: Swiss, playerView: SwissPlayer.ViewExt): JsObject =
    playerJsonBase(playerView, performance = true) ++ Json.obj(
      "sheet" -> swiss.allRounds
        .zip(playerView.sheet.outcomes)
        .reverse
        .map { case (round, outcome) =>
          playerView.pairings.get(round).fold[JsValue](JsString(outcomeJson(outcome))) { p =>
            pairingJson(playerView.player, p.pairing) ++
              Json.obj(
                "user"        -> p.player.user,
                "rating"      -> p.player.player.rating,
                "inputRating" -> p.player.player.inputRating,
                "ratingDisplay" -> (swiss.variant.gameFamily == GameFamily
                  .Go() && (swiss.settings.handicapped || swiss.settings.mcmahon)) ?? p.player.player.inputRating
                  .map(goRatingDisplay(_))
              )
          }
        }
    ) ++ Json.obj(
      "mmStartingScore" -> (swiss.settings.mcmahon ?? playerView.player.mcMahonStartingScore(
        swiss.settings.mcmahonCutoffGrade
      ))
    )

  def playerJsonExt(swiss: Swiss, playerView: SwissPlayer.ViewExt, pairingsWithGames: Option[Seq[SwissPairingGames]] = None): JsObject =
    commonPlayerJsonExt(swiss, playerView) ++ Json.obj(
      "multiPoint" -> pairingsWithGames.map(multiPointResultsJson)
    )

  private def multiPointResultsJson(swissPairingGames: Seq[SwissPairingGames]) =
    JsArray(
      swissPairingGames.flatMap {
        pairingGame => Seq(
          Json.obj(
            "target" -> pairingGame.game.metadata.multiPointState.fold(0)(_.target),
            "players" -> Json.obj(
              "p1" -> Json.obj(
                "userId" -> pairingGame.game.p1Player.userId,
              ),
              "p2" -> Json.obj(
                "userId" -> pairingGame.game.p2Player.userId,
              )
            ),
            "games" -> JsArray(
                pairingGame.multiMatchGames.toList.flatten.reverse.map { game => 
                    Json.obj(
                      "id"        -> game.id,
                      "p1UserId"  -> game.p1Player.userId,
                      "startingScore" -> Json.obj(
                        "p1" -> game.multiPointResult.get.p1Points.toString,
                        "p2" -> game.multiPointResult.get.p2Points.toString,
                      )
                    )
                } :+
                Json.obj(
                  "id"        -> pairingGame.game.id,
                  "p1UserId"  -> pairingGame.game.p1Player.userId,
                  "startingScore" -> Json.obj(
                    "p1" -> pairingGame.game.multiPointResult.get.p1Points.toString,
                    "p2" -> pairingGame.game.multiPointResult.get.p2Points.toString,
                  )
                )
            )
          )
        )
      }
    )

  private def playerJsonBase(
      view: SwissPlayer.Viewish,
      performance: Boolean
  ): JsObject =
    playerJsonBase(view.player, view.user, performance) ++
      Json.obj("rank" -> view.rank)

  private def playerJsonBase(
      p: SwissPlayer,
      user: LightUser,
      performance: Boolean
  ): JsObject =
    Json
      .obj(
        "user"          -> user,
        "rating"        -> p.rating,
        "inputRating"   -> p.inputRating,
        "ratingDisplay" -> p.inputRating.map(goRatingDisplay(_)),
        "points"        -> p.points,
        "tieBreak"      -> p.tieBreak,
        "tieBreak2"     -> p.tieBreak2
      )
      .add("performance" -> (performance ?? p.performance))
      .add("provisional" -> p.provisional)
      .add("absent" -> p.absent)
      .add("disqualified" -> p.disqualified)

  private def outcomeJson(outcome: List[SwissSheet.Outcome]): String =
    outcome.head match {
      case SwissSheet.Absent => "absent"
      case SwissSheet.Bye    => "bye"
      case _                 => ""
    }

  private def pairingJsonMin(player: SwissPlayer, pairing: SwissPairing): String = {
    val status =
      if (pairing.isOngoing) "o"
      else pairing.resultFor(player.userId).fold("d") { r => if (r) "w" else "l" }
    val multiMatchIds = pairing.multiMatchGameIds.fold("")(l => "_" + l.mkString("_"))
    val useMatchScore = if (pairing.isMatchScore) "s" else ""
    val matchScore =
      pairing.matchScoreFor(player.userId) // "" if isMatchScore is false, otherwise 2 digit string number
    val bestOfX    = if (pairing.isBestOfX) "x" else ""
    val playX      = if (pairing.isPlayX) "px" else ""
    val openingFEN = pairing.openingFEN.map(_.value).fold("")(f => s"=${f}")
    s"${pairing.gameId}$status${pairing.nbGamesPerRound}$bestOfX$playX$useMatchScore$matchScore$multiMatchIds$openingFEN"
  }

  private def pairingJson(player: SwissPlayer, pairing: SwissPairing) =
    Json
      .obj(
        "g"     -> pairing.gameId,
        "mmids" -> pairing.multiMatchGameIds,
        "x"     -> pairing.isBestOfX,
        "px"    -> pairing.isPlayX,
        "gpr"   -> pairing.nbGamesPerRound,
        "ms"    -> pairing.isMatchScore,
        "mp"    -> pairing.matchScoreFor(player.userId),
        "of"    -> pairing.openingFEN.map(_.value)
      )
      .add("o" -> pairing.isOngoing)
      .add("w" -> pairing.resultFor(player.userId))
      .add("mr" -> pairing.multiMatchResultsFor(player.userId))
      .add("c" -> (pairing.p1 == player.userId))
      .add("vi" -> pairing.variant.map(_.perfIcon.toString))

  private def pairingJsonOrOutcome(
      player: SwissPlayer
  ): ((Option[SwissPairing], List[SwissSheet.Outcome])) => String = {
    case (Some(pairing), _) => pairingJsonMin(player, pairing)
    case (_, outcome)       => outcomeJson(outcome)
  }

  private def myInfoJson(i: MyInfo) =
    Json
      .obj(
        "rank"              -> i.rank,
        "gameId"            -> i.gameIds.map(_.id),
        "multiMatchGameIds" -> i.gameIds.map(_.multiMatchGameIds),
        "isMatchScore"      -> i.gameIds.map(_.isMatchScore),
        "isBestOfX"         -> i.gameIds.map(_.isBestOfX),
        "isPlayX"           -> i.gameIds.map(_.isPlayX),
        "nbGamesPerRound"   -> i.gameIds.map(_.nbGamesPerRound),
        "id"                -> i.user.id,
        "name"              -> i.user.username,
        "absent"            -> i.player.absent
      )

  private[swiss] def boardSizeJson(v: Variant) = v match {
    case Variant.Draughts(v) =>
      Some(
        Json.obj(
          "size" -> Json.arr(v.boardSize.width, v.boardSize.height),
          "key"  -> v.boardSize.key
        )
      )
    case _ => None
  }

  private[swiss] def boardGameJson(g: Game, p1: SwissBoard.Player, p2: SwissBoard.Player) =
    Json
      .obj(
        "id"          -> g.id,
        "gameLogic"   -> g.variant.gameLogic.name.toLowerCase(),
        "gameFamily"  -> g.variant.gameFamily.key,
        "variantKey"  -> g.variant.key,
        "fen"         -> Forsyth.>>(g.variant.gameLogic, g.stratGame).value,
        "lastMove"    -> ~g.lastActionKeys,
        "orientation" -> g.naturalOrientation.name,
        "p1"          -> boardPlayerJson(p1),
        "p2"          -> boardPlayerJson(p2),
        "p1Color"     -> g.variant.playerColors(P1),
        "p2Color"     -> g.variant.playerColors(P2)
      )
      .add(
        "clock" -> g.clock.ifTrue(g.isBeingPlayed).map { c =>
          Json.obj(
            "p1" -> c.remainingTime(P1).roundSeconds,
            "p2" -> c.remainingTime(P2).roundSeconds
          )
        }
      )
      .add("multiPointScore" -> multiPointScoreJson(g))

  private[swiss] def boardPlayerFromGame(g: Game, b: SwissBoard, playerIndex: PlayerIndex) =
    if (playerIndex == P1)
      if (g.p1Player.userId.contains(b.p1.user.id)) b.p1
      else b.p2
    else if (g.p2Player.userId.contains(b.p2.user.id)) b.p2
    else b.p1

  private[swiss] def multiPointScoreJson(g: Game) =
    g.metadata.multiPointState.map { mps =>
      Json.obj(
        "target" -> mps.target,
        "p1" -> mps.p1Points,
        "p2" -> mps.p2Points
      )
    }

  private[swiss] def boardJson(b: SwissBoard.WithGame) =
    boardGameJson(b.game, b.board.p1, b.board.p2)
      .add("winner" -> b.game.winnerPlayerIndex.map(_.name))
      .add("boardSize" -> boardSizeJson(b.game.variant))
      .add("isBestOfX" -> b.board.isBestOfX)
      .add("isPlayX" -> b.board.isPlayX)
      .add("multiMatchGameIds" -> b.board.multiMatchGameIds)
      .add(
        "multiMatchGames" -> b.multiMatchGames.map(l =>
          l.map(g =>
            boardGameJson(g, boardPlayerFromGame(g, b.board, P1), boardPlayerFromGame(g, b.board, P2))
              .add("boardSize" -> boardSizeJson(g.variant))
          )
        )
      )

  private def boardPlayerJson(player: SwissBoard.Player) =
    Json.obj(
      "rank"          -> player.rank,
      "rating"        -> player.rating,
      "inputRating"   -> player.inputRating,
      "ratingDisplay" -> player.inputRating.map(goRatingDisplay(_)),
      "user"          -> player.user
    )

  implicit private val roundNumberWriter: Writes[SwissRound.Number] = Writes[SwissRound.Number] { n =>
    JsNumber(n.value)
  }
  implicit private val pointsWriter: Writes[Swiss.Points] = Writes[Swiss.Points] { p =>
    JsNumber(p.value)
  }
  implicit private val performanceWriter: Writes[Swiss.Performance] = Writes[Swiss.Performance] { t =>
    JsNumber(t.value.toInt)
  }

  implicit private val clockWrites: OWrites[strategygames.ClockConfig] = OWrites { clock =>
    clock match {
      // TODO: this clock json should be universal
      case fc: Clock.Config =>
        Json.obj(
          "limit"     -> fc.limitSeconds,
          "increment" -> fc.incrementSeconds
        )
      case bc: Clock.BronsteinConfig =>
        Json.obj(
          "limit"     -> bc.limitSeconds,
          "delay"     -> bc.delaySeconds,
          "delayType" -> "bronstein"
        )
      case udc: Clock.SimpleDelayConfig =>
        Json.obj(
          "limit"     -> udc.limitSeconds,
          "delay"     -> udc.delaySeconds,
          "delayType" -> "usdelay"
        )
      case bc: ByoyomiClock.Config =>
        Json.obj(
          "limit"     -> bc.limitSeconds,
          "increment" -> bc.incrementSeconds,
          "byoyomi"   -> bc.byoyomiSeconds,
          "periods"   -> bc.periodsTotal
        )
    }
  }

  implicit private val statsWrites: Writes[SwissStats] = Json.writes[SwissStats]
}
