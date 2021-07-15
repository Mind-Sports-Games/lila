package lila.importer

import cats.data.Validated
import strategygames.chess.format.pgn.{ ParsedPgn, Parser, Reader }
import strategygames.format.pgn.{ Tag, TagType, Tags }
import strategygames.chess.format.{ FEN, Forsyth }
import strategygames.chess.{ Color, Replay }
import strategygames.{ Mode, Status }
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

private case class TagResult(status: Status, winner: Option[Color])
case class Preprocessed(
    game: NewGame,
    replay: Replay,
    initialFen: Option[FEN],
    parsed: ParsedPgn
)

case class ImportData(pgn: String, analyse: Option[String]) {

  private type TagPicker = Tag.type => TagType

  private val maxPlies = 600

  private def evenIncomplete(result: Reader.Result): Replay =
    result match {
      case Reader.Result.Complete(replay)      => replay
      case Reader.Result.Incomplete(replay, _) => replay
    }

  def preprocess(user: Option[String]): Validated[String, Preprocessed] = ImporterForm.catchOverflow { () =>
    Parser.full(pgn) flatMap { parsed =>
      Reader.fullWithSans(
        pgn,
        sans => sans.copy(value = sans.value take maxPlies),
        Tags.empty
      ) map evenIncomplete map { case replay @ Replay(setup, _, state) =>
        val initBoard    = parsed.tags.fen match {
            case Some(strategygames.format.FEN.Chess(fen)) => Forsyth.<<(fen).map(_.board)
            case None => None
            case _ => sys.error("Not implemented for draughts yet")
        }
        val fromPosition = initBoard.nonEmpty && !parsed.tags.fen.exists(_.initial)
        val variant = {
          val chessVariant = parsed.tags.variant match {
            case Some(strategygames.variant.Variant.Chess(variant)) => Some(variant)
            case None => None
            case _ => sys.error("Not implemented for draughts yet")
          }
          chessVariant | {
            if (fromPosition) strategygames.chess.variant.FromPosition
            else strategygames.chess.variant.Standard
          }
        } match {
          case strategygames.chess.variant.Chess960 if !Chess960.isStartPosition(setup.board) =>
            strategygames.chess.variant.FromPosition
          case strategygames.chess.variant.FromPosition if parsed.tags.fen.isEmpty => strategygames.chess.variant.Standard
          case strategygames.chess.variant.Standard if fromPosition                => strategygames.chess.variant.FromPosition
          case v                                                     => v
        }
        val game = state.copy(situation = state.situation withVariant variant)
        val initialFen = (parsed.tags.fen match {
          case Some(strategygames.format.FEN.Chess(fen)) => Forsyth.<<<@(variant, fen)
          case None => None
          case _ => sys.error("Not implemented for draughts yet")
        }) map Forsyth.>>

        val status = parsed.tags(_.Termination).map(_.toLowerCase) match {
          case Some("normal") | None                   => Status.Resign
          case Some("abandoned")                       => Status.Aborted
          case Some("time forfeit")                    => Status.Outoftime
          case Some("rules infraction")                => Status.Cheat
          case Some(txt) if txt contains "won on time" => Status.Outoftime
          case Some(_)                                 => Status.UnknownFinish
        }

        val date = parsed.tags.anyDate

        val dbGame = Game
          .make(
            chess = game,
            whitePlayer = Player.makeImported(
              strategygames.chess.White,
              parsed.tags(_.White),
              parsed.tags(_.WhiteElo).flatMap(_.toIntOption)
            ),
            blackPlayer = Player.makeImported(
              strategygames.chess.Black,
              parsed.tags(_.Black),
              parsed.tags(_.BlackElo).flatMap(_.toIntOption)
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
              parsed.tags.resultColor
                .map {
                  case Some(strategygames.Color.Chess(color)) => TagResult(status, color.some)
                  case None if status == Status.Outoftime => TagResult(status, none)
                  case None                               => TagResult(Status.Draw, none)
                  case _ => sys.error("Not implemented for draughts yet")
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
