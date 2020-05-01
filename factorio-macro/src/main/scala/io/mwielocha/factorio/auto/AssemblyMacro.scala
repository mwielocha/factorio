package io.mwielocha.factorio.auto

import io.mwielocha.factorio.auto.internal.Assembler

import scala.reflect.macros.blackbox

object AssemblyMacro {

  def assemble[T : c.WeakTypeTag, R : c.WeakTypeTag](c: blackbox.Context)(recipe: c.Expr[R]): c.Expr[() => T] = {
    val assembler = new Assembler[c.type, T, R](c)
    c.Expr[() => T](assembler(recipe))
  }

}
