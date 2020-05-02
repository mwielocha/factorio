package factorio.`macro`

import scala.reflect.macros.blackbox

object AssemblerMacro {

  def assemble[T : c.WeakTypeTag, B : c.WeakTypeTag](c: blackbox.Context)(blueprint: c.Expr[B]): c.Expr[() => T] = {
    c.Expr[() => T] {
      new factorio.`macro`.Assembler[c.type, T, B](c)
        .assemble(blueprint)
    }
  }
}
