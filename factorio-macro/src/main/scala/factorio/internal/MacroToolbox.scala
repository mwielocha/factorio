package factorio.internal

import scala.reflect.macros.blackbox

trait MacroToolbox[C <: blackbox.Context] extends Toolbox {

  val c: C
  import c.universe._

  private[internal] object Error {

    def apply(msg: String): String =
      s"\n${Console.YELLOW}[Factorio]: ${Console.RED}$msg${Console.RESET}\n\n"
  }

  private[internal] def discoverConstructor(targetType: Type): Option[Symbol] = {
    lazy val constructors = targetType.members
      .filter(m => m.isMethod && m.asMethod.isConstructor && m.isPublic)
      .filterNot(_.asMethod.fullName.endsWith("$init$"))
    constructors.find(_.asMethod.isPrimaryConstructor)
  }

  private[internal] def createUniqueName(targetType: Type): TermName =
    createUniqueLabel(targetType, None)

  private[internal] def createUniqueLabel(targetType: Type, sufx: Option[String]): TermName = {
    import c.universe._
    val baseClassName = targetType.baseClasses.head.name.toString
    val name = c.freshName((Seq(firstCharLowerCase(baseClassName)) ++ sufx).mkString("@"))
    TermName(name)
  }

  private[internal] def isAnnotated(m: Symbol, a: Type): Boolean =
    m.asMethod.annotations.exists(_.tree.tpe == a)

  private[internal] def extractLabel(m: Symbol): Option[String] = {
    m.annotations
      .filter(_.tree.tpe == typeOf[javax.inject.Named])
      .flatMap(_.tree.children.tail.headOption)
      .headOption.flatMap(_.children.lastOption)
      .map(_.toString()).map(x => x.substring(1, x.length - 1))
  }
}
