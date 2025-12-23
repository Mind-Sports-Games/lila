package lila.puzzle

import cats.data.NonEmptyList
import strategygames.format.{ FEN, Forsyth, Uci }
import strategygames.{ Player => PlayerIndex, GameLogic }
import strategygames.variant.Variant

import lila.rating.{ Glicko, PerfType }
import lila.common.Iso
import lila.i18n.I18nKeys
import play.api.i18n.Lang

case class Puzzle(
    id: Puzzle.Id,
    gameId: lila.game.Game.ID,
    lib: Int,
    variantId: Int,
    fen: FEN,
    line: NonEmptyList[Uci.Move],
    glicko: Glicko,
    plays: Int,
    vote: Float, // denormalized ratio of voteUp/voteDown
    themes: Set[PuzzleTheme.Key]
) {

  def gameLogic: GameLogic = GameLogic(lib)
  def variant: Variant     = Variant.orDefault(gameLogic, variantId)

  //When updating, also edit modules/game, modules/challenge, and ui/@types/playstrategy/index.d.ts:declare type PlayerName
  def playerTrans(implicit lang: Lang): String =
    variant.playerNames(playerIndex) match {
      case "White" => I18nKeys.white.txt()
      case "Black" => I18nKeys.black.txt()
      //Xiangqi add back in when adding red as a colour for Xiangqi
      //case "Red"   => I18nKeys.red.txt()
      case "Sente"   => I18nKeys.sente.txt()
      case "Gote"    => I18nKeys.gote.txt()
      case s: String => s
    }

  // ply after "initial move" when we start solving
  def initialPly: Int =
    fen.fullMove ?? { fm =>
      fm * 2 - playerIndex.fold(1, 2)
    }

  //TODO suport all actions when adding more variants
  lazy val fenAfterInitialMove: FEN = {
    for {
      sit1 <- Forsyth.<<(variant.gameLogic, fen)
      sit2 <- sit1.move(line.head).toOption.map(_.situationAfter)
    } yield Forsyth.>>(variant.gameLogic, sit2)
  } err s"Can't apply puzzle $id first move"

  def playerIndex = fen.player.fold[PlayerIndex](PlayerIndex.P1)(!_)
}

object Puzzle {

  val idSize = 5

  case class Id(value: String) extends AnyVal with StringValue

  def toId(id: String) = id.size == idSize option Id(id)

  val puzzleVariants: List[Variant] = List(
    Variant.orDefault(GameLogic.Chess(), 1), //Standard
    Variant.orDefault(GameLogic.Chess(), 7), //Atomic
    Variant.orDefault(GameLogic.Chess(), 11) //Lines of Action
    //Variant.orDefault(GameLogic.FairySF(), 2) //Xiangqi - requires startops support first
  )

  val defaultVariant: Variant = puzzleVariants.head

  val randomVariant: Variant = puzzleVariants(scala.util.Random.nextInt(puzzleVariants.size))

  /* The mobile app requires numerical IDs.
   * We convert string ids from and to Longs using base 62
   */
  object numericalId {

    private val powers: List[Long] =
      (0 until idSize).toList.map(m => Math.pow(62, m).toLong)

    def apply(id: Id): Long = id.value.toList
      .zip(powers)
      .foldLeft(0L) { case (l, (char, pow)) =>
        l + charToInt(char) * pow
      }

    def apply(l: Long): Option[Id] = (l > 130_000) ?? {
      val str = powers.reverse
        .foldLeft(("", l)) { case ((id, rest), pow) =>
          val frac = rest / pow
          (s"${intToChar(frac.toInt)}$id", rest - frac * pow)
        }
        ._1
      (str.size == idSize) option Id(str)
    }

    private def charToInt(c: Char) = {
      val i = c.toInt
      if (i > 96) i - 71
      else if (i > 64) i - 65
      else i + 4
    }

    private def intToChar(i: Int): Char = {
      if (i < 26) i + 65
      else if (i < 52) i + 71
      else i - 4
    }.toChar
  }

  case class UserResult(
      puzzleId: Id,
      userId: lila.user.User.ID,
      result: Result,
      rating: (Int, Int),
      perfType: PerfType
  )

  object BSONFields {
    val id       = "_id"
    val gameId   = "gameId"
    val lib      = "l"
    val variant  = "v"
    val fen      = "fen"
    val line     = "line"
    val glicko   = "glicko"
    val vote     = "vote"
    val voteUp   = "vu"
    val voteDown = "vd"
    val plays    = "plays"
    val themes   = "themes"
    val day      = "day"
    val dirty    = "dirty" // themes need to be denormalized
  }

  implicit val idIso: Iso.StringIso[Id] = lila.common.Iso.string[Id](Id.apply, _.value)
}
