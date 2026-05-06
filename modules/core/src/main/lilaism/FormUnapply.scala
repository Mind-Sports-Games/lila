package lila.core.lilaism

import scala.deriving.Mirror

// Typeclass backing `Lilaism.unapply` for Play form mappings.
// Companion object extends FormUnapplyLow so `single` has higher priority than `multi`.
sealed trait FormUnapply[P, Out] {
  def apply(p: P): Option[Out]
}

trait FormUnapplyLow {
  // Multi-field case classes: MirroredElemTypes is a Tuple with 2+ elements.
  given multi[P <: Product, T <: Tuple](
      using m: Mirror.ProductOf[P] { type MirroredElemTypes = T }
  ): FormUnapply[P, T] = new FormUnapply[P, T] {
    def apply(p: P): Option[T] = Some(Tuple.fromProduct(p).asInstanceOf[T])
  }
}

object FormUnapply extends FormUnapplyLow {
  // Single-field: unwrap 1-element tuple so Play's mapping1 gets Option[A] not Option[A *: EmptyTuple].
  given single[P <: Product, A](
      using m: Mirror.ProductOf[P] { type MirroredElemTypes = A *: EmptyTuple }
  ): FormUnapply[P, A] = new FormUnapply[P, A] {
    def apply(p: P): Option[A] = Some(p.productElement(0).asInstanceOf[A])
  }
}
