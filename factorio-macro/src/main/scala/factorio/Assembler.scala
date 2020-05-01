package factorio

import factorio.`macro`.AssemblerMacro

import scala.language.experimental.macros

final class Assembler[T] {
  def apply[R <: Recipe](r: R): () => T = macro AssemblerMacro.assemble[T, R]
}
