package lila.history

import scala.util.Success

import lila.rating.PerfType

case class History(
    standard: RatingsMap,
    chess960: RatingsMap,
    kingOfTheHill: RatingsMap,
    antichess: RatingsMap,
    threeCheck: RatingsMap,
    atomic: RatingsMap,
    horde: RatingsMap,
    racingKings: RatingsMap,
    crazyhouse: RatingsMap,
    linesOfAction: RatingsMap,
    international: RatingsMap,
    frisian: RatingsMap,
    frysk: RatingsMap,
    antidraughts: RatingsMap,
    breakthrough: RatingsMap,
    russian: RatingsMap,
    brazilian: RatingsMap,
    pool: RatingsMap,
    shogi: RatingsMap,
    xiangqi: RatingsMap,
    ultraBullet: RatingsMap,
    bullet: RatingsMap,
    blitz: RatingsMap,
    rapid: RatingsMap,
    classical: RatingsMap,
    correspondence: RatingsMap,
    puzzle: RatingsMap
) {

  def apply(perfType: PerfType): RatingsMap =
    perfType.key match {
      case "standard"       => standard
      case "bullet"         => bullet
      case "blitz"          => blitz
      case "rapid"          => rapid
      case "classical"      => classical
      case "correspondence" => correspondence
      case "chess960"       => chess960
      case "kingOfTheHill"  => kingOfTheHill
      case "antichess"      => antichess
      case "threeCheck"     => threeCheck
      case "atomic"         => atomic
      case "horde"          => horde
      case "racingKings"    => racingKings
      case "crazyhouse"     => crazyhouse
      case "linesOfAction"  => linesOfAction
      case "international"  => international
      case "frisian"        => frisian
      case "frysk"          => frysk
      case "antidraughts"   => antidraughts
      case "breakthrough"   => breakthrough
      case "russian"        => russian
      case "brazilian"      => brazilian
      case "pool"           => pool
      case "shogi"          => shogi
      case "puzzle"         => puzzle
      case "ultraBullet"    => ultraBullet
      case x                => sys error s"No history for perf $x"
    }
}

object History {

  import reactivemongo.api.bson._

  implicit private[history] val RatingsMapReader = new BSONDocumentReader[RatingsMap] {
    def readDocument(doc: BSONDocument) =
      Success(
        doc.elements
          .flatMap {
            case BSONElement(k, BSONInteger(v)) => k.toIntOption map (_ -> v)
            case _                              => none[(Int, Int)]
          }
          .sortBy(_._1)
          .toList
      )
  }

  implicit private[history] val HistoryBSONReader = new BSONDocumentReader[History] {
    def readDocument(doc: BSONDocument) =
      Success {
        def ratingsMap(key: String): RatingsMap = ~doc.getAsOpt[RatingsMap](key)
        History(
          standard = ratingsMap("standard"),
          chess960 = ratingsMap("chess960"),
          kingOfTheHill = ratingsMap("kingOfTheHill"),
          threeCheck = ratingsMap("threeCheck"),
          antichess = ratingsMap("antichess"),
          atomic = ratingsMap("atomic"),
          horde = ratingsMap("horde"),
          racingKings = ratingsMap("racingKings"),
          crazyhouse = ratingsMap("crazyhouse"),
          linesOfAction = ratingsMap("linesOfAction"),
          international = ratingsMap("international"),
          frisian = ratingsMap("frisian"),
          frysk = ratingsMap("frysk"),
          antidraughts = ratingsMap("antidraughts"),
          breakthrough = ratingsMap("breakthrough"),
          russian = ratingsMap("russian"),
          brazilian = ratingsMap("brazilian"),
          pool = ratingsMap("pool"),
          shogi = ratingsMap("shogi"),
          xiangqi = ratingsMap("xiangqi"),
          ultraBullet = ratingsMap("ultraBullet"),
          bullet = ratingsMap("bullet"),
          blitz = ratingsMap("blitz"),
          rapid = ratingsMap("rapid"),
          classical = ratingsMap("classical"),
          correspondence = ratingsMap("correspondence"),
          puzzle = ratingsMap("puzzle")
        )
      }
  }
}
