package io.mwielocha.factorio.auto.internal

import com.sun.tools.classfile.TypeAnnotation.TargetType

import scala.reflect.macros.blackbox

trait MacroToolbox extends Toolbox {

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

  private [auto] def name(c: blackbox.Context)(targetType: c.Type): c.TermName = label(c)(targetType, None)

  private [auto] def label(c: blackbox.Context)(targetType: c.Type, sufx: Option[String]): c.TermName = {
    import c.universe._
    val baseClassName = targetType.baseClasses.head.name.toString
    val name = c.freshName((Seq(firstCharLowerCase(baseClassName)) ++ sufx).mkString("@"))
    TermName(name)
  }

  private [auto] def isAnnotated(c: blackbox.Context)(m: c.Symbol, a: c.Type): Boolean =
    m.asMethod.annotations.exists(_.tree.tpe == a)

  private [auto] def extractLabel(c: blackbox.Context)(m: c.Symbol): Option[String] = {
    import c.universe._
    m.annotations
      .filter(_.tree.tpe == typeOf[javax.inject.Named])
      .flatMap(_.tree.children.tail.headOption)
      .headOption.flatMap(_.children.lastOption)
      .map(_.toString()).map(x => x.substring(1, x.length - 1))
  }
}
