package io.mwielocha.factorio

import io.mwielocha.factorio

package object auto extends Syntax with AutoSyntax {

  type Assembly = factorio.Assembly
  type Assembler[T] = factorio.Assembler[T]

}
