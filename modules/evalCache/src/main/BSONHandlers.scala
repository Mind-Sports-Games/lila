package lila.evalCache

import reactivemongo.api.bson._
import scala.util.{ Success, Try }
import cats.data.NonEmptyList

import strategygames.format.Uci
import strategygames.variant.Variant
import strategygames.GameLib
import lila.db.dsl._
import lila.tree.Eval._

private object BSONHandlers {

  import EvalCacheEntry._

  implicit private val TrustBSONHandler  = doubleAnyValHandler[Trust](_.value, Trust.apply)
  implicit private val KnodesBSONHandler = intAnyValHandler[Knodes](_.value, Knodes.apply)

  implicit val PvsHandler = new BSONHandler[NonEmptyList[Pv]] {
    private def scoreWrite(s: Score): String = s.value.fold(_.value.toString, m => s"#${m.value}")
    private def scoreRead(str: String): Option[Score] =
      if (str startsWith "#") str.drop(1).toIntOption map { m =>
        Score mate Mate(m)
      }
      else
        str.toIntOption map { c =>
          Score cp Cp(c)
        }
    private def movesWrite(moves: Moves): String = Uci writeListPiotr moves.value.toList
    private def movesRead(str: String): Option[Moves] =
      Uci.readListPiotr(str) flatMap (_.toNel) map Moves.apply
    private val scoreSeparator = ':'
    private val pvSeparator    = '/'
    private val pvSeparatorStr = pvSeparator.toString
    def readTry(bs: BSONValue) =
      bs match {
        case BSONString(value) =>
          Try {
            value.split(pvSeparator).toList.map { pvStr =>
              pvStr.split(scoreSeparator) match {
                case Array(score, moves) =>
                  Pv(
                    scoreRead(score) err s"Invalid score $score",
                    movesRead(moves) err s"Invalid moves $moves"
                  )
                case x => sys error s"Invalid PV $pvStr: ${x.toList} (in $value)"
              }
            }
          }.flatMap {
            _.toNel toTry s"Empty PVs $value"
          }
        case b => lila.db.BSON.handlerBadType[NonEmptyList[Pv]](b)
      }
    def writeTry(x: NonEmptyList[Pv]) =
      Success(BSONString {
        x.toList.map { pv =>
          s"${scoreWrite(pv.score)}$scoreSeparator${movesWrite(pv.moves)}"
        } mkString pvSeparatorStr
      })
  }

  implicit val EntryIdHandler = tryHandler[Id](
    { case BSONString(value) =>
      value split ':' match {
        case Array(lib, fen) =>
          Success(Id(Variant.libStandard(GameLib(lib.toInt)), SmallFen raw fen))
        case Array(lib, variantId, fen) =>
          Success(
            Id(
              variantId.toIntOption flatMap {
                id => Variant.apply(GameLib(lib.toInt), id)
              } err s"Invalid evalcache variant $variantId",
              SmallFen raw fen
            )
          )
        case _ => lila.db.BSON.handlerBadValue(s"Invalid evalcache id $value")
      }
    },
    x =>
      BSONString {
        if (x.variant.standardVariant || x.variant == Variant.libFromPosition(GameLib.Chess()) || x.variant == Variant.libFromPosition(GameLib.Draughts()))
          s"${x.variant.gameLib.id}:${x.smallFen.value}"
        else s"${x.variant.gameLib.id}:${x.variant.id}:${x.smallFen.value}"
      }
  )

  implicit val evalHandler  = Macros.handler[Eval]
  implicit val entryHandler = Macros.handler[EvalCacheEntry]
}
