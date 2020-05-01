package factorio

import scala.language.experimental.macros

trait Syntax {

  def assemble[T, R](r: R): () => T = macro Assembler.assemble[T, R]

}
