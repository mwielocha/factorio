package factorio

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

final class Assembler[T] {
  def apply[R <: Recipe](r: R): () => T = macro Assembler.assemble[T, R]
}

object Assembler {

  def assemble[T : c.WeakTypeTag, R : c.WeakTypeTag](c: blackbox.Context)(r: c.Expr[R]): c.Expr[() => T] = {
    val assembler = new factorio.`macro`.Assembler[c.type, T, R](c)
    c.Expr[() => T](assembler(r))
  }
}
