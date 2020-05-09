package factorio.internal

import factorio.annotations.named

import scala.reflect.macros.blackbox

private[internal] trait Toolbox[+C <: blackbox.Context] {

  val c: C
  import c.universe._

  def debug(a: Any): Unit =
    c.echo(c.enclosingPosition, s"$a")

  private[internal] object Log {

    def apply(msg: String, args: Any*)(rootPath: Seq[Type] = Nil): String = {
      val formatted = args.foldLeft(msg) {
        case (msg, arg) =>
          msg.replaceFirst("\\{}", s"$arg".yellow)
      }

      val context = rootPath match {
        case Nil => ""
        case rootPath =>
          val header = new StringBuilder(s"\n")
          header.append(s"While analyzing path:\n")
          rootPath.zipWithIndex
            .foldLeft(header) {
              case (acc, (node, index)) =>
                acc.append(Console.RED)
                Seq
                  .fill(index)(" ")
                  .foreach(acc.append)
                acc
                  .append(index)
                  .append(": ")
                  .append(node)
                  .append(" ->")
                  .append("\n")
            }.append("\n")
      }

      s"\n\n ${"[Factorio]:".yellow} $formatted \n$context"
    }

  }

  private[internal] def discoverConstructor(targetType: Type): Option[Symbol] = {
    lazy val constructors = targetType.members
      .filter(m => m.isMethod && m.asMethod.isConstructor && m.isPublic)
      .filterNot(_.asMethod.fullName.endsWith("$init$"))
    constructors.find(_.asMethod.isPrimaryConstructor)
  }

  private[internal] def uname(targetType: Type, name: Option[String] = None): TermName = {
    import c.universe._
    val baseClassName = targetType.baseClasses.head.name.toString
    val output = c.freshName((Seq(firstCharLowerCase(baseClassName)) ++ name).mkString("@"))
    TermName(output)
  }

  private[internal] implicit class SymbolExtension(s: Symbol) {

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

  private[internal] implicit class SymbolListsExtension(symbolLists: List[List[Symbol]]) {

    def namedBindedTypeSignatures(bindedTypes: Map[Named[Type], Type]): List[List[Named[Type]]] =
      symbolLists.map {
        for {
          symbol <- _
          name = symbol.named
          symbolType = symbol.typeSignature.dealias
          identifier = Named(symbolType, name)
          bindedType = bindedTypes
            .get(identifier)
            .getOrElse(symbolType)
        } yield Named(bindedType, name)
      }
  }

  private[internal] def function(tname: TermName, resultType: Type, of: Tree) = q"def $tname: $resultType = $of"
  private[internal] def lazyValue(tname: TermName, resultType: Type, of: Tree) = q"lazy val $tname: $resultType = $of"

  private[internal] def firstCharLowerCase(s: String): String =
    if (s.nonEmpty) s"${Character.toLowerCase(s.charAt(0))}${s.substring(1)}"
    else s

}
