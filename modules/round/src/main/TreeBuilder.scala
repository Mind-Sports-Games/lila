package lila.round

import strategygames.{ Player => PlayerIndex, Centis, Game, GameLogic, Pos, Replay, Situation }
import strategygames.format.pgn.Glyphs
import strategygames.format.{ FEN, Forsyth, Uci, UciCharPair }
import strategygames.opening.{ FullOpening, FullOpeningDB }
import strategygames.variant.Variant
import JsonView.WithFlags
import lila.analyse.{ Advice, Analysis, Info }
import lila.tree._

object TreeBuilder {

  private type Ply       = Int
  private type OpeningOf = FEN => Option[FullOpening]

  private def makeEval(info: Info) =
    Eval(
      cp = info.cp,
      mate = info.mate,
      best = info.best
    )

  def fullOpeningOf(fen: FEN, variant: Variant, withFlags: WithFlags): Option[FullOpening] =
    if (withFlags.opening && Variant.openingSensibleVariants(variant.gameLogic)(variant))
      FullOpeningDB.findByFen(variant.gameLogic, fen)
    else None

  def apply(
      game: lila.game.Game,
      analysis: Option[Analysis],
      initialFen: FEN,
      withFlags: WithFlags
  ): Root = {
    val withClocks: Option[Vector[Centis]] = withFlags.clocks ?? game.bothClockStates
    val drawOfferTurnCount                 = game.drawOffers.normalizedTurns
    Replay.gameWithUciWhileValid(
      game.variant.gameLogic,
      game.actionStrs,
      game.startPlayerIndex,
      game.activePlayer,
      initialFen,
      game.variant
    ) match {
      case (init, games, error) =>
        error foreach logChessError(game.id)
        val fen                 = Forsyth.>>(game.variant.gameLogic, init)
        val infos: Vector[Info] = analysis.??(_.infos.toVector)
        val advices: Map[Ply, Advice] = analysis.??(_.advices.view.map { a =>
          a.ply -> a
        }.toMap)
        val root = Root(
          ply = init.plies,
          turnCount = init.turnCount,
          playedPlayerIndex = if (init.board.history.currentTurn.nonEmpty) init.player else !init.player,
          variant = game.variant,
          fen = fen,
          check = init.situation.check,
          captureLength = init.situation match {
            case Situation.Draughts(situation) => situation.allMovesCaptureLength.some
            case _                             => None
          },
          opening = fullOpeningOf(fen, game.variant, withFlags),
          clock = withClocks.flatMap(_.headOption),
          pocketData = init.situation.board.pocketData,
          eval = infos lift 0 map makeEval,
          dropsByRole = init.situation.dropsByRole
        )
        def makeBranch(index: Int, g: Game, m: Uci.WithSan) = {
          val fen    = Forsyth.>>(g.situation.board.variant.gameLogic, g)
          val info   = infos lift (index - 1)
          val advice = advices get g.plies
          val player = !g.situation.player
          val branch = Branch(
            id = UciCharPair(g.situation.board.variant.gameLogic, m.uci),
            ply = g.plies,
            turnCount = g.turnCount,
            playedPlayerIndex = if (g.board.history.currentTurn.nonEmpty) g.player else !g.player,
            variant = g.situation.board.variant,
            move = m,
            fen = fen,
            captureLength = (g.situation, m) match {
              case (Situation.Draughts(situation), Uci.DraughtsWithSan(uciMove)) =>
                if (situation.ghosts > 0) situation.captureLengthFrom(uciMove.uci.dest)
                else situation.allMovesCaptureLength.some
              case _ => None
            },
            check = g.situation.check,
            opening = fullOpeningOf(fen, g.situation.board.variant, withFlags),
            clock = withClocks flatMap (_ lift (g.plies - init.plies - 1)),
            pocketData = g.situation.board.pocketData,
            eval = info map makeEval,
            glyphs = Glyphs.fromList(advice.map(_.judgment.glyph).toList),
            dropsByRole = g.situation.dropsByRole,
            comments = Node.Comments {
              drawOfferTurnCount(g.turnCount)
                .option(
                  makePlayStrategyComment(
                    s"${g.situation.board.variant.playerNames(player)} offers draw"
                  )
                )
                .toList :::
                advice
                  .map(_.makeComment(withEval = false, withBestMove = true))
                  .toList
                  .map(makePlayStrategyComment)
            }
          )
          advices.get(g.plies + 1).flatMap { adv =>
            games.lift(index - 1).map { case (fromGame, _) =>
              withAnalysisChild(
                game.id,
                branch,
                game.variant,
                Forsyth.>>(game.variant.gameLogic, fromGame),
                withFlags
              )(adv.info)
            }
          } getOrElse branch
        }
        games.zipWithIndex.reverse match {
          case Nil => root
          case ((g, m), i) :: rest =>
            root prependChild rest.foldLeft(makeBranch(i + 1, g, m)) { case (node, ((g, m), i)) =>
              makeBranch(i + 1, g, m) prependChild node
            }
        }
    }
  }

  private def makePlayStrategyComment(text: String) =
    Node.Comment(
      Node.Comment.Id.make,
      Node.Comment.Text(text),
      Node.Comment.Author.PlayStrategy
    )

  private def withAnalysisChild(
      id: String,
      root: Branch,
      variant: Variant,
      fromFen: FEN,
      withFlags: WithFlags
  )(info: Info): Branch = {
    def makeBranch(g: Game, m: Uci.WithSan) = {
      val fen = Forsyth.>>(variant.gameLogic, g)
      Branch(
        id = UciCharPair(variant.gameLogic, m.uci),
        ply = g.plies,
        turnCount = g.turnCount,
        playedPlayerIndex = if (g.board.history.currentTurn.nonEmpty) g.player else !g.player,
        variant = variant,
        move = m,
        fen = fen,
        check = g.situation.check,
        opening = fullOpeningOf(fen, variant, withFlags),
        pocketData = g.situation.board.pocketData,
        dropsByRole = g.situation.dropsByRole,
        eval = none
      )
    }
    Replay.gameWithUciWhileValid(
      variant.gameLogic,
      info.variation.take(20),
      //TODO: Doublecheck: Think this is ok to handle like this
      PlayerIndex.P1,
      PlayerIndex.fromTurnCount(info.variation.take(20).size),
      fromFen,
      variant
    ) match {
      case (_, games, error) =>
        error foreach logChessError(id)
        games.reverse match {
          case Nil => root
          case (g, m) :: rest =>
            root addChild rest
              .foldLeft(makeBranch(g, m)) { case (node, (g, m)) =>
                makeBranch(g, m) addChild node
              }
              .setComp
        }
    }
  }

  private val logChessError = (id: String) =>
    (err: String) =>
      logger.warn(s"round.TreeBuilder https://playstrategy.org/$id ${err.linesIterator.toList.headOption}")
}
