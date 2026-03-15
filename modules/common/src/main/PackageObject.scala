package lila

trait PackageObject:

  export lila.core.lilaism.Lilaism.{ *, given }

  given ecToExecutor(using ec: scala.concurrent.ExecutionContext): Executor =
    ec.asInstanceOf[Executor]

  object makeTimeout:
    import org.apache.pekko.util.Timeout
    import scala.concurrent.duration.*

    def apply(duration: FiniteDuration): Timeout = Timeout(duration)
    def millis(s: Int): Timeout  = Timeout(s.millis)
    def seconds(s: Int): Timeout = Timeout(s.seconds)
    def minutes(m: Int): Timeout = Timeout(m.minutes)

    val short: Timeout     = seconds(1)
    val large: Timeout     = seconds(5)
    val larger: Timeout    = seconds(30)
    val veryLarge: Timeout = minutes(5)
    val halfSecond: Timeout = millis(500)

    given Timeout = short
