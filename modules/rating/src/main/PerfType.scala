package lila.rating

import strategygames.{ Centis, GameFamily, GameLogic, Speed }
import strategygames.variant.Variant
import play.api.i18n.Lang

import lila.i18n.I18nKeys

sealed abstract class PuzzlePerf(
  val gameFamily: GameFamily,
  val id: Perf.ID,
  val key: Perf.Key,
  val name: String,
  val title: String,
  val iconChar: Char
)

object PuzzlePerf {
  case object ChessPuzzle
      extends PuzzlePerf(
        GameFamily.Chess(),
        50, // changed from 20 to 50 as was same as noCastling perf id.... currently unused as puzzles disabled
        key = "puzzle",
        name = "Training",
        title = "Chess tactics trainer",
        iconChar = '-'
      )
}

class PerfType(
    val category: Either[Either[Speed, Variant], PuzzlePerf]
) {

  val id: Perf.ID = category match {
    case Left(Left(s))  => s.perfId
    case Left(Right(v)) => v.perfId
    case Right(p)       => p.id
  }

  val key: Perf.Key = category match {
    case Left(Left(s))  => s.key
    case Left(Right(v)) => v.key
    case Right(p)       => p.key
  }

  private val name: String = category match {
    case Left(Left(s))  => s.name
    case Left(Right(v)) => v.name
    case Right(p)       => p.name
  }

  private val title: String = category match {
    case Left(Left(s))  => s.title
    case Left(Right(v)) => s"${v.name} variant"
    case Right(p)       => p.title
  }

  val iconChar: Char = category match {
    case Left(Left(s))  => s.perfIcon
    case Left(Right(v)) => v.perfIcon
    case Right(p)       => p.iconChar
  }

  def iconString = iconChar.toString

  def trans(implicit lang: Lang): String = PerfType.trans(this)

  def desc(implicit lang: Lang): String = PerfType.desc(this)
}

object PerfType {

  val allSpeed: List[PerfType] =
    Speed.all.map(s => new PerfType(Left(Left(s))))

  val allVariant: List[PerfType] =
    Variant.all.filter(!_.fromPositionVariant).map(v => new PerfType(Left(Right(v))))

  val allPuzzle: List[PerfType] =
    List(new PerfType(Right(PuzzlePerf.ChessPuzzle)))

  val all: List[PerfType] = allSpeed ::: allVariant ::: allPuzzle

  val byKey = all map { p =>
    (p.key, p)
  } toMap
  val byId = all map { p =>
    (p.id, p)
  } toMap

  val default = byKey("standard")

  def apply(key: Perf.Key): Option[PerfType]  = byKey get key
  def orDefault(key: Perf.Key): PerfType      = apply(key) | default
  def orDefaultSpeed(key: Perf.Key): PerfType = apply(key) | byKey("classical")

  def apply(id: Perf.ID): Option[PerfType] = byId get id

  // def name(key: Perf.Key): Option[String] = apply(key) map (_.name)

  def id2key(id: Perf.ID): Option[Perf.Key] = byId get id map (_.key)

  val nonPuzzle: List[PerfType] =
    all.filter(p => !(List("standard", "puzzle") contains p.key))

  val leaderboardable: List[PerfType] = nonPuzzle.filter(_.key != "correspondence")

  val variants: List[PerfType] = allVariant.filter(_.key != "standard")

  val standard: List[PerfType] = allSpeed.filter(_.key != "ultraBullet")

  def variantOf(pt: PerfType): Variant = pt.category match {
    case Left(Right(v)) => v
    case _              => Variant.default(GameLogic.Chess())
  }

  def byVariant(variant: Variant): Option[PerfType] =
    variants.filter(_.category == Left(Right(variant))) match {
      case List(pt) => pt.some
      case _        => none
    }

  def standardBySpeed(speed: Speed): PerfType =
    allSpeed.filter(_.category == Left(Left(speed))) match {
      case List(pt) => pt
      //unnecessary default to keep compiler happy
      case _        => orDefaultSpeed("")
    }

  def apply(variant: Variant, speed: Speed): PerfType =
    byVariant(variant) getOrElse standardBySpeed(speed)

  def totalTimeRoughEstimation(pt: PerfType): Centis =
    pt.category match {
      case Left(Left(s)) => s.totalTimeRoughEstimation
      case _             => Centis(7 * 60 * 100)
    }

  def iconByVariant(variant: Variant): Char =
    byVariant(variant).fold('C')(_.iconChar)

  def trans(pt: PerfType)(implicit lang: Lang): String =
    pt.key match {
      case "rapid"          => I18nKeys.rapid.txt()
      case "classical"      => I18nKeys.classical.txt()
      case "Correspondence" => I18nKeys.correspondence.txt()
      case "puzzle"         => I18nKeys.puzzles.txt()
      case _                => pt.name
    }

  val translated: Set[PerfType] =
    all.filter(List("rapid", "classical", "correspondence", "puzzle") contains _.key).toSet

  def desc(pt: PerfType)(implicit lang: Lang): String =
    pt.key match {
      case "ultraBullet"    => I18nKeys.ultraBulletDesc.txt()
      case "bullet"         => I18nKeys.bulletDesc.txt()
      case "blitz"          => I18nKeys.blitzDesc.txt()
      case "rapid"          => I18nKeys.rapidDesc.txt()
      case "classical"      => I18nKeys.classicalDesc.txt()
      case "correspondence" => I18nKeys.correspondenceDesc.txt()
      case "puzzle"         => I18nKeys.puzzleDesc.txt()
      case _                => pt.title
    }
}
