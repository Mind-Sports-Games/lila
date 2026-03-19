package lila

import play.api.http._
import play.api.mvc.Codec
import scalatags.Text.Frag

package object app extends PackageObject {

  implicit def contentTypeOfFrag(implicit codec: Codec): ContentTypeOf[Frag] =
    ContentTypeOf[Frag](Some(ContentTypes.HTML))

  implicit def writeableOfFrag(implicit codec: Codec): Writeable[Frag] =
    Writeable(frag => codec.encode(frag.render))

  // Re-export givens as implicits so they are available via `import lila.app._`
  // (Scala 3's `import pkg._` does not import givens, only `import pkg.given` does)
  implicit def appZeroFuture[A](implicit z: alleycats.Zero[A], ec: scala.concurrent.ExecutionContext): alleycats.Zero[scala.concurrent.Future[A]] =
    alleycats.Zero(scala.concurrent.Future.successful(z.zero))
  implicit val appEqLang: cats.Eq[play.api.i18n.Lang] =
    cats.Eq.fromUniversalEquals
  implicit val appZeroInt: alleycats.Zero[Int] = alleycats.Zero(0)
  implicit val appZeroLong: alleycats.Zero[Long] = alleycats.Zero(0L)
  implicit val appZeroString: alleycats.Zero[String] = alleycats.Zero("")
  implicit val appZeroBoolean: alleycats.Zero[Boolean] = alleycats.Zero(false)
  implicit def appZeroOption[A]: alleycats.Zero[Option[A]] = alleycats.Zero(None)
  implicit def appZeroList[A]: alleycats.Zero[List[A]] = alleycats.Zero(Nil)
  implicit def appZeroSeq[A]: alleycats.Zero[Seq[A]] = alleycats.Zero(Nil)
  implicit def appZeroSet[A]: alleycats.Zero[Set[A]] = alleycats.Zero(Set.empty)
  implicit def appZeroMap[K, V]: alleycats.Zero[Map[K, V]] = alleycats.Zero(Map.empty)
  implicit val appZeroJsObject: alleycats.Zero[play.api.libs.json.JsObject] =
    alleycats.Zero(play.api.libs.json.JsObject.empty)
  implicit val appZeroUnit: alleycats.Zero[Unit] = alleycats.Zero(())
}
