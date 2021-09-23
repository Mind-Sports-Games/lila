package lila.rating

import strategygames.{ Centis, Speed }
import strategygames.variant.Variant
import play.api.i18n.Lang

import lila.i18n.I18nKeys

sealed abstract class PerfType(
    val id: Perf.ID,
    val key: Perf.Key,
    private val name: String,
    private val title: String,
    val iconChar: Char
) {

  def iconString = iconChar.toString

  def trans(implicit lang: Lang): String = PerfType.trans(this)

  def desc(implicit lang: Lang): String = PerfType.desc(this)
}

object PerfType {

  case object UltraBullet
      extends PerfType(
        0,
        key = "ultraBullet",
        name = Speed.UltraBullet.name,
        title = Speed.UltraBullet.title,
        iconChar = '{'
      )

  case object Bullet
      extends PerfType(
        1,
        key = "bullet",
        name = Speed.Bullet.name,
        title = Speed.Bullet.title,
        iconChar = 'T'
      )

  case object Blitz
      extends PerfType(
        2,
        key = "blitz",
        name = Speed.Blitz.name,
        title = Speed.Blitz.title,
        iconChar = ')'
      )

  case object Rapid
      extends PerfType(
        6,
        key = "rapid",
        name = Speed.Rapid.name,
        title = Speed.Rapid.title,
        iconChar = '#'
      )

  case object Classical
      extends PerfType(
        3,
        key = "classical",
        name = Speed.Classical.name,
        title = Speed.Classical.title,
        iconChar = '+'
      )

  case object Correspondence
      extends PerfType(
        4,
        key = "correspondence",
        name = "Correspondence",
        title = Speed.Correspondence.title,
        iconChar = ';'
      )

  case object Standard
      extends PerfType(
        5,
        key = "standard",
        name = Variant.Chess(strategygames.chess.variant.Standard).name,
        title = "Standard rules of chess",
        iconChar = '8'
      )

  case object Chess960
      extends PerfType(
        11,
        key = "chess960",
        name = Variant.Chess(strategygames.chess.variant.Chess960).name,
        title = "Chess960 variant",
        iconChar = '\''
      )

  case object KingOfTheHill
      extends PerfType(
        12,
        key = "kingOfTheHill",
        name = Variant.Chess(strategygames.chess.variant.KingOfTheHill).name,
        title = "King of the Hill variant",
        iconChar = '('
      )

  case object Antichess
      extends PerfType(
        13,
        key = "antichess",
        name = Variant.Chess(strategygames.chess.variant.Antichess).name,
        title = "Antichess variant",
        iconChar = '@'
      )

  case object Atomic
      extends PerfType(
        14,
        key = "atomic",
        name = Variant.Chess(strategygames.chess.variant.Atomic).name,
        title = "Atomic variant",
        iconChar = '>'
      )

  case object ThreeCheck
      extends PerfType(
        15,
        key = "threeCheck",
        name = Variant.Chess(strategygames.chess.variant.ThreeCheck).name,
        title = "Three-check variant",
        iconChar = '.'
      )

  case object FiveCheck
      extends PerfType(
        22,
        key = "fiveCheck",
        name = Variant.Chess(strategygames.chess.variant.FiveCheck).name,
        title = "Five-check variant",
        iconChar = '.'
      )


  case object Horde
      extends PerfType(
        16,
        key = "horde",
        name = Variant.Chess(strategygames.chess.variant.Horde).name,
        title = "Horde variant",
        iconChar = '_'
      )

  case object RacingKings
      extends PerfType(
        17,
        key = "racingKings",
        name = Variant.Chess(strategygames.chess.variant.RacingKings).name,
        title = "Racing kings variant",
        iconChar = ''
      )

  case object Crazyhouse
      extends PerfType(
        18,
        key = "crazyhouse",
        name = Variant.Chess(strategygames.chess.variant.Crazyhouse).name,
        title = "Crazyhouse variant",
        iconChar = ''
      )

  case object Puzzle
      extends PerfType(
        20,
        key = "puzzle",
        name = "Training",
        title = "Chess tactics trainer",
        iconChar = '-'
      )

  case object LinesOfAction
      extends PerfType(
        21,
        key = "linesOfAction",
        name = Variant.Chess(strategygames.chess.variant.LinesOfAction).name,
        title = "Lines Of Action game",
        iconChar = ''
      )

  case object International
      extends PerfType(
        105,
        key = "international",
        name = Variant.Draughts(strategygames.draughts.variant.Standard).name,
        title = "Standard rules of international draughts",
        iconChar = 'K'
      )

  case object Frisian
      extends PerfType(
        111,
        key = "frisian",
        name = Variant.Draughts(strategygames.draughts.variant.Frisian).name,
        title = "Frisian variant",
        iconChar = 'K'
      )

  case object Frysk
      extends PerfType(
        116,
        key = "frysk",
        name = Variant.Draughts(strategygames.draughts.variant.Frysk).name,
        title = "Frysk! variant",
        iconChar = 'K'
      )

  case object Antidraughts
      extends PerfType(
        113,
        key = "antidraughts",
        name = Variant.Draughts(strategygames.draughts.variant.Antidraughts).name,
        title = "Antidraughts variant",
        iconChar = 'K'
      )

  case object Breakthrough
      extends PerfType(
        117,
        key = "breakthrough",
        name = Variant.Draughts(strategygames.draughts.variant.Breakthrough).name,
        title = "Breakthrough variant",
        iconChar = 'K'
      )

  case object Russian
      extends PerfType(
        122,
        key = "russian",
        name = Variant.Draughts(strategygames.draughts.variant.Russian).name,
        title = "Russian draughts",
        iconChar = 'K'
      )

  case object Brazilian
      extends PerfType(
        123,
        key = "brazilian",
        name = Variant.Draughts(strategygames.draughts.variant.Brazilian).name,
        title = "Brazilian draughts",
        iconChar = 'K'
      )

  case object Pool
      extends PerfType(
        124,
        key = "pool",
        name = Variant.Draughts(strategygames.draughts.variant.Pool).name,
        title = "Pool draughts",
        iconChar = 'K'
      )

  val all: List[PerfType] = List(
    UltraBullet,
    Bullet,
    Blitz,
    Rapid,
    Classical,
    Correspondence,
    Standard,
    Crazyhouse,
    Chess960,
    KingOfTheHill,
    ThreeCheck,
    Antichess,
    Atomic,
    Horde,
    RacingKings,
    Puzzle,
    LinesOfAction,
    International,
    Frisian,
    Frysk,
    Antidraughts,
    Breakthrough,
    Russian,
    Brazilian,
    Pool
  )
  val byKey = all map { p =>
    (p.key, p)
  } toMap
  val byId = all map { p =>
    (p.id, p)
  } toMap

  val default = Standard

  def apply(key: Perf.Key): Option[PerfType] = byKey get key
  def orDefault(key: Perf.Key): PerfType     = apply(key) | default

  def apply(id: Perf.ID): Option[PerfType] = byId get id

  // def name(key: Perf.Key): Option[String] = apply(key) map (_.name)

  def id2key(id: Perf.ID): Option[Perf.Key] = byId get id map (_.key)

  val nonPuzzle: List[PerfType] = List(
    UltraBullet,
    Bullet,
    Blitz,
    Rapid,
    Classical,
    Correspondence,
    Crazyhouse,
    Chess960,
    KingOfTheHill,
    ThreeCheck,
    Antichess,
    Atomic,
    Horde,
    RacingKings,
    LinesOfAction,
    International,
    Frisian,
    Frysk,
    Antidraughts,
    Breakthrough,
    Russian,
    Brazilian,
    Pool
  )
  val leaderboardable: List[PerfType] = List(
    Bullet,
    Blitz,
    Rapid,
    Classical,
    UltraBullet,
    Crazyhouse,
    Chess960,
    KingOfTheHill,
    ThreeCheck,
    Antichess,
    Atomic,
    Horde,
    RacingKings,
    LinesOfAction,
    International,
    Frisian,
    Frysk,
    Antidraughts,
    Breakthrough,
    Russian,
    Brazilian,
    Pool
  )
  val variants: List[PerfType] =
    List(
      Crazyhouse,
      Chess960,
      KingOfTheHill,
      ThreeCheck,
      Antichess,
      Atomic,
      Horde,
      RacingKings,
      LinesOfAction,
      International,
      Frisian,
      Frysk,
      Antidraughts,
      Breakthrough,
      Russian,
      Brazilian,
      Pool
    )
  val standard: List[PerfType] = List(Bullet, Blitz, Rapid, Classical, Correspondence)

  def variantOf(pt: PerfType): Variant =
    pt match {
      case Crazyhouse    => Variant.Chess(strategygames.chess.variant.Crazyhouse)
      case Chess960      => Variant.Chess(strategygames.chess.variant.Chess960)
      case KingOfTheHill => Variant.Chess(strategygames.chess.variant.KingOfTheHill)
      case ThreeCheck    => Variant.Chess(strategygames.chess.variant.ThreeCheck)
      case Antichess     => Variant.Chess(strategygames.chess.variant.Antichess)
      case Atomic        => Variant.Chess(strategygames.chess.variant.Atomic)
      case Horde         => Variant.Chess(strategygames.chess.variant.Horde)
      case RacingKings   => Variant.Chess(strategygames.chess.variant.RacingKings)
      case LinesOfAction => Variant.Chess(strategygames.chess.variant.LinesOfAction)
      case International => Variant.Draughts(strategygames.draughts.variant.Standard)
      case Frisian          => Variant.Draughts(strategygames.draughts.variant.Frisian)
      case Frysk            => Variant.Draughts(strategygames.draughts.variant.Frysk)
      case Antidraughts     => Variant.Draughts(strategygames.draughts.variant.Antidraughts)
      case Breakthrough     => Variant.Draughts(strategygames.draughts.variant.Breakthrough)
      case Russian          => Variant.Draughts(strategygames.draughts.variant.Russian)
      case Brazilian        => Variant.Draughts(strategygames.draughts.variant.Brazilian)
      case Pool             => Variant.Draughts(strategygames.draughts.variant.Pool)
      case _             => Variant.Chess(strategygames.chess.variant.Standard)
    }

  def byVariant(variant: Variant): Option[PerfType] =
    variant match {
      case Variant.Chess(strategygames.chess.variant.Standard)      => none
      case Variant.Chess(strategygames.chess.variant.FromPosition)  => none
      case Variant.Chess(strategygames.chess.variant.Crazyhouse)    => Crazyhouse.some
      case Variant.Chess(strategygames.chess.variant.Chess960)      => Chess960.some
      case Variant.Chess(strategygames.chess.variant.KingOfTheHill) => KingOfTheHill.some
      case Variant.Chess(strategygames.chess.variant.ThreeCheck)    => ThreeCheck.some
      case Variant.Chess(strategygames.chess.variant.Antichess)     => Antichess.some
      case Variant.Chess(strategygames.chess.variant.Atomic)        => Atomic.some
      case Variant.Chess(strategygames.chess.variant.Horde)         => Horde.some
      case Variant.Chess(strategygames.chess.variant.RacingKings)   => RacingKings.some
      case Variant.Chess(strategygames.chess.variant.LinesOfAction) => LinesOfAction.some
      case Variant.Draughts(strategygames.draughts.variant.Standard) => International.some
      case Variant.Draughts(strategygames.draughts.variant.Frisian)      => Frisian.some
      case Variant.Draughts(strategygames.draughts.variant.Frysk)        => Frysk.some
      case Variant.Draughts(strategygames.draughts.variant.Antidraughts) => Antidraughts.some
      case Variant.Draughts(strategygames.draughts.variant.Breakthrough) => Breakthrough.some
      case Variant.Draughts(strategygames.draughts.variant.Russian)      => Russian.some
      case Variant.Draughts(strategygames.draughts.variant.Brazilian)    => Brazilian.some
      case Variant.Draughts(strategygames.draughts.variant.Pool)         => Pool.some
    }

  def standardBySpeed(speed: Speed): PerfType = speed match {
    case Speed.UltraBullet    => UltraBullet
    case Speed.Bullet         => Bullet
    case Speed.Blitz          => Blitz
    case Speed.Rapid          => Rapid
    case Speed.Classical      => Classical
    case Speed.Correspondence => Correspondence
  }

  def apply(variant: Variant, speed: Speed): PerfType =
    byVariant(variant) getOrElse standardBySpeed(speed)

  lazy val totalTimeRoughEstimation: Map[PerfType, Centis] = nonPuzzle.view
    .map { pt =>
      pt -> Centis(pt match {
        case UltraBullet    => 25 * 100
        case Bullet         => 90 * 100
        case Blitz          => 7 * 60 * 100
        case Rapid          => 12 * 60 * 100
        case Classical      => 30 * 60 * 100
        case Correspondence => 60 * 60 * 100
        case _              => 7 * 60 * 100
      })
    }
    .to(Map)

  def iconByVariant(variant: Variant): Char =
    byVariant(variant).fold('C')(_.iconChar)

  def trans(pt: PerfType)(implicit lang: Lang): String =
    pt match {
      case Rapid          => I18nKeys.rapid.txt()
      case Classical      => I18nKeys.classical.txt()
      case Correspondence => I18nKeys.correspondence.txt()
      case Puzzle         => I18nKeys.puzzles.txt()
      case pt             => pt.name
    }

  val translated: Set[PerfType] = Set(Rapid, Classical, Correspondence, Puzzle)

  def desc(pt: PerfType)(implicit lang: Lang): String =
    pt match {
      case UltraBullet    => I18nKeys.ultraBulletDesc.txt()
      case Bullet         => I18nKeys.bulletDesc.txt()
      case Blitz          => I18nKeys.blitzDesc.txt()
      case Rapid          => I18nKeys.rapidDesc.txt()
      case Classical      => I18nKeys.classicalDesc.txt()
      case Correspondence => I18nKeys.correspondenceDesc.txt()
      case Puzzle         => I18nKeys.puzzleDesc.txt()
      case pt             => pt.title
    }
}
