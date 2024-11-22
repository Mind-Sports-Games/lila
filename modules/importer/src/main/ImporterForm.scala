package lila.importer

import cats.data.Validated
// TODO: For post mso tournament, get Parser refactored so we can parse draughts games.
import strategygames.chess.format.pgn.{ Parser }
import strategygames.format.pgn.{ ParsedPgn, Reader, Tag, TagType, Tags }
import strategygames.format.{ FEN, Forsyth }
import strategygames.variant.{ Variant => StratVariant }
import strategygames.{ Board, Player => PlayerIndex, GameLogic, Mode, Replay, Status }
import play.api.data._
import play.api.data.Forms._
import scala.util.chaining._

import lila.game._

final class ImporterForm {

  lazy val importForm = Form(
    mapping(
      "pgn"     -> nonEmptyText.verifying("invalidPgn", p => checkPgn(p).isValid),
      "analyse" -> optional(nonEmptyText)
    )(ImportData.apply)(ImportData.unapply)
  )

  def checkPgn(pgn: String): Validated[String, Preprocessed] = ImporterForm.catchOverflow { () =>
    ImportData(pgn, none).preprocess(none)
  }
}

object ImporterForm {

  def catchOverflow(f: () => Validated[String, Preprocessed]): Validated[String, Preprocessed] = try {
    f()
  } catch {
    case e: RuntimeException if e.getMessage contains "StackOverflowError" =>
      Validated.Invalid("This PGN seems too long or too complex!")
  }
}

private case class TagResult(status: Status, winner: Option[PlayerIndex])
case class Preprocessed(
    game: NewGame,
    replay: Replay,
    initialFen: Option[FEN],
    parsed: ParsedPgn
)

case class ImportData(pgn: String, analyse: Option[String]) {

  private type TagPicker = Tag.type => TagType

  private val maxPlies = 1000

  private def evenIncomplete(result: Reader.Result): Replay =
    result match {
      case Reader.Result.ChessComplete(replay)             => Replay.Chess(replay)
      case Reader.Result.ChessIncomplete(replay, _)        => Replay.Chess(replay)
      case Reader.Result.DraughtsComplete(replay)          => Replay.Draughts(replay)
      case Reader.Result.DraughtsIncomplete(replay, _)     => Replay.Draughts(replay)
      case Reader.Result.FairySFComplete(replay)           => Replay.FairySF(replay)
      case Reader.Result.FairySFIncomplete(replay, _)      => Replay.FairySF(replay)
      case Reader.Result.SamuraiComplete(replay)           => Replay.Samurai(replay)
      case Reader.Result.SamuraiIncomplete(replay, _)      => Replay.Samurai(replay)
      case Reader.Result.TogyzkumalakComplete(replay)      => Replay.Togyzkumalak(replay)
      case Reader.Result.TogyzkumalakIncomplete(replay, _) => Replay.Togyzkumalak(replay)
      case Reader.Result.GoComplete(replay)                => Replay.Go(replay)
      case Reader.Result.GoIncomplete(replay, _)           => Replay.Go(replay)
      case Reader.Result.BackgammonComplete(replay)        => Replay.Backgammon(replay)
      case Reader.Result.BackgammonIncomplete(replay, _)   => Replay.Backgammon(replay)
      case Reader.Result.AbaloneComplete(replay)           => Replay.Abalone(replay)
      case Reader.Result.AbaloneIncomplete(replay, _)      => Replay.Abalone(replay)
    }

  def preprocess(user: Option[String]): Validated[String, Preprocessed] = ImporterForm.catchOverflow { () =>
    Parser.full(pgn) flatMap { parsed =>
      Reader.fullWithSans(
        GameLogic.Chess(),
        pgn,
        sans => sans.copy(value = sans.value take maxPlies),
        Tags.empty
      ) map evenIncomplete map { case replay: Replay =>
        val setup = replay.setup
        val state = replay.state
        val board = setup.board match {
          case Board.Chess(board) => board
          case _                  => sys.error("Importer doesn't support draughts yet")
        }
        val initBoard    = parsed.tags.fen.map(fen => Forsyth.<<(GameLogic.Chess(), fen).map(_.board))
        val fromPosition = initBoard.nonEmpty && !parsed.tags.fen.exists(_.initial)
        val variant = StratVariant.wrap({
          val chessVariant = parsed.tags.variant match {
            case Some(strategygames.variant.Variant.Chess(variant)) => Some(variant)
            case None                                               => None
            case _                                                  => sys.error("Not implemented for draughts yet")
          }
          chessVariant | {
            if (fromPosition) strategygames.chess.variant.FromPosition
            else strategygames.chess.variant.Standard
          }
        } match {
          case strategygames.chess.variant.Chess960 if !Chess960.isStartPosition(board) =>
            strategygames.chess.variant.FromPosition
          case strategygames.chess.variant.FromPosition if parsed.tags.fen.isEmpty =>
            strategygames.chess.variant.Standard
          case strategygames.chess.variant.Standard if fromPosition =>
            strategygames.chess.variant.FromPosition
          case v => v
        })
        val game = state.copy(situation = state.situation withVariant variant)
        val initialFen = parsed.tags.fen
          .flatMap(fen => Forsyth.<<<@(GameLogic.Chess(), variant, fen))
          .map(situation => Forsyth.>>(GameLogic.Chess(), situation))

        val status = parsed.tags(_.Termination).map(_.toLowerCase) match {
          case Some("normal") | None                   => Status.Resign
          case Some("abandoned")                       => Status.Aborted
          case Some("time forfeit")                    => Status.Outoftime
          case Some("rule of gin")                     => Status.RuleOfGin
          case Some("rules infraction")                => Status.Cheat
          case Some(txt) if txt contains "won on time" => Status.Outoftime
          case Some(_)                                 => Status.UnknownFinish
        }

        val date = parsed.tags.anyDate

        val dbGame = Game
          .make(
            stratGame = game,
            p1Player = Player.makeImported(
              PlayerIndex.P1,
              parsed.tags(_.P1),
              parsed.tags(_.P1Elo).flatMap(_.toIntOption)
            ),
            p2Player = Player.makeImported(
              PlayerIndex.P2,
              parsed.tags(_.P2),
              parsed.tags(_.P2Elo).flatMap(_.toIntOption)
            ),
            mode = Mode.Casual,
            source = Source.Import,
            pgnImport = PgnImport.make(user = user, date = date, pgn = pgn).some
          )
          .sloppy
          .start pipe { dbGame =>
          // apply the result from the board or the tags
          game.situation.status match {
            case Some(situationStatus) => dbGame.finish(situationStatus, game.situation.winner).game
            case None =>
              parsed.tags.resultPlayer
                .map {
                  case Some(playerIndex)                       => TagResult(status, playerIndex.some)
                  case None if Status.flagged.contains(status) => TagResult(status, none)
                  case None                                    => TagResult(Status.Draw, none)
                  case _                                       => sys.error("Not implemented for draughts yet")
                }
                .filter(_.status > Status.Started)
                .fold(dbGame) { res =>
                  dbGame.finish(res.status, res.winner).game
                }
          }
        }

        Preprocessed(NewGame(dbGame), replay.copy(state = game), initialFen, parsed)
      }
    }
  }
}
