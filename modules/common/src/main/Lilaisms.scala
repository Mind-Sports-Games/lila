package lila

import cats.data.Validated
import com.typesafe.config.Config
import org.joda.time.DateTime
import ornicar.scalalib
import play.api.libs.json.{ JsObject, JsValue }
import scala.concurrent.duration._
import scala.util.Try

import lila.base._

trait Lilaisms
    extends LilaTypes
    with scalalib.Common
    with scalalib.OrnicarOption
    with scalalib.Regex
    with scalalib.Zeros
    with scalalib.Zero.Syntax
    with cats.syntax.OptionSyntax
    with cats.syntax.ListSyntax {

  type StringValue = lila.base.LilaTypes.StringValue
  type IntValue    = lila.base.LilaTypes.IntValue

  @inline implicit def toPimpedFuture[A](f: Fu[A]): PimpedFuture[A]               = new PimpedFuture(f)
  @inline implicit def toPimpedFutureBoolean(f: Fu[Boolean]): PimpedFutureBoolean = new PimpedFutureBoolean(f)
  @inline implicit def toPimpedFutureOption[A](f: Fu[Option[A]]): PimpedFutureOption[A] =
    new PimpedFutureOption(f)
  @inline implicit def toPimpedIterableFuture[A, M[X] <: IterableOnce[X]](
      t: M[Fu[A]]
  ): PimpedIterableFuture[A, M] =
    new PimpedIterableFuture(t)

  @inline implicit def toPimpedJsObject(jo: JsObject): PimpedJsObject = new PimpedJsObject(jo)
  @inline implicit def toPimpedJsValue(jv: JsValue): PimpedJsValue    = new PimpedJsValue(jv)

  @inline implicit def toAugmentedAny(b: Any): AugmentedAny       = new AugmentedAny(b)
  @inline implicit def toPimpedBoolean(b: Boolean): PimpedBoolean = new PimpedBoolean(b)
  @inline implicit def toPimpedInt(i: Int): PimpedInt             = new PimpedInt(i)
  @inline implicit def toPimpedLong(l: Long): PimpedLong          = new PimpedLong(l)
  @inline implicit def toPimpedFloat(f: Float): PimpedFloat       = new PimpedFloat(f)
  @inline implicit def toPimpedDouble(d: Double): PimpedDouble    = new PimpedDouble(d)

  @inline implicit def toPimpedTryList[A](l: List[Try[A]]): PimpedTryList[A] = new PimpedTryList(l)
  @inline implicit def toPimpedList[A](l: List[A]): PimpedList[A]            = new PimpedList(l)
  @inline implicit def toPimpedSeq[A](l: Seq[A]): PimpedSeq[A]               = new PimpedSeq(l)
  @inline implicit def toPimpedByteArray(ba: Array[Byte]): PimpedByteArray   = new PimpedByteArray(ba)

  @inline implicit def toPimpedOption[A](a: Option[A]): PimpedOption[A]          = new PimpedOption(a)
  @inline implicit def toPimpedString(s: String): PimpedString                   = new PimpedString(s)
  @inline implicit def toPimpedConfig(c: Config): PimpedConfig                   = new PimpedConfig(c)
  @inline implicit def toPimpedDateTime(d: DateTime): PimpedDateTime             = new PimpedDateTime(d)
  @inline implicit def toPimpedTry[A](t: Try[A]): PimpedTry[A]                   = new PimpedTry(t)
  @inline implicit def toPimpedEither[A, B](e: Either[A, B]): PimpedEither[A, B] = new PimpedEither(e)
  @inline implicit def toPimpedFiniteDuration(d: FiniteDuration): PimpedFiniteDuration =
    new PimpedFiniteDuration(d)

  @inline implicit def toRichValidated[E, A](v: Validated[E, A]): RichValidated[E, A] = new RichValidated(v)
}
