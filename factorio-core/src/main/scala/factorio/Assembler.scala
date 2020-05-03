package factorio

import factorio.internal.AssemblerMacro

import scala.language.experimental.macros

final class Assembler[T] {
  def apply[B](blueprint: B): () => T = macro AssemblerMacro.assemble[T, B]
}

object Assembler {
  def apply[T] = new Assembler[T]
}
