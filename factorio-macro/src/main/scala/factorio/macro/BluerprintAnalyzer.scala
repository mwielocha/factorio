package factorio.`macro`

import factorio.annotations.{ blueprint, provides, replicated }

import scala.reflect.macros.blackbox
import scala.collection.mutable

class BluerprintAnalyzer[+C <: blackbox.Context, R : C#WeakTypeTag](override val c: C) extends Toolbox[C] {
  import c.universe._

  val blueprintBaseType = weakTypeTag[R].tpe

  lazy val blueprintBaseClassSymbols: List[Symbol] =
    blueprintBaseType.baseClasses.filter(_.isAnnotatedWith(typeOf[blueprint]))

  case class Binder(tpe: Type, props: Props)
  case class Provider(sym: Symbol, props: Props)

  case class Blueprint(
    binders: Map[Named[Type], Binder],
    provider: Map[Named[Type], Provider]
  )

  def isBinder(m: Symbol): Boolean =
    typeOf[factorio.Binder[_, _]].erasure ==
      m.typeSignature.resultType.dealias.erasure

  def isProvider(member: Symbol): Boolean =
    member.isMethod &&
      member.isPublic &&
      member.isAnnotatedWith(typeOf[provides])

  def blueprintAnalysis: Blueprint = {

    val binders = mutable.Map.empty[Named[Type], Binder]
    val providers = mutable.Map.empty[Named[Type], Provider]

    for {
      baseClassSymbol <- blueprintBaseClassSymbols
      declaration <- baseClassSymbol.typeSignature.decls
      if !declaration.isConstructor
    } yield {

      val name = declaration.named
      val replicated = declaration.isAnnotatedWith(typeOf[replicated])

      if (isBinder(declaration)) {

        val targetType :: bindedType :: Nil =
          declaration.typeSignature.resultType.dealias.typeArgs
            .map(_.dealias)

        val named = Named(targetType, name)
        val props = Props(name, replicated)

        binders += (named -> Binder(bindedType, props))

      } else if (isProvider(declaration)) {

        val targetType = declaration.typeSignature.resultType.dealias
        val named = Named(targetType, name)
        val props = Props(name, replicated)

        providers += (named -> Provider(declaration, props))
      }

    }

    Blueprint(binders.to(Map), providers.to(Map))
  }
}
