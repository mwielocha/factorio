package factorio

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.{ Duration, FiniteDuration }

import System.nanoTime

package object internal {

  private[internal] object StopWatch {

    def apply(): () => Duration = {
      val start = nanoTime()
      () => FiniteDuration(nanoTime() - start, TimeUnit.NANOSECONDS)
    }
  }
}
