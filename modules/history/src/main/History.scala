package lila.history

import scala.util.Success

import lila.rating.PerfType

case class History(
    standard: RatingsMap,
    chess960: RatingsMap,
    kingOfTheHill: RatingsMap,
    antichess: RatingsMap,
    threeCheck: RatingsMap,
    fiveCheck: RatingsMap,
    atomic: RatingsMap,
    horde: RatingsMap,
    racingKings: RatingsMap,
    crazyhouse: RatingsMap,
    noCastling: RatingsMap,
    linesOfAction: RatingsMap,
    scrambledEggs: RatingsMap,
    international: RatingsMap,
    frisian: RatingsMap,
    frysk: RatingsMap,
    antidraughts: RatingsMap,
    breakthrough: RatingsMap,
    russian: RatingsMap,
    brazilian: RatingsMap,
    pool: RatingsMap,
    portuguese: RatingsMap,
    english: RatingsMap,
    shogi: RatingsMap,
    xiangqi: RatingsMap,
    minishogi: RatingsMap,
    minixiangqi: RatingsMap,
    flipello: RatingsMap,
    flipello10: RatingsMap,
    amazons: RatingsMap,
    oware: RatingsMap,
    togyzkumalak: RatingsMap,
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
      case "fiveCheck"      => fiveCheck
      case "atomic"         => atomic
      case "horde"          => horde
      case "racingKings"    => racingKings
      case "crazyhouse"     => crazyhouse
      case "noCastling"     => noCastling
      case "linesOfAction"  => linesOfAction
      case "scrambledEggs"  => scrambledEggs
      case "international"  => international
      case "frisian"        => frisian
      case "frysk"          => frysk
      case "antidraughts"   => antidraughts
      case "breakthrough"   => breakthrough
      case "russian"        => russian
      case "brazilian"      => brazilian
      case "pool"           => pool
      case "portuguese"     => portuguese
      case "english"        => english
      case "shogi"          => shogi
      case "xiangqi"        => xiangqi
      case "minishogi"      => minishogi
      case "minixiangqi"    => minixiangqi
      case "flipello"       => flipello
      case "flipello10"     => flipello10
      case "amazons"        => amazons
      case "oware"          => oware
      case "togyzkumalak"   => togyzkumalak
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
          fiveCheck = ratingsMap("fiveCheck"),
          antichess = ratingsMap("antichess"),
          atomic = ratingsMap("atomic"),
          horde = ratingsMap("horde"),
          racingKings = ratingsMap("racingKings"),
          crazyhouse = ratingsMap("crazyhouse"),
          noCastling = ratingsMap("noCastling"),
          linesOfAction = ratingsMap("linesOfAction"),
          scrambledEggs = ratingsMap("scrambledEggs"),
          international = ratingsMap("international"),
          frisian = ratingsMap("frisian"),
          frysk = ratingsMap("frysk"),
          antidraughts = ratingsMap("antidraughts"),
          breakthrough = ratingsMap("breakthrough"),
          russian = ratingsMap("russian"),
          brazilian = ratingsMap("brazilian"),
          pool = ratingsMap("pool"),
          portuguese = ratingsMap("portuguese"),
          english = ratingsMap("english"),
          shogi = ratingsMap("shogi"),
          xiangqi = ratingsMap("xiangqi"),
          minishogi = ratingsMap("minishogi"),
          minixiangqi = ratingsMap("minixiangqi"),
          flipello = ratingsMap("flipello"),
          flipello10 = ratingsMap("flipello10"),
          amazons = ratingsMap("amazons"),
          oware = ratingsMap("oware"),
          togyzkumalak = ratingsMap("togyzkumalak"),
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
