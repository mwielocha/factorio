package factorio.`macro`

import scala.reflect.macros.blackbox

trait Toolbox[C <: blackbox.Context] {

  val c: C
  import c.universe._

  private[`macro`] object Error {

    def apply(msg: String): String =
      s"\n${Console.YELLOW}[Factorio]: ${Console.RED}$msg${Console.RESET}\n\n"
  }

  private[`macro`] def discoverConstructor(targetType: Type): Option[Symbol] = {
    lazy val constructors = targetType.members
      .filter(m => m.isMethod && m.asMethod.isConstructor && m.isPublic)
      .filterNot(_.asMethod.fullName.endsWith("$init$"))
    constructors.find(_.asMethod.isPrimaryConstructor)
  }

  private[`macro`] def createUniqueName(targetType: Type): TermName =
    createUniqueLabel(targetType, None)

  private[`macro`] def createUniqueLabel(targetType: Type, sufx: Option[String]): TermName = {
    import c.universe._
    val baseClassName = targetType.baseClasses.head.name.toString
    val name = c.freshName((Seq(firstCharLowerCase(baseClassName)) ++ sufx).mkString("@"))
    TermName(name)
  }

  private[`macro`] def isAnnotated(m: Symbol, a: Type): Boolean =
    m.asMethod.annotations.exists(_.tree.tpe == a)

  private[`macro`] def extractLabel(m: Symbol): Option[String] = {
    m.annotations
      .filter(_.tree.tpe == typeOf[javax.inject.Named])
      .flatMap(_.tree.children.tail.headOption)
      .headOption.flatMap(_.children.lastOption)
      .map(_.toString()).map(x => x.substring(1, x.length - 1))
  }

  private[`macro`] def firstCharLowerCase(s: String): String =
    if (s.nonEmpty) s"${Character.toLowerCase(s.charAt(0))}${s.substring(1)}" else s
}
