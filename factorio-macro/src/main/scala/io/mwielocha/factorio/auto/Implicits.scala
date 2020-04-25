package io.mwielocha.factorio.auto

import io.mwielocha.factorio.Assembler
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object Implicits {

  implicit def assembler[T]: Assembler[T] = macro assemblerImpl[T]

  def assemblerImpl[T](c: blackbox.Context): c.Expr[Assembler[T]] = {
    import c.universe._
    c.Expr(q"""???""")
  }
}
