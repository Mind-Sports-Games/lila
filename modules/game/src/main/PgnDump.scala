package lila.game

import strategygames.chess.format.pgn.{ Parser, Pgn }
import strategygames.format.pgn.{ ParsedPgn, Tag, TagType, Tags }
import strategygames.format.{ FEN, Forsyth }
import strategygames.chess.format.{ pgn => chessPgn }
import strategygames.{ Centis, Color, GameLib, Status }
import strategygames.variant.Variant

import lila.common.config.BaseUrl
import lila.common.LightUser
import lila.common.Form

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
      teams: Option[Color.Map[String]] = None,
      hideRatings: Boolean = false
  ): Fu[Pgn] = {
    val imported = game.pgnImport.flatMap { pgni =>
      Parser.full(pgni.pgn).toOption
    }
    val algebraic = game.variant match {
      case Variant.Draughts(variant) => variant.boardSize.pos.hasAlgebraic// && flags.algebraic
      case _ => false
    }
    val tagsFuture =
      if (flags.tags) tags(
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
    tagsFuture map { ts =>
      val turns = flags.moves ?? {
        val fenSituation = ts.fen.flatMap{fen => Forsyth.<<<(game.variant.gameLib, fen)}
        makeTurns(
          game.variant match {
            case Variant.Draughts(variant) => {
              val pdnMovesFull = game.pdnMovesConcat(true, true)
              val pdnMoves = strategygames.draughts.Replay.unambiguousPdnMoves(
                pdnMoves = pdnMovesFull,
                initialFen = ts.fen match {
                  case Some(FEN.Draughts(fen)) => Some(fen)
                  case None => None
                  case _ => sys.error("invalid draughts fen in pgnDump")
                },
                variant = variant
              ).fold(
                  err => {
                    logger.warn(s"Could not unambiguate moves of ${game.id}: $err")
                    shortenMoves(pdnMovesFull)
                  },
                  moves => moves
                )
              val moves = flags keepDelayIf game.playable applyDelay pdnMoves
              val moves2 =
                if (algebraic) san2alg(moves, variant.boardSize.pos)
                else moves
              if (fenSituation.exists(_.situation.color.black)) ".." +: moves2
              else moves2
            }
            case _ => flags keepDelayIf game.playable applyDelay {
              if (fenSituation.exists(_.situation.color.black)) ".." +: game.pgnMoves
              else game.pgnMoves
            }
          },
          fenSituation.map(_.fullMoveNumber) | 1,
          flags.clocks ?? ~game.bothClockStates,
          game.startColor
        )
      }
      Pgn(ts, turns)
    }
  }

  private def shortenMoves(moves: Seq[String]) = moves map { move =>
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
      val capture = move.contains('x')
      val fields = if (capture) move.split("x") else move.split("-")
      val algebraicFields = fields.flatMap { boardPos.algebraic(_) }
      val sep = if (capture) "x" else "-"
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
    (game.whitePlayer.userId ?? { if (withProfileName) namedLightUser else lightUserApi.async}) zip (game.blackPlayer.userId ?? { if (withProfileName) namedLightUser else lightUserApi.async})
*/

  private def gameLightUsers(game: Game): Fu[(Option[LightUser], Option[LightUser])] =
    (game.whitePlayer.userId ?? lightUserApi.async) zip (game.blackPlayer.userId ?? lightUserApi.async)

  private def rating(p: Player) = p.rating.fold("?")(_.toString)

  def player(p: Player, u: Option[LightUser]) =
    p.aiLevel.fold(u.fold(p.name | lila.user.User.anonymous)(_.name))("playstrategy AI level " + _)

  private val customStartPosition: Set[Variant] =
    Set(strategygames.chess.variant.Chess960, strategygames.chess.variant.FromPosition, strategygames.chess.variant.Horde, strategygames.chess.variant.RacingKings).map(Variant.Chess)

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
      teams: Option[Color.Map[String]] = None,
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
          Tag(_.White, player(game.whitePlayer, wu)).some,
          Tag(_.Black, player(game.blackPlayer, bu)).some,
          Tag(_.Result, result(game, draughtsResult)).some,
          importedDate.isEmpty option Tag(
            _.UTCDate,
            imported.flatMap(_.tags(_.UTCDate)) | Tag.UTCDate.format.print(game.createdAt)
          ),
          importedDate.isEmpty option Tag(
            _.UTCTime,
            imported.flatMap(_.tags(_.UTCTime)) | Tag.UTCTime.format.print(game.createdAt)
          ),
          withRatings option Tag(_.WhiteElo, rating(game.whitePlayer)),
          withRatings option Tag(_.BlackElo, rating(game.blackPlayer)),
          withRatings ?? ratingDiffTag(game.whitePlayer, _.WhiteRatingDiff),
          withRatings ?? ratingDiffTag(game.blackPlayer, _.BlackRatingDiff),
          wu.flatMap(_.title).map { t =>
            Tag(_.WhiteTitle, t)
          },
          bu.flatMap(_.title).map { t =>
            Tag(_.BlackTitle, t)
          },
          teams.map { t => Tag("WhiteTeam", t.white) },
          teams.map { t => Tag("BlackTeam", t.black) },
          Tag(_.Variant, game.variant.name.capitalize).some,
          Tag.timeControl(game.clock.map(_.config)).some,
          game.metadata.microMatchGameId.map(gameId => Tag(_.MicroMatch, gameId)),
          Tag(_.ECO, game.opening.fold("?")(_.opening.eco)).some,
          withOpening option Tag(_.Opening, game.opening.fold("?")(_.opening.name)),
          Tag(
            _.Termination, {
              import Status._
              game.status match {
                case Created | Started                             => "Unterminated"
                case Aborted | NoStart                             => "Abandoned"
                case Timeout | Outoftime                           => "Time forfeit"
                case Resign | Draw | Stalemate | Mate | VariantEnd => "Normal"
                case Cheat                                         => "Rules infraction"
                case UnknownFinish                                 => "Unknown"
              }
            }
          ).some
        ).flatten ::: customStartPosition(game.variant).??(game.variant match {
          case Variant.Draughts(variant) => List(
            Tag(
              _.FEN,
              (initialFen match {
                case Some(FEN.Draughts(fen)) => Some(fen)
                case None => None
                case _ => sys.error("invalid draughts fen in pgnDump tags")
              }).flatMap { fen =>
                if (algebraic)
                  strategygames.draughts.format.Forsyth.toAlgebraic(
                    variant,
                    fen
                  )
                else fen.some
              }.fold("?")(f => strategygames.draughts.format.Forsyth.shorten(f.value))
              .map(FEN.Draughts)
            )
          )
          case _ => List(
            Tag(_.FEN, (initialFen | Forsyth.initial(game.variant.gameLib)).value),
            Tag("SetUp", "1")
          )
        })
      }
    }

  private def makeTurns(
      moves: Seq[String],
      from: Int,
      clocks: Vector[Centis],
      startColor: Color
  ): List[chessPgn.Turn] =
    (moves grouped 2).zipWithIndex.toList map { case (moves, index) =>
      val clockOffset = startColor.fold(0, 1)
      chessPgn.Turn(
        number = index + from,
        white = moves.headOption filter (".." !=) map { san =>
          chessPgn.Move(
            san = san,
            secondsLeft = clocks lift (index * 2 - clockOffset) map (_.roundSeconds)
          )
        },
        black = moves lift 1 map { san =>
          chessPgn.Move(
            san = san,
            secondsLeft = clocks lift (index * 2 + 1 - clockOffset) map (_.roundSeconds)
          )
        }
      )
    } filterNot (_.isEmpty)
}

object PgnDump {

  private val delayMovesBy         = 3
  private val delayKeepsFirstMoves = 5

  case class WithFlags(
      clocks: Boolean = true,
      moves: Boolean = true,
      tags: Boolean = true,
      evals: Boolean = true,
      opening: Boolean = true,
      literate: Boolean = false,
      pgnInJson: Boolean = false,
      delayMoves: Boolean = false
  ) {
    def applyDelay[M](moves: Seq[M]): Seq[M] =
      if (!delayMoves) moves
      else moves.take((moves.size - delayMovesBy) atLeast delayKeepsFirstMoves)

    def keepDelayIf(cond: Boolean) = copy(delayMoves = delayMoves && cond)
  }

  def result(game: Game, draughtsResult: Boolean) =
    if (game.finished) Color.showResult(game.winnerColor, draughtsResult)
    else "*"
}
