package lila.importer

import cats.data.Validated
import strategygames.chess.format.pgn.{ ParsedPgn, Parser }
import strategygames.format.pgn.{ Reader, Tag, TagType, Tags }
import strategygames.format.{ FEN, Forsyth }
import strategygames.{ Color, Game => StratGame, GameLib, Mode, Replay, Status }
import strategygames.variant.Variant
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
(GameLib.Chess())}

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
      case Reader.Result.ChessComplete(replay)         => Replay.Chess(replay)
      case Reader.Result.ChessIncomplete(replay, _)    => Replay.Chess(replay)
      case Reader.Result.DraughtsComplete(replay)      => Replay.Draughts(replay)
      case Reader.Result.DraughtsIncomplete(replay, _) => Replay.Draughts(replay)
    }

  private def preprocessReplay(replay: Replay, parsed: ParsedPgn, pgn: String): Validated[String, Preprocessed] = {
    val initBoard    = parsed.tags.fen
    val fromPosition = initBoard.nonEmpty && !parsed.tags.fen.exists(_.initial)
    val variant = (replay, {
      val chessVariant = parsed.tags.variant
      chessVariant | {
        if (fromPosition) Variant.libFromPosition(GameLib.Chess())
        else Variant.libStandard(GameLib.Chess())
      }
    }) match {
      case (Replay.Chess(replay), Variant.Chess(strategygames.chess.variant.Chess960))
        if !Chess960.isStartPosition(replay.setup.board) =>
          Variant.libFromPosition(GameLib.Chess())
      case (_, Variant.Chess(strategygames.chess.variant.FromPosition))
        if parsed.tags.fen.isEmpty
          => Variant.libStandard(GameLib.Chess())
      case (_, Variant.Chess(strategygames.chess.variant.Standard))
        if fromPosition
          => Variant.libFromPosition(GameLib.Chess())
      case (_, v) => v
    }
    val game = replay match {
      case Replay.Chess(replay) =>
        Replay.Chess(replay.state.copy(situation = replay.state.situation withVariant variant))
      case Replay.Draughts(replay) =>
        Replay.Draughts(replay.state.copy(situation = replay.state.situation withVariant variant))
    }
    val initialFen = parsed.tags.fen map{fen => Forsyth.>>(GameLib.Chess(), fen)}

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
        chess = StratGame.wrap(game),
        whitePlayer = Player.makeImported(
          Color.White,
          parsed.tags(_.White),
          parsed.tags(_.WhiteElo).flatMap(_.toIntOption)
        ),
        blackPlayer = Player.makeImported(
          Color.Black,
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
              case Some(color) => TagResult(status, color.some)
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

  //this doesnt exist in draughts
  def preprocess(user: Option[String]): Validated[String, Preprocessed] = ImporterForm.catchOverflow { () =>
    Parser.full(pgn) flatMap { parsed =>
      Reader.fullWithSans(
        GameLib.Chess(),
        pgn,
        sans => sans.copy(value = sans.value take maxPlies),
        Tags.empty
      ) map evenIncomplete map {
        case Replay.Chess(replay) => preprocessReplay(Replay.Chess(replay), parsed, pgn)
        case Replay.Draughts(replay) => preprocessReplay(Replay.Draughts(replay), parsed, pgn)
      }
    }
  }
}
