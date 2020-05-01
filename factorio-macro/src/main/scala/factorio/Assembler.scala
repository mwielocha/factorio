package factorio

import factorio.internal.Assembler

import scala.reflect.macros.blackbox

object Assembler {

  def assemble[T : c.WeakTypeTag, R : c.WeakTypeTag](c: blackbox.Context)(r: c.Expr[R]): c.Expr[() => T] = {
    val assembler = new Assembler[c.type, T, R](c)
    c.Expr[() => T](assembler(r))
  }

}
