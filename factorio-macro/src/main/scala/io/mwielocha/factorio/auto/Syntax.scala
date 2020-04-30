package io.mwielocha.factorio.auto

import scala.language.experimental.macros

trait Syntax {

  def assemble[T, R](recipe: R): () => T = macro AssemblyMacro.assemble[T, R]

}
