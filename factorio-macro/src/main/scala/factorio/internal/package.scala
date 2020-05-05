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

  implicit class Colored(s: String) {
    import Console._

    /** Colorize the given string foreground to ANSI black */
    def black = BLACK + s + RESET

    /** Colorize the given string foreground to ANSI red */
    def red = RED + s + RESET

    /** Colorize the given string foreground to ANSI green */
    def green = GREEN + s + RESET

    /** Colorize the given string foreground to ANSI yellow */
    def yellow = YELLOW + s + RESET

    /** Colorize the given string foreground to ANSI blue */
    def blue = BLUE + s + RESET

    /** Colorize the given string foreground to ANSI magenta */
    def magenta = MAGENTA + s + RESET

    /** Colorize the given string foreground to ANSI cyan */
    def cyan = CYAN + s + RESET

    /** Colorize the given string foreground to ANSI white */
    def white = WHITE + s + RESET

    /** Colorize the given string background to ANSI black */
    def onBlack = BLACK_B + s + RESET

    /** Colorize the given string background to ANSI red */
    def onRed = RED_B + s + RESET

    /** Colorize the given string background to ANSI green */
    def onGreen = GREEN_B + s + RESET

    /** Colorize the given string background to ANSI yellow */
    def onYellow = YELLOW_B + s + RESET

    /** Colorize the given string background to ANSI blue */
    def onBlue = BLUE_B + s + RESET

    /** Colorize the given string background to ANSI magenta */
    def onMagenta = MAGENTA_B + s + RESET

    /** Colorize the given string background to ANSI cyan */
    def onCyan = CYAN_B + s + RESET

    /** Colorize the given string background to ANSI white */
    def onWhite = WHITE_B + s + RESET

    /** Make the given string bold */
    def bold = BOLD + s + RESET

    /** Underline the given string */
    def underlined = UNDERLINED + s + RESET

    /** Make the given string blink (some terminals may turn this off) */
    def blink = BLINK + s + RESET
  }
}
