package lila.api

import akka.stream.scaladsl._
import strategygames.format.FEN
import strategygames.format.pgn.Tag
import strategygames.{ Player => PlayerIndex, P2, P1 }
import org.joda.time.DateTime
import play.api.libs.json._
import scala.concurrent.duration._

import lila.analyse.{ JsonView => analysisJson, Analysis }
import lila.common.config.MaxPerSecond
import lila.common.Json.jodaWrites
import lila.common.{ HTTPRequest, LightUser }
import lila.db.dsl._
import lila.game.JsonView._
import lila.game.PgnDump.WithFlags
import lila.game.{ Game, PerfPicker, Query }
import lila.team.GameTeams
import lila.tournament.Tournament
import lila.user.User
import lila.round.GameProxyRepo

final class GameApiV2(
    pgnDump: PgnDump,
    gameRepo: lila.game.GameRepo,
    tournamentRepo: lila.tournament.TournamentRepo,
    pairingRepo: lila.tournament.PairingRepo,
    playerRepo: lila.tournament.PlayerRepo,
    swissApi: lila.swiss.SwissApi,
    analysisRepo: lila.analyse.AnalysisRepo,
    getLightUser: LightUser.Getter,
    realPlayerApi: RealPlayerApi,
    gameProxy: GameProxyRepo
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem
) {

  import GameApiV2._

  private val keepAliveInterval = 70.seconds // play's idleTimeout = 75s

  def exportOne(game: Game, config: OneConfig): Fu[String] =
    game.pgnImport ifTrue config.imported match {
      case Some(imported) => fuccess(imported.pgn)
      case None =>
        for {
          realPlayers                  <- config.playerFile.??(realPlayerApi.apply)
          (game, initialFen, analysis) <- enrich(config.flags)(game)
          export <- config.format match {
            case Format.JSON =>
              toJson(game, initialFen, analysis, config.flags, realPlayers = realPlayers) dmap Json.stringify
            case Format.PGN =>
              pgnDump(
                game,
                initialFen,
                analysis,
                config.flags,
                realPlayers = realPlayers
              ) dmap pgnDump.toPgnString
          }
        } yield export
    }

  private val fileR = """[\s,]""".r

  def filename(game: Game, format: Format): Fu[String] =
    gameLightUsers(game) map { case (wu, bu) =>
      fileR.replaceAllIn(
        "playstrategy_pgn_%s_%s_vs_%s.%s.%s".format(
          Tag.UTCDate.format.print(game.createdAt),
          pgnDump.dumper.player(game.p1Player, wu),
          pgnDump.dumper.player(game.p2Player, bu),
          game.id,
          format.toString.toLowerCase
        ),
        "_"
      )
    }

  def filename(tour: Tournament, format: Format): String =
    filename(tour, format.toString.toLowerCase)

  def filename(tour: Tournament, format: String): String =
    fileR.replaceAllIn(
      "playstrategy_tournament_%s_%s_%s.%s".format(
        Tag.UTCDate.format.print(tour.startsAt),
        tour.id,
        lila.common.String.slugify(tour.name),
        format
      ),
      "_"
    )

  def filename(swiss: lila.swiss.Swiss, format: Format): String =
    filename(swiss, format.toString.toLowerCase)

  def filename(swiss: lila.swiss.Swiss, format: String): String =
    fileR.replaceAllIn(
      "playstrategy_swiss_%s_%s_%s.%s".format(
        Tag.UTCDate.format.print(swiss.startsAt),
        swiss.id,
        lila.common.String.slugify(swiss.name),
        format
      ),
      "_"
    )

  def exportByUser(config: ByUserConfig): Source[String, _] =
    Source futureSource {
      config.playerFile.??(realPlayerApi.apply) map { realPlayers =>
        gameRepo
          .sortedCursor(
            config.vs.fold(Query.user(config.user.id)) { Query.opponents(config.user, _) } ++
              Query.createdBetween(config.since, config.until) ++
              (!config.ongoing).??(Query.finished),
            Query.sortCreated,
            batchSize = config.perSecond.value
          )
          .documentSource()
          .map(g => config.postFilter(g) option g)
          .throttle(config.perSecond.value * 10, 1 second, e => if (e.isDefined) 10 else 2)
          .mapConcat(_.toList)
          .take(config.max | Int.MaxValue)
          .via(upgradeOngoingGame)
          .via(preparationFlow(config, realPlayers))
          .keepAlive(keepAliveInterval, () => emptyMsgFor(config))
      }
    }

  def exportByIds(config: ByIdsConfig): Source[String, _] =
    Source futureSource {
      config.playerFile.??(realPlayerApi.apply) map { realPlayers =>
        gameRepo
          .sortedCursor(
            $inIds(config.ids),
            Query.sortCreated,
            batchSize = config.perSecond.value
          )
          .documentSource()
          .throttle(config.perSecond.value, 1 second)
          .via(upgradeOngoingGame)
          .via(preparationFlow(config, realPlayers))
      }
    }

  def exportByTournament(config: ByTournamentConfig): Source[String, _] =
    Source futureSource {
      tournamentRepo.isTeamBattle(config.tournamentId) map { isTeamBattle =>
        pairingRepo
          .sortedCursor(
            tournamentId = config.tournamentId,
            batchSize = config.perSecond.value
          )
          .documentSource()
          .grouped(config.perSecond.value)
          .throttle(1, 1 second)
          .mapAsync(1) { pairings =>
            isTeamBattle.?? {
              playerRepo.teamsOfPlayers(config.tournamentId, pairings.flatMap(_.users).distinct).dmap(_.toMap)
            } flatMap { playerTeams =>
              gameRepo.gameOptionsFromSecondary(pairings.map(_.gameId)) map {
                _.zip(pairings) collect { case (Some(game), pairing) =>
                  import cats.implicits._
                  (
                    game,
                    pairing,
                    (
                      playerTeams.get(pairing.user1),
                      playerTeams.get(
                        pairing.user2
                      )
                    ) mapN PlayerIndex.Map.apply[String]
                  )
                }
              }
            }
          }
          .mapConcat(identity)
          .mapAsync(4) { case (game, pairing, teams) =>
            enrich(config.flags)(game) dmap { (_, pairing, teams) }
          }
          .mapAsync(4) { case ((game, fen, analysis), pairing, teams) =>
            config.format match {
              case Format.PGN => pgnDump.formatter(config.flags)(game, fen, analysis, teams, none)
              case Format.JSON =>
                def addBerserk(playerIndex: PlayerIndex)(json: JsObject) =
                  if (pairing berserkOf playerIndex)
                    json deepMerge Json.obj(
                      "players" -> Json.obj(playerIndex.name -> Json.obj("berserk" -> true))
                    )
                  else json
                toJson(game, fen, analysis, config.flags, teams) dmap
                  addBerserk(P1) dmap
                  addBerserk(P2) dmap { json =>
                    s"${Json.stringify(json)}\n"
                  }
            }
          }
      }
    }

  def exportBySwiss(config: BySwissConfig): Source[String, _] =
    swissApi
      .gameIdSource(
        swissId = config.swissId,
        batchSize = config.perSecond.value
      )
      .grouped(config.perSecond.value)
      .throttle(1, 1 second)
      .mapAsync(1)(gameRepo.gamesFromSecondary)
      .mapConcat(identity)
      .mapAsync(4)(enrich(config.flags))
      .mapAsync(4) { case (game, fen, analysis) =>
        config.format match {
          case Format.PGN => pgnDump.formatter(config.flags)(game, fen, analysis, none, none)
          case Format.JSON =>
            toJson(game, fen, analysis, config.flags, None) dmap { json =>
              s"${Json.stringify(json)}\n"
            }
        }
      }

  private val upgradeOngoingGame =
    Flow[Game].mapAsync(4)(gameProxy.upgradeIfPresent)

  private def preparationFlow(config: Config, realPlayers: Option[RealPlayers]) =
    Flow[Game]
      .mapAsync(4)(enrich(config.flags))
      .mapAsync(4) { case (game, fen, analysis) =>
        formatterFor(config)(game, fen, analysis, None, realPlayers)
      }

  private def enrich(flags: WithFlags)(game: Game) =
    gameRepo initialFen game flatMap { initialFen =>
      (flags.evals ?? analysisRepo.byGame(game)) dmap {
        (game, initialFen, _)
      }
    }

  private def formatterFor(config: Config) =
    config.format match {
      case Format.PGN  => pgnDump.formatter(config.flags)
      case Format.JSON => jsonFormatter(config.flags)
    }

  private def emptyMsgFor(config: Config) =
    config.format match {
      case Format.PGN  => "\n"
      case Format.JSON => "{}\n"
    }

  private def jsonFormatter(flags: WithFlags) =
    (
        game: Game,
        initialFen: Option[FEN],
        analysis: Option[Analysis],
        teams: Option[GameTeams],
        realPlayers: Option[RealPlayers]
    ) =>
      toJson(game, initialFen, analysis, flags, teams, realPlayers) dmap { json =>
        s"${Json.stringify(json)}\n"
      }

  private def toJson(
      g: Game,
      initialFen: Option[FEN],
      analysisOption: Option[Analysis],
      withFlags: WithFlags,
      teams: Option[GameTeams] = None,
      realPlayers: Option[RealPlayers] = None
  ): Fu[JsObject] =
    for {
      lightUsers <- gameLightUsers(g) dmap { case (wu, bu) => List(wu, bu) }
      pgn <-
        withFlags.pgnInJson ?? pgnDump
          .apply(g, initialFen, analysisOption, withFlags, realPlayers = realPlayers)
          .dmap(pgnDump.toPgnString)
          .dmap(some)
    } yield Json
      .obj(
        "id"         -> g.id,
        "rated"      -> g.rated,
        "lib"        -> g.variant.gameLogic.id,
        "variant"    -> g.variant.key,
        "speed"      -> g.speed.key,
        "perf"       -> PerfPicker.key(g),
        "createdAt"  -> g.createdAt,
        "lastMoveAt" -> g.updatedAt,
        "status"     -> g.status.name,
        "players" -> JsObject(g.players zip lightUsers map { case (p, user) =>
          p.playerIndex.name -> Json
            .obj()
            .add("user", user)
            .add("rating", p.rating)
            .add("ratingDiff", p.ratingDiff)
            .add("name", p.name)
            .add("provisional" -> p.provisional)
            .add("aiLevel" -> p.aiLevel)
            .add("analysis" -> analysisOption.flatMap(analysisJson.player(g pov p.playerIndex)))
            .add("team" -> teams.map(_(p.playerIndex)))
        // .add("plyCentis" -> withFlags.plyTimes ?? g.plyTimes(p.playerIndex).map(_.map(_.centis)))
        })
      )
      .add("initialFen" -> initialFen)
      .add("multiMatch" -> g.metadata.multiMatchGameId)
      .add("winner" -> g.winnerPlayerIndex.map(_.name))
      .add("opening" -> g.opening.ifTrue(withFlags.opening))
      .add("moves" -> withFlags.turns.option {
        withFlags keepDelayIf g.playable applyDelay g.actionStrs.map(_.mkString(",")) mkString " "
      })
      .add("pgn" -> pgn)
      .add("daysPerTurn" -> g.daysPerTurn)
      .add("analysis" -> analysisOption.ifTrue(withFlags.evals).map(analysisJson.moves(_, withGlyph = false)))
      .add("tournament" -> g.tournamentId)
      .add("clock" -> g.clock.map { clock =>
        Json.obj(
          "initial" -> clock.limitSeconds,
          // TODO: We are reusing the 'increment' field for both increment and delay here which may be confusing in the future. [Bronstein]
          "increment" -> clock.graceSeconds,
          "totalTime" -> clock.estimateTotalSeconds
        )
      })

  private def gameLightUsers(game: Game): Fu[(Option[LightUser], Option[LightUser])] =
    (game.p1Player.userId ?? getLightUser) zip (game.p2Player.userId ?? getLightUser)
}

object GameApiV2 {

  sealed trait Format
  object Format {
    case object PGN  extends Format
    case object JSON extends Format
    def byRequest(req: play.api.mvc.RequestHeader) = if (HTTPRequest acceptsNdJson req) JSON else PGN
  }

  sealed trait Config {
    val format: Format
    val flags: WithFlags
  }

  case class OneConfig(
      format: Format,
      imported: Boolean,
      flags: WithFlags,
      playerFile: Option[String]
  ) extends Config

  case class ByUserConfig(
      user: User,
      vs: Option[User],
      format: Format,
      since: Option[DateTime] = None,
      until: Option[DateTime] = None,
      max: Option[Int] = None,
      rated: Option[Boolean] = None,
      perfType: Set[lila.rating.PerfType],
      analysed: Option[Boolean] = None,
      ongoing: Boolean = false,
      playerIndex: Option[PlayerIndex],
      flags: WithFlags,
      perSecond: MaxPerSecond,
      playerFile: Option[String]
  ) extends Config {
    def postFilter(g: Game) =
      rated.fold(true)(g.rated ==) && {
        perfType.isEmpty || g.perfType.exists(perfType.contains)
      } && playerIndex.fold(true) { c =>
        g.player(c).userId has user.id
      } && analysed.fold(true)(g.metadata.analysed ==)
  }

  case class ByIdsConfig(
      ids: Seq[Game.ID],
      format: Format,
      flags: WithFlags,
      perSecond: MaxPerSecond,
      playerFile: Option[String]
  ) extends Config

  case class ByTournamentConfig(
      tournamentId: Tournament.ID,
      format: Format,
      flags: WithFlags,
      perSecond: MaxPerSecond
  ) extends Config

  case class BySwissConfig(
      swissId: lila.swiss.Swiss.Id,
      format: Format,
      flags: WithFlags,
      perSecond: MaxPerSecond
  ) extends Config
}
