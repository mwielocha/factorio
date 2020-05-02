package factorio.`macro`

import factorio.annotations.named

import scala.reflect.macros.blackbox

trait Toolbox[+C <: blackbox.Context] {

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

  private[`macro`] def uname(targetType: Type, name: Option[String] = None): TermName = {
    import c.universe._
    val baseClassName = targetType.baseClasses.head.name.toString
    val output = c.freshName((Seq(firstCharLowerCase(baseClassName)) ++ name).mkString("@"))
    TermName(output)
  }

  private[`macro`] implicit class SymbolExtension(s: Symbol) {

    def isAnnotatedWith(annotations: Type*): Boolean =
      s.annotations.exists(t => annotations.contains(t.tree.tpe))

    def named: Option[String] = {
      val scala = s.annotations
        .filter(_.tree.tpe == typeOf[named])
        .flatMap(_.tree.children.tail.headOption)
        .headOption

      scala
        .orElse {
          // fallback to javax.inject.Named for backwards compatibility
          s.annotations
            .filter(_.tree.tpe == typeOf[javax.inject.Named])
            .flatMap(_.tree.children.tail.headOption)
            .headOption
            .flatMap(_.children.lastOption)
        }.map(_.toString()).map(x => x.substring(1, x.length - 1))
    }
  }

  private[`macro`] def firstCharLowerCase(s: String): String =
    if (s.nonEmpty) s"${Character.toLowerCase(s.charAt(0))}${s.substring(1)}" else s
}
