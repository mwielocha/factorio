package factorio.internal

import factorio.annotations.named

import scala.reflect.macros.blackbox
import scala.reflect.api.Constants
import java.util.regex.Matcher

private[internal] trait Toolbox[+C <: blackbox.Context] {

  val c: C
  import c.universe._

  def debug(a: Any): Unit =
    c.echo(c.enclosingPosition, s"$a")

  private[internal] object Log {

    def apply(msg: String, args: Any*)(rootPath: Seq[Type] = Nil): String = {
      val formatted = args.foldLeft(msg) {
        case (msg, arg) =>
          msg.replaceFirst("\\{\\}", Matcher.quoteReplacement(s"$arg".yellow))
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

  private val isNamedAnnotation: Type => Boolean =
    Set(typeOf[named], typeOf[javax.inject.Named])

  private[internal] def namedValue(s: Symbol): Option[String] =
    namedValue(s, s.annotations)

  private[internal] def namedValue(s: Symbol, annotations: List[Annotation]): Option[String] = {
    annotations
      .map(_.tree)
      .filter(x => isNamedAnnotation(x.tpe.dealias))
      .flatMap { annotation =>
        annotation.children.tail.headOption
          .flatMap(namedValue(annotation, _))
      }
      .headOption
  }

  private[internal] def namedValue(annotation: Tree, argument: Tree): Option[String] = argument match {
    case q"value = ${Literal(Constant(name: String)) }" => Some(name) // for javax.inject.Named
    case q"name = ${Literal(Constant(name: String)) }"  => Some(name) // for factorio.named
    case Literal(Constant(name: String))                => Some(name)
    case x =>
      c.abort(
        c.enclosingPosition,
        Log(
          s"Error analyzing [{}] annotation. Argument [{}] is not a stable identifier, " +
            s"consider using either a string literal or a final, static val.",
          annotation.tpe,
          x
        )(Nil)
      )
  }

  private[internal] implicit class SymbolExtension(s: Symbol) {

    locally {
      val _ = s.typeSignature // we need this because otheriwse scalac looses annotation information
    }

    def isAnnotatedWith(annotations: Type*): Boolean =
      s.annotations.exists(t => annotations.contains(t.tree.tpe))

    def named: Option[String] =
      s.annotations
        .map(_.tree)
        .filter(x => isNamedAnnotation(x.tpe.dealias))
        .flatMap { annotation =>
          annotation.children.tail.headOption
            .flatMap(namedValue(annotation, _))
        }
        .headOption

  }

  private[internal] implicit class TypeExtension(tpe: Type) {

    def dealiasAll: Type =
      tpe.dealias.map(_.dealias)

  }

  private[internal] def function(tname: TermName, resultType: Type, of: Tree) = q"def $tname: $resultType = $of"
  private[internal] def lazyValue(tname: TermName, resultType: Type, of: Tree) = q"lazy val $tname: $resultType = $of"

  private[internal] def firstCharLowerCase(s: String): String =
    if (s.nonEmpty) s"${Character.toLowerCase(s.charAt(0))}${s.substring(1)}"
    else s

}
