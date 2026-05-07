package lila

trait PackageObject {

  export lila.core.lilaism.Lilaism.{ *, given }

  given ecToExecutor(using ec: scala.concurrent.ExecutionContext): Executor =
    ec.asInstanceOf[Executor]

  implicit def toPimpedFuture[A](fua: Fu[A]): lila.base.PimpedFuture[A] =
    new lila.base.PimpedFuture(fua)
  implicit def toPimpedFutureBoolean(fua: Fu[Boolean]): lila.base.PimpedFutureBoolean =
    new lila.base.PimpedFutureBoolean(fua)
  implicit def toPimpedFutureOption[A](fua: Fu[Option[A]]): lila.base.PimpedFutureOption[A] =
    new lila.base.PimpedFutureOption(fua)
}
