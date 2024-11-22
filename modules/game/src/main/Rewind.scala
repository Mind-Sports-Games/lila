package lila.game

import cats.data.Validated
import strategygames.GameLogic
import strategygames.format.{ FEN }
import strategygames.format.pgn.{ Reader, Sans, Tag, Tags }
import org.joda.time.DateTime
import lila.i18n.VariantKeys

object Rewind {

  private def createTags(fen: Option[FEN], game: Game) = {
    val variantTag = Some(Tag(_.Variant, VariantKeys.variantName(game.variant)))
    val fenTag     = fen.map(f => Tag(_.FEN, f.value))

    Tags(List(variantTag, fenTag).flatten)
  }

  //takeback
  def apply(game: Game, initialFen: Option[FEN], rewindPly: Boolean): Validated[String, Progress] =
    (game.variant.gameLogic match {
      case GameLogic.Chess() | GameLogic.Draughts() =>
        Reader
          .replayResultFromActionStrsUsingSan(
            game.variant.gameLogic,
            actionStrs = game.actionStrs,
            //this is ok as sans uses a flattened version of actionStrs safely
            op = sans => Sans(sans.value.dropRight(1)),
            tags = createTags(initialFen, game)
          )
      case GameLogic.FairySF() | GameLogic.Samurai() | GameLogic.Togyzkumalak() | GameLogic.Go() |
          GameLogic.Backgammon() | GameLogic.Abalone() =>
        Reader
          .replayResultFromActionStrs(
            game.variant.gameLogic,
            actionStrs = game.actionStrs,
            op = actionStrs => {
              //rewindTurn (which might just be one ply)
              if (actionStrs.takeRight(1).flatten.size <= 1 || !rewindPly)
                //adding empty Vector enables actionStrs to tell that the previous turn was complete
                actionStrs.dropRight(1) :+ Vector()
              //rewindPly - keeps the same turn
              else actionStrs.dropRight(1) :+ actionStrs.takeRight(1).flatten.dropRight(1)
            },
            tags = createTags(initialFen, game)
          )
    }).flatMap(_.valid) map { replay =>
      val switchPlayer = game.turnPlayerIndex != replay.state.player
      val playerIndex  = if (switchPlayer) game.turnPlayerIndex else !game.turnPlayerIndex
      val rewindedGame = replay.state
      val pliesRemoved = game.stratGame.plies - rewindedGame.plies
      val newClock = game.clock.map(_.takeback(switchPlayer)) map { clk =>
        game.clockHistory
          .flatMap(_.lastX(playerIndex, pliesRemoved))
          .fold(clk) { t =>
            clk.setRemainingTime(playerIndex, t)
          }
      }
      def rewindPlayer(player: Player) = player.copy(proposeTakebackAt = 0)
      val newGame = game.copy(
        p1Player = rewindPlayer(game.p1Player),
        p2Player = rewindPlayer(game.p2Player),
        stratGame = rewindedGame.copy(clock = newClock),
        binaryPlyTimes = game.binaryPlyTimes.map { binary =>
          val plyTimes = BinaryFormat.plyTime.read(binary, game.plies)
          BinaryFormat.plyTime.write(plyTimes.take(rewindedGame.plies))
        },
        loadClockHistory = _ => game.clockHistory.map(_.update(!playerIndex, _.dropRight(pliesRemoved))),
        updatedAt = DateTime.now,
        turnAt = DateTime.now //this is not the actual turn start time but closer than not change it.
      )
      Progress(game, newGame)
    }

}
