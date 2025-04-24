package lila.importer

import strategygames.chess._

private object Chess960 {

  def isStartPosition(board: Board) =
    board valid {

      def rankMatches(f: Option[Piece] => Boolean)(rank: Rank) =
        File.all forall { file =>
          f(board(file, rank))
        }

      rankMatches {
        case Some(Piece(P1, King | Queen | Rook | Knight | Bishop)) => true
        case _                                                      => false
      }(Rank.First) &&
      rankMatches {
        case Some(Piece(P1, Pawn)) => true
        case _                     => false
      }(Rank.Second) &&
      List(Rank.Third, Rank.Fourth, Rank.Fifth, Rank.Sixth).forall(rankMatches(_.isEmpty)) &&
      rankMatches {
        case Some(Piece(P2, Pawn)) => true
        case _                     => false
      }(Rank.Seventh) &&
      rankMatches {
        case Some(Piece(P2, King | Queen | Rook | Knight | Bishop)) => true
        case _                                                      => false
      }(Rank.Eighth)
    }

  def fixVariantName(v: String) =
    v.toLowerCase match {
      case "chess 960"   => "chess960"
      case "fisherandom" => "chess960" // I swear, sometimes...
      case _             => v
    }
}
