package factorio.`macro`

import scala.reflect.macros.blackbox

object AssemblerMacro {

  def assemble[T : c.WeakTypeTag, R : c.WeakTypeTag](c: blackbox.Context)(r: c.Expr[R]): c.Expr[() => T] = {
    c.Expr[() => T] {
      new factorio.`macro`.Assembler[c.type, T, R](c)
        .assemble(r)
    }
  }
}
