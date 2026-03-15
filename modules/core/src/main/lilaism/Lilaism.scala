package lila.core.lilaism

object Lilaism extends LilaLibraryExtensions:

  def some[A](a: A): Option[A] = Some(a)

  trait StringValue extends Any:
    def value: String
    override def toString = value
  given cats.Show[StringValue] = cats.Show.show(_.value)

  given cats.Show[play.api.mvc.Call] = cats.Show.show(_.url)

  // move somewhere else when we have more Eqs
  given cats.Eq[play.api.i18n.Lang] = cats.Eq.fromUniversalEquals

  import play.api.Mode
  extension (mode: Mode)
    inline def isDev  = mode == Mode.Dev
    inline def isProd = mode == Mode.Prod
    inline def notProd = mode != Mode.Prod
