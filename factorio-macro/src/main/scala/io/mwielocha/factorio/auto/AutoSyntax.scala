package io.mwielocha.factorio.auto

import scala.language.experimental.macros

trait AutoSyntax {

  // this is the default so, implicit
  implicit def lazySingleton[T]: Assembler[T] = macro AssemblerMacro.lazySingleton[T]

  def eagerSingleton[T]: Assembler[T] = macro AssemblerMacro.eagerSingleton[T]

  def replicated[T]: Assembler[T] = macro AssemblerMacro.replicated[T]

}
