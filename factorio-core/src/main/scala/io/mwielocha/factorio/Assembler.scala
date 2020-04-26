package io.mwielocha.factorio

import scala.reflect.ClassTag

trait Assembler[T] {
  def assemble: T
}

object Assembler {

  def apply[T](make: () => T)(
    implicit
    classTag: ClassTag[T],
    assembly: Assembly
  ): Assembler[T] = `lazy`(make)

  def `lazy`[T](make: () => T)(
    implicit
    classTag: ClassTag[T],
    assembly: Assembly
  ): Assembler[T] =
    assembly.getOrRegister {
      () => new Assembler[T] {
        override lazy val assemble: T = make()
      }
    }

  def eager[T](make: () => T)(
    implicit
    classTag: ClassTag[T],
    assembly: Assembly
  ): Assembler[T] =
    assembly.getOrRegister {
      () => new Assembler[T] {
        override val assemble: T = make()
      }
    }

  def replicated[T](make: () => T)(
    implicit
    classTag: ClassTag[T],
    assembly: Assembly
  ): Assembler[T] =
    assembly.getOrRegister {
      () => new Assembler[T] {
        override def assemble: T = make()
      }
    }
}
