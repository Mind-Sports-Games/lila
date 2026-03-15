package lila.core.lilaism

import cats.mtl.Raise

trait CoreExports:

  type Fu[A] = Future[A]
  type Funit = Fu[Unit]
  type FuRaise[E, +A] = Raise[Fu, E] ?=> Fu[A]
  type PairOf[A] = (A, A)
  type Update[A] = A => A

  export scala.concurrent.{ ExecutionContextExecutor as Executor, Future, Promise }
  export scala.concurrent.duration.{ DurationInt, DurationLong, IntMult, Duration, FiniteDuration }
  export org.apache.pekko.actor.Scheduler
  export java.time.{ Instant, LocalDateTime }

  export scalalib.newtypes.{ given, * }
  export scalalib.zeros.given
  export scalalib.extensions.{ given, * }
  export scalalib.json.extensions.*
  export scalalib.json.Json.given_Zero_JsObject
  export scalalib.time.*
  export scalalib.model.{ Max, MaxPerPage, MaxPerSecond }

  // Random wrapper with secureString for backwards compatibility
  object Random:
    def secureString(len: Int): String = scalalib.SecureRandom.nextString(len)
    def nextString(len: Int): String = scalalib.SecureRandom.nextString(len)
    def nextInt(n: Int): Int = scalalib.SecureRandom.nextInt(n)
    def nextBoolean(): Boolean = scalalib.SecureRandom.nextBoolean()
    def shuffle[T, C](xs: IterableOnce[T])(using scala.collection.BuildFrom[xs.type, T, C]): C =
      scalalib.SecureRandom.shuffle(xs)

  export cats.syntax.all.*
  export cats.{ Eq, Show }
  export cats.data.NonEmptyList

  export cats.mtl.syntax.raise.*

object Core extends CoreExports
