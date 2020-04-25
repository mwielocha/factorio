package io.mwielocha.factorio

trait Assembler[T] {
  def assemble: T
}

object Assembler {
  def apply[T](make: => T): Assembler[T] =
    new Assembler[T] {
      override lazy val assemble: T = make
    }
}
