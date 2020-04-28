package io.mwielocha.factorio.auto.internal

import scala.reflect.macros.blackbox

trait MacroToolbox {

  private [auto] object Error {
    def apply(msg: String): String =
      s"\n${Console.YELLOW}[Factorio]: ${Console.RED}$msg${Console.RESET}\n\n"
  }

  private [auto] def discoverConstructor[T](c: blackbox.Context)(targetType: c.Type): Option[c.Symbol] = {
    lazy val constructors = targetType.members
      .filter(m => m.isMethod && m.asMethod.isConstructor && m.isPublic)
      .filterNot(_.asMethod.fullName.endsWith("$init$"))
    constructors.find(_.asMethod.isPrimaryConstructor)
  }

  private [auto] def checkForCircularDependencies(c: blackbox.Context): Unit = {

    val targetType = c.macroApplication.tpe.dealias

    val macroTypeArguments = for {
      context <- c.openMacros
      macroType = context.macroApplication.tpe.dealias
      if(macroType.erasure == targetType.erasure)
    } yield macroType.typeArgs.head

    macroTypeArguments.drop(2).foldLeft(Seq.empty[AnyRef]) {
      case (cycle, argument) if cycle contains argument =>
        c.abort(
          c.enclosingPosition,
          Error(s"Circular dependency detected: ${(cycle :+ argument).mkString(" ~> ")}")
        )
      case (cycle, argument) => cycle :+ argument

    }
  }
}
