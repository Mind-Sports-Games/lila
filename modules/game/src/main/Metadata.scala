package lila.game

import java.security.MessageDigest
import lila.db.ByteArray
import org.joda.time.DateTime
import strategygames.{ P2, Player => PlayerIndex, P1 }

private[game] case class Metadata(
    source: Option[Source],
    pgnImport: Option[PgnImport],
    tournamentId: Option[String],
    swissId: Option[String],
    simulId: Option[String],
    analysed: Boolean,
    drawOffers: GameDrawOffers,
    //draughts options
    simulPairing: Option[Int] = None,
    timeOutUntil: Option[DateTime] = None,
    drawLimit: Option[Int] = None,
    multiMatch: Option[String] = None
) {

  //TODO change for play x
  def needsMultiMatchRematch =
    //  (multiMatch.pp("needsmmCalled mm is").contains("multiMatch") || multiMatch.contains(":")).pp("needs mm")
    false
  // (
  //   multiMatch.pp("needsmmCalled mm is").fold(false)(x => x.contains("multiMatch") || x.contains(":"))
  // ).pp("needs mm")

  def multiMatchGameNr = multiMatch.pp("multiMatch get nb") ?? { mm =>
    if (mm.pp("mm") == "multiMatch") 1.some
    else if (mm.length() == 10 && mm.substring(1, 2) == ":")(toInt(mm.take(1)).map(x => x + 1)).pp("game nb")
    else none
  }

  def multiMatchGameId = multiMatch.map { mm =>
    if (mm.length() == 10 && mm.substring(1, 2) == ":") mm.drop(2).pp("mm gameId")
    else "*"
  }

  private def toInt(s: String): Option[Int] = {
    try {
      Some(s.toInt)
    } catch {
      case _: Exception => None
    }
  }

  def pgnDate = pgnImport flatMap (_.date)

  def pgnUser = pgnImport flatMap (_.user)

  def isEmpty = this == Metadata.empty
}

private[game] object Metadata {

  val empty = Metadata(None, None, None, None, None, analysed = false, GameDrawOffers.empty)
}

// plies
case class GameDrawOffers(p1: Set[Int], p2: Set[Int]) {

  def lastBy(playerIndex: PlayerIndex): Option[Int] = playerIndex.fold(p1, p2).maxOption

  def add(playerIndex: PlayerIndex, ply: Int) =
    playerIndex.fold(copy(p1 = p1 incl ply), copy(p2 = p2 incl ply))

  def isEmpty = this == GameDrawOffers.empty

  // playstrategy allows to offer draw on either turn,
  // normalize to pretend it was done on the opponent turn.
  def normalize(playerIndex: PlayerIndex): Set[Int] = playerIndex.fold(p1, p2) map {
    case ply if (ply % 2 == 0) == playerIndex.p1 => ply + 1
    case ply => ply
  }
  def normalizedPlies: Set[Int] = normalize(P1) ++ normalize(P2)
}

object GameDrawOffers {
  val empty = GameDrawOffers(Set.empty, Set.empty)
}

case class PgnImport(
    user: Option[String],
    date: Option[String],
    pgn: String,
    // hashed PGN for DB unicity
    h: Option[ByteArray]
)

object PgnImport {

  def hash(pgn: String) =
    ByteArray {
      MessageDigest getInstance "MD5" digest {
        pgn.linesIterator
          .map(_.replace(" ", ""))
          .filter(_.nonEmpty)
          .to(List)
          .mkString("\n")
          .getBytes("UTF-8")
      } take 12
    }

  def make(
      user: Option[String],
      date: Option[String],
      pgn: String
  ) =
    PgnImport(
      user = user,
      date = date,
      pgn = pgn,
      h = hash(pgn).some
    )

  import reactivemongo.api.bson.Macros
  import ByteArray.ByteArrayBSONHandler
  implicit val pgnImportBSONHandler = Macros.handler[PgnImport]
}
