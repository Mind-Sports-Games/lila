package lila.puzzle

import strategygames.format.{ FEN, Uci }
import reactivemongo.api.bson._
import scala.util.{ Success, Try }

import lila.db.BSON
import lila.db.dsl._
import lila.game.Game
import lila.rating.Glicko

import strategygames.{ GameFamily, GameLogic }

object BsonHandlers {

  implicit val PuzzleIdBSONHandler: BSONHandler[Puzzle.Id] = stringIsoHandler(Puzzle.idIso)

  import Puzzle.BSONFields._

  implicit private[puzzle] val PuzzleBSONReader: BSONDocumentReader[Puzzle] = new BSONDocumentReader[Puzzle] {
    def readDocument(r: BSONDocument) = for {
      id      <- r.getAsTry[Puzzle.Id](id)
      gameId  <- r.getAsTry[Game.ID](gameId)
      fen     <- r.getAsTry[String](fen)
      lineStr <- r.getAsTry[String](line)
      line <- lineStr
        .split(' ')
        .toList
        .flatMap { line => Uci.Move.apply(GameLogic.Chess(), GameFamily.Chess(), line) }
        .toNel
        .toTry("Empty move list?!")
      glicko <- r.getAsTry[Glicko](glicko)
      plays  <- r.getAsTry[Int](plays)
      vote   <- r.getAsTry[Float](vote)
      themes <- r.getAsTry[Set[PuzzleTheme.Key]](themes)
    } yield Puzzle(
      id = id,
      gameId = gameId,
      fen = FEN.apply(GameLogic.Chess(), fen),
      line = line,
      glicko = glicko,
      plays = plays,
      vote = vote,
      themes = themes
    )
  }

  implicit private[puzzle] val RoundIdHandler: BSONHandler[PuzzleRound.Id] = tryHandler[PuzzleRound.Id](
    { case BSONString(v) =>
      v split PuzzleRound.idSep match {
        case Array(userId, puzzleId) => Success(PuzzleRound.Id(userId, Puzzle.Id(puzzleId)))
        case _                       => handlerBadValue(s"Invalid puzzle round id $v")
      }
    },
    id => BSONString(id.toString)
  )

  implicit private[puzzle] val RoundThemeHandler: BSONHandler[PuzzleRound.Theme] =
    tryHandler[PuzzleRound.Theme](
      { case BSONString(v) =>
        PuzzleTheme
          .find(v.tail)
          .fold[Try[PuzzleRound.Theme]](handlerBadValue(s"Invalid puzzle round theme $v")) { theme =>
            Success(PuzzleRound.Theme(theme.key, v.head == '+'))
          }
      },
      rt => BSONString(s"${if (rt.vote) "+" else "-"}${rt.theme}")
    )

  implicit private[puzzle] val RoundHandler: BSON[PuzzleRound] = new BSON[PuzzleRound] {
    import PuzzleRound.BSONFields._
    def reads(r: BSON.Reader) = PuzzleRound(
      id = r.get[PuzzleRound.Id](id),
      win = r.bool(win),
      fixedAt = r.dateO(fixedAt),
      date = r.date(date),
      vote = r.intO(vote),
      themes = r.getsD[PuzzleRound.Theme](themes)
    )
    def writes(w: BSON.Writer, r: PuzzleRound) =
      $doc(
        id      -> r.id,
        win     -> r.win,
        fixedAt -> r.fixedAt,
        date    -> r.date,
        vote    -> r.vote,
        themes  -> w.listO(r.themes)
      )
  }

  implicit private[puzzle] val PathIdBSONHandler: BSONHandler[PuzzlePath.Id] = stringIsoHandler(
    PuzzlePath.pathIdIso
  )

  implicit private[puzzle] val ThemeKeyBSONHandler: BSONHandler[PuzzleTheme.Key] = stringIsoHandler(
    PuzzleTheme.keyIso
  )
}
