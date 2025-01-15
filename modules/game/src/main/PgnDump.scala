package lila.game

import strategygames.chess.format.pgn.{ Parser }
import strategygames.format.pgn.{ FullTurn, ParsedPgn, Pgn, Tag, TagType, Tags, Turn }
import strategygames.format.{ FEN, Forsyth }
import strategygames.{ ActionStrs, Centis, Player => PlayerIndex, GameLogic, Status }
import strategygames.variant.Variant

import lila.common.config.BaseUrl
import lila.common.LightUser
import lila.common.Form
import lila.i18n.VariantKeys

final class PgnDump(
    baseUrl: BaseUrl,
    lightUserApi: lila.user.LightUserApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  import PgnDump._

  //TODO: For draughts PgnDump to work as it does in lidraughts
  //the extra flag fields commented out below need to be set
  def apply(
      game: Game,
      initialFen: Option[FEN],
      flags: WithFlags,
      teams: Option[PlayerIndex.Map[String]] = None,
      hideRatings: Boolean = false
  ): Fu[Pgn] = {
    val imported = game.pgnImport.flatMap { pgni =>
      Parser.full(pgni.pgn).toOption
    }
    val algebraic = game.variant match {
      case Variant.Draughts(variant) => variant.boardSize.pos.hasAlgebraic // && flags.algebraic
      case _                         => false
    }
    val tagsFuture =
      if (flags.tags)
        tags(
          game,
          initialFen,
          imported,
          withOpening = flags.opening,
          //draughtsResult = flags.draughtsResult, //Need to set this elsewhere in lila
          algebraic = algebraic,
          //withProfileName = flags.profileName, //Need to set this elsewhere in lila
          withRatings = !hideRatings,
          teams = teams
        )
      else fuccess(Tags(Nil))
    tagsFuture map { tags =>
      val fullTurns = flags.turns ?? {
        val fenSituation = tags.fen.flatMap { fen => Forsyth.<<<(game.variant.gameLogic, fen) }
        makeFullTurns(
          game.variant match {
            case Variant.Draughts(variant) => {
              val pliesFull = game.draughtsActionStrsConcat(true, true).flatten
              val plies = strategygames.draughts.Replay
                .unambiguousPdnMoves(
                  pdnMoves = pliesFull,
                  initialFen = tags.fen match {
                    case Some(FEN.Draughts(fen)) => Some(fen)
                    case None                    => None
                    case _                       => sys.error("invalid draughts fen in pgnDump")
                  },
                  variant = variant
                  //TODO: draughts, this used to be a Valid[List[String]] type
                  //and now we have lost the error. Perhaps we need to reconsider this
                )
                .fold(shortenDraughtsMoves(pliesFull))(moves => moves)
              val delayedPlies = flags keepDelayIf game.playable applyDelay plies
              val algPlies =
                if (algebraic) san2alg(delayedPlies, variant.boardSize.pos)
                else delayedPlies
              val offsetPlies =
                if (fenSituation.exists(_.situation.player.p2)) ".." +: algPlies
                else algPlies
              offsetPlies.toVector.map(Vector(_))
            }
            case _ =>
              (flags keepDelayIf game.playable applyDelay {
                if (fenSituation.exists(_.situation.player.p2)) Vector("..") +: game.actionStrs
                else game.actionStrs
              }).toVector
          },
          fenSituation.map(_.fullTurnCount) | 1,
          flags.clocks ?? ~game.bothClockStates,
          game.startPlayerIndex
        )
      }
      Pgn(tags, fullTurns)
    }
  }

  private def shortenDraughtsMoves(moves: Seq[String]) = moves map { move =>
    val x1 = move.indexOf("x")
    if (x1 == -1) move
    else {
      val x2 = move.lastIndexOf("x")
      if (x2 == x1 || x2 == -1) move
      else move.slice(0, x1) + move.slice(x2, move.length)
    }
  }

  private def san2alg(moves: Seq[String], boardPos: strategygames.draughts.BoardPos) =
    moves map { move =>
      val capture         = move.contains('x')
      val fields          = if (capture) move.split("x") else move.split("-")
      val algebraicFields = fields.flatMap { boardPos.algebraic(_) }
      val sep             = if (capture) "x" else "-"
      algebraicFields mkString sep
    }

  private def gameUrl(id: String) = s"$baseUrl/$id"

  //TODO figure out how this works for Draughts to replicate lidraughts functionality
  /*private def namedLightUser(userId: String) =
    lila.user.UserRepo.byId(userId) map {
      _ ?? { u =>
        LightUser(
          id = u.id,
          name = u.profile.flatMap(_.nonEmptyRealName).fold(u.username)(n => s"$n (${u.username})"),
          title = u.title.map(_.value),
          isPatron = u.plan.active
        ).some
      }
    }

  private def gameLightUsers(game: Game, withProfileName: Boolean): Fu[(Option[LightUser], Option[LightUser])] =
    (game.p1Player.userId ?? { if (withProfileName) namedLightUser else lightUserApi.async}) zip (game.p2Player.userId ?? { if (withProfileName) namedLightUser else lightUserApi.async})
   */

  private def gameLightUsers(game: Game): Fu[(Option[LightUser], Option[LightUser])] =
    (game.p1Player.userId ?? lightUserApi.async) zip (game.p2Player.userId ?? lightUserApi.async)

  private def rating(p: Player) = p.rating.fold("?")(_.toString)

  def player(p: Player, u: Option[LightUser]) =
    p.aiLevel.fold(u.fold(p.name | lila.user.User.anonymous)(_.name))("playstrategy AI level " + _)

  private val customStartPosition: Set[Variant] =
    strategygames.chess.variant.Variant.all
      .filter(
        !_.standardInitialPosition
      )
      .map(Variant.Chess)
      .toSet

  private def eventOf(game: Game) = {
    val perf = game.perfType.fold("Standard")(_.trans(lila.i18n.defaultLang))
    game.tournamentId.map { id =>
      s"${game.mode} $perf tournament https://playstrategy.org/tournament/$id"
    } orElse game.simulId.map { id =>
      s"$perf simul https://playstrategy.org/simul/$id"
    } getOrElse {
      s"${game.mode} $perf game"
    }
  }

  private def ratingDiffTag(p: Player, tag: Tag.type => TagType) =
    p.ratingDiff.map { rd =>
      Tag(tag(Tag), s"${if (rd >= 0) "+" else ""}$rd")
    }

  def tags(
      game: Game,
      initialFen: Option[FEN],
      imported: Option[ParsedPgn],
      withOpening: Boolean,
      teams: Option[PlayerIndex.Map[String]] = None,
      draughtsResult: Boolean = false,
      algebraic: Boolean = false,
      withProfileName: Boolean = false,
      withRatings: Boolean = true
  ): Fu[Tags] =
    gameLightUsers(game) map { case (wu, bu) =>
      Tags {
        val importedDate = imported.flatMap(_.tags(_.Date))
        List[Option[Tag]](
          Tag(
            _.Event,
            imported.flatMap(_.tags(_.Event)) | { if (game.imported) "Import" else eventOf(game) }
          ).some,
          Tag(_.Site, gameUrl(game.id)).some,
          Tag(_.Date, importedDate | Tag.UTCDate.format.print(game.createdAt)).some,
          imported.flatMap(_.tags(_.Round)).map(Tag(_.Round, _)),
          Tag(_.P1, player(game.p1Player, wu)).some,
          Tag(_.P2, player(game.p2Player, bu)).some,
          Tag(_.Result, result(game, draughtsResult)).some,
          importedDate.isEmpty option Tag(
            _.UTCDate,
            imported.flatMap(_.tags(_.UTCDate)) | Tag.UTCDate.format.print(game.createdAt)
          ),
          importedDate.isEmpty option Tag(
            _.UTCTime,
            imported.flatMap(_.tags(_.UTCTime)) | Tag.UTCTime.format.print(game.createdAt)
          ),
          withRatings option Tag(_.P1Elo, rating(game.p1Player)),
          withRatings option Tag(_.P2Elo, rating(game.p2Player)),
          withRatings ?? ratingDiffTag(game.p1Player, _.P1RatingDiff),
          withRatings ?? ratingDiffTag(game.p2Player, _.P2RatingDiff),
          wu.flatMap(_.title).map { t =>
            Tag(_.P1Title, t)
          },
          bu.flatMap(_.title).map { t =>
            Tag(_.P2Title, t)
          },
          teams.map { t => Tag("P1Team", t.p1) },
          teams.map { t => Tag("P2Team", t.p2) },
          Tag(_.Variant, VariantKeys.variantName(game.variant).capitalize).some,
          Tag.timeControl(game.clock.map(_.config)).some,
          game.metadata.multiMatchGameId.map(gameId => Tag(_.MultiMatch, gameId)),
          Tag(_.ECO, game.opening.fold("?")(_.opening.eco)).some,
          withOpening option Tag(_.Opening, game.opening.fold("?")(_.opening.name)),
          Tag(
            _.Termination, {
              import Status._
              game.status match {
                case Created | Started                                           => "Unterminated"
                case Aborted | NoStart                                           => "Abandoned"
                case Timeout | Outoftime | OutoftimeGammon | OutoftimeBackgammon => "Time forfeit"
                case RuleOfGin | GinGammon | GinBackgammon                       => "Rule of Gin"
                case Resign | ResignGammon | ResignBackgammon | CubeDropped | Draw | Stalemate | Mate |
                    PerpetualCheck | VariantEnd | SingleWin | GammonWin | BackgammonWin =>
                  "Normal"
                case Cheat         => "Rules infraction"
                case UnknownFinish => "Unknown"
              }
            }
          ).some
        ).flatten ::: customStartPosition(game.variant).??(game.variant match {
          case Variant.Draughts(variant) =>
            List(
              Tag(
                _.FEN,
                (initialFen match {
                  case Some(FEN.Draughts(fen)) => Some(fen)
                  case None                    => None
                  case _                       => sys.error("invalid draughts fen in pgnDump tags")
                }).flatMap { fen =>
                  if (algebraic)
                    strategygames.draughts.format.Forsyth.toAlgebraic(
                      variant,
                      fen
                    )
                  else fen.some
                }.fold("?")(f => strategygames.draughts.format.Forsyth.shorten(f).value)
              )
            )
          case _ =>
            List(
              Tag(_.FEN, (initialFen | game.variant.initialFen).value),
              Tag("SetUp", "1")
            )
        })
      }
    }

  private def makeFullTurns(
      actionStrs: ActionStrs,
      from: Int,
      clocks: Vector[Centis],
      startPlayerIndex: PlayerIndex
  ): List[FullTurn] =
    Centis
      .withActionStrs(clocks, actionStrs, startPlayerIndex)
      .toSeq
      .grouped(2)
      .toList
      .zipWithIndex
      .map {
        case (actionStrsWithClock, index) => {
          val p1 = actionStrsWithClock.headOption
          val p2 = actionStrsWithClock.lift(1)
          FullTurn(
            fullTurnNumber = index + from,
            p1 = p1.map(_._1.mkString(",")).filter(".." !=).map { san =>
              Turn(
                san = san,
                secondsLeft = p1.flatMap(_._2).map(_.roundSeconds)
              )
            },
            p2 = p2.map(_._1.mkString(",")).map { san =>
              Turn(
                san = san,
                secondsLeft = p2.flatMap(_._2).map(_.roundSeconds)
              )
            }
          )
        }
      } filterNot (_.isEmpty)
}

object PgnDump {

  private val delayTurnsBy         = 3
  private val delayKeepsFirstTurns = 5

  case class WithFlags(
      clocks: Boolean = true,
      turns: Boolean = true,
      tags: Boolean = true,
      evals: Boolean = true,
      opening: Boolean = true,
      literate: Boolean = false,
      pgnInJson: Boolean = false,
      delayTurns: Boolean = false
  ) {
    def applyDelay[M](actionStrs: Seq[M]): Seq[M] =
      if (!delayTurns) actionStrs
      else actionStrs.take((actionStrs.size - delayTurnsBy) atLeast delayKeepsFirstTurns)

    def keepDelayIf(cond: Boolean) = copy(delayTurns = delayTurns && cond)
  }

  def result(game: Game, draughtsResult: Boolean) =
    if (game.finished) PlayerIndex.showResult(game.winnerPlayerIndex, draughtsResult)
    else "*"
}
