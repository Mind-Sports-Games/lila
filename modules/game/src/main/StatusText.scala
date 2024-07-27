package lila.game

import strategygames.variant.Variant
import strategygames.{ Player => PlayerIndex, Status }

object StatusText {

  import Status._

  def apply(status: Status, win: Option[PlayerIndex], variant: Variant): String =
    status match {
      case Aborted                               => "Game was aborted."
      case Mate                                  => s"${winner(win)} wins by checkmate."
      case PerpetualCheck                        => s"${winner(win)} wins by opponent causing perpetual check."
      case Resign                                => s"${loser(win)} resigns."
      case ResignGammon                          => s"${loser(win)} resigns a gammon."
      case ResignBackgammon                      => s"${loser(win)} resigns a backgammon."
      case UnknownFinish                         => s"${winner(win)} wins."
      case Stalemate if !variant.stalemateIsDraw => s"${winner(win)} wins by stalemate."
      case Stalemate                             => "Draw by stalemate."
      case Timeout if win.isDefined              => s"${loser(win)} left the game."
      case Timeout | Draw                        => "The game is a draw."
      case Outoftime                             => s"${winner(win)} wins on time."
      case OutoftimeGammon                       => s"${winner(win)} wins a gammon on time."
      case OutoftimeBackgammon                   => s"${winner(win)} wins a backgammon on time."
      case RuleOfGin                             => s"${winner(win)} wins by rule of gin."
      case GinGammon                             => s"${winner(win)} wins a gammon by rule of gin."
      case GinBackgammon                         => s"${winner(win)} wins a backgammon by rule of gin."
      case NoStart                               => s"${loser(win)} wins by forfeit."
      case Cheat                                 => "Cheat detected."
      case SingleWin                             => s"${winner(win)} wins."
      case GammonWin                             => s"${winner(win)} wins by gammon."
      case BackgammonWin                         => s"${winner(win)} wins by backgammon."
      case VariantEnd =>
        variant match {
          case Variant.Chess(strategygames.chess.variant.KingOfTheHill) =>
            s"${winner(win)} brings the king in the center."
          case Variant.Chess(strategygames.chess.variant.ThreeCheck) =>
            s"${winner(win)} gives the third check."
          case Variant.Chess(strategygames.chess.variant.FiveCheck) =>
            s"${winner(win)} gives the fifth check."
          case Variant.Chess(strategygames.chess.variant.RacingKings) => s"${winner(win)} wins the race."
          case Variant.Chess(strategygames.chess.variant.LinesOfAction) =>
            s"${winner(win)} connects all of their pieces."
          case Variant.Chess(strategygames.chess.variant.ScrambledEggs) =>
            s"${winner(win)} connects all of their pieces."
          case Variant.Draughts(strategygames.draughts.variant.Breakthrough) =>
            s"${winner(win)} has a promotion first."
          case Variant.FairySF(strategygames.fairysf.variant.BreakthroughTroyka) =>
            s"${winner(win)} wins the race."
          case Variant.FairySF(strategygames.fairysf.variant.MiniBreakthroughTroyka) =>
            s"${winner(win)} wins the race."
          case _ => "Game ends by variant rule."
        }
      case _ => ""
    }

  def apply(game: lila.game.Game): String = apply(game.status, game.winnerPlayerIndex, game.variant)

  private def winner(win: Option[PlayerIndex]) = win.??(_.toString)
  private def loser(win: Option[PlayerIndex])  = winner(win.map(!_))
}
