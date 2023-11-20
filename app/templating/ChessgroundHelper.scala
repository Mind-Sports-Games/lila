package lila.app
package templating

//TODO: Muddled Pos here, chess specific stuff in here with ranks/files
import strategygames.Pos
import strategygames.chess
import strategygames.draughts
import strategygames.{ Board, Player => PlayerIndex, History }
import lila.api.Context

import lila.app.ui.ScalatagsTemplate._
import lila.game.Pov

trait ChessgroundHelper {

  private val cgWrap      = div(cls := "cg-wrap")
  private val cgHelper    = tag("cg-helper")
  private val cgContainer = tag("cg-container")
  private val cgBoard     = tag("cg-board")
  val cgWrapContent       = cgHelper(cgContainer(cgBoard))

  def chessground(board: Board, orient: PlayerIndex, lastMove: List[Pos] = Nil)(implicit ctx: Context): Frag =
    wrap {
      cgBoard {
        raw {
          if (ctx.pref.is3d) ""
          else {
            def top(p: Pos) = p match {
              case Pos.Chess(p)        => orient.fold(7 - p.rank.index, p.rank.index) * 12.5
              case Pos.FairySF(p)      => orient.fold(7 - p.rank.index, p.rank.index) * 12.5
              case Pos.Samurai(p)      => orient.fold(7 - p.rank.index, p.rank.index) * 12.5
              case Pos.Togyzkumalak(p) => orient.fold(7 - p.rank.index, p.rank.index) * 12.5
              case Pos.Go(p)           => orient.fold(7 - p.rank.index, p.rank.index) * 12.5
              case Pos.Backgammon(p)   => orient.fold(7 - p.rank.index, p.rank.index) * 12.5
              case _                   => sys.error("Invalid Pos type")
            }
            def left(p: Pos) = p match {
              case Pos.Chess(p)        => orient.fold(p.file.index, 7 - p.file.index) * 12.5
              case Pos.FairySF(p)      => orient.fold(p.file.index, 7 - p.file.index) * 12.5
              case Pos.Samurai(p)      => orient.fold(p.file.index, 7 - p.file.index) * 12.5
              case Pos.Togyzkumalak(p) => orient.fold(p.file.index, 7 - p.file.index) * 12.5
              case Pos.Go(p)           => orient.fold(p.file.index, 7 - p.file.index) * 12.5
              case Pos.Backgammon(p)   => orient.fold(p.file.index, 7 - p.file.index) * 12.5
              case _                   => sys.error("Invalid Pos type")
            }
            val highlights = ctx.pref.highlight ?? lastMove.distinct.map { pos =>
              s"""<square class="last-move" style="top:${top(pos)}%;left:${left(pos)}%"></square>"""
            } mkString ""
            val pieces =
              if (ctx.pref.isBlindfold) ""
              else
                //note this doesnt seem to be used although it is passed through on round creation
                board.pieces.map { case (pos, (piece, count)) =>
                  val klass =
                    if (count > 1) s"${piece.player.name} ${piece.role.name}${count}"
                    else s"${piece.player.name} ${piece.role.name}"
                  s"""<piece class="$klass" style="top:${top(pos)}%;left:${left(pos)}%"></piece>"""
                } mkString ""
            s"$highlights$pieces"
          }
        }
      }
    }

  def draughtsground(board: draughts.Board, orient: PlayerIndex, lastMove: List[draughts.Pos] = Nil)(implicit
      ctx: Context
  ): Frag = wrap {
    cgBoard {
      raw {
        def addX(p: draughts.PosMotion) = if (p.y % 2 != 0) -0.5 else -1.0
        def top(p: draughts.PosMotion)  = orient.fold(p.y - 1, 10 - p.y) * 10.0
        def left(p: draughts.PosMotion) = orient.fold(addX(p) + p.x, 4.5 - (addX(p) + p.x)) * 20.0
        val highlights = ctx.pref.highlight ?? lastMove.distinct.map { pos =>
          val pm = board.posAt(pos)
          s"""<square class="last-move" style="top:${top(pm)}%;left:${left(pm)}%"></square>"""
        } mkString ""
        val pieces =
          if (ctx.pref.isBlindfold) ""
          else
            board.pieces.map { case (pos, piece) =>
              val klass = s"${piece.player.name} ${piece.role.name}"
              val pm    = board.posAt(pos)
              s"""<piece class="$klass" style="top:${top(pm)}%;left:${left(pm)}%"></piece>"""
            } mkString ""
        s"$highlights$pieces"
      }
    }
  }

  def chessground(pov: Pov)(implicit ctx: Context): Frag =
    (pov.game.board, pov.game.history) match {
      case (board: Board.Chess, history: History.Chess) =>
        chessground(
          board = board,
          orient = pov.playerIndex,
          lastMove = history.lastMove.map(_.origDest) ?? { case (orig, dest) =>
            List(orig, dest)
          }
        )
      //is there a better way of duplicating the case for Chess/FairySF?
      case (board: Board.FairySF, history: History.FairySF) =>
        chessground(
          board = board,
          orient = pov.playerIndex,
          lastMove = history.lastMove.map(_.origDest) ?? { case (orig, dest) =>
            List(orig, dest)
          }
        )
      case (board: Board.Samurai, history: History.Samurai) =>
        chessground(
          board = board,
          orient = pov.playerIndex,
          lastMove = history.lastMove.map(_.origDest) ?? { case (orig, dest) =>
            List(orig, dest)
          }
        )
      case (board: Board.Togyzkumalak, history: History.Togyzkumalak) =>
        chessground(
          board = board,
          orient = pov.playerIndex,
          lastMove = history.lastMove.map(_.origDest) ?? { case (orig, dest) =>
            List(orig, dest)
          }
        )
      case (board: Board.Go, history: History.Go) =>
        chessground(
          board = board,
          orient = pov.playerIndex,
          lastMove = history.lastMove.map(_.origDest) ?? { case (orig, dest) =>
            List(orig, dest)
          }
        )
      case (board: Board.Backgammon, history: History.Backgammon) =>
        chessground(
          board = board,
          orient = pov.playerIndex,
          lastMove = history.lastMove.map(_.origDest) ?? { case (orig, dest) =>
            List(orig, dest)
          }
        )
      case (Board.Draughts(board), History.Draughts(history)) =>
        draughtsground(
          board = board,
          orient = pov.playerIndex,
          lastMove = history.lastMove.map(_.origDest) ?? { case (orig, dest) =>
            List(orig, dest)
          }
        )
      case _ => sys.error("Mismatched board and history")
    }

  private def wrap(content: Frag): Frag =
    cgWrap {
      cgHelper {
        cgContainer {
          content
        }
      }
    }

  lazy val chessgroundBoard = wrap(cgBoard)
}
