package factorio.internal

import factorio.annotations.{ blueprint, provides, replicated, overrides }

import scala.reflect.macros.blackbox
import scala.collection.mutable

class BluerprintAnalyzer[+C <: blackbox.Context, R : C#WeakTypeTag](override val c: C) extends Toolbox[C] {
  import c.universe._

  private[internal] val blueprintBaseType = weakTypeTag[R].tpe

  private[internal] lazy val blueprintBaseClassSymbols: List[Symbol] =
    blueprintBaseType.baseClasses.filter(_.isAnnotatedWith(typeOf[blueprint]))

  private[internal] case class Binder(`type`: Type, props: Props, isOverride: Boolean)
  private[internal] case class Provider(symbol: Symbol, props: Props, isOverride: Boolean)

  private[internal] case class Blueprint(
    binders: Map[Named[Type], Binder],
    providers: Map[Named[Type], Provider]
  )

  private[internal] def isBinder(m: Symbol): Boolean =
    !m.isSynthetic &&
      typeOf[factorio.Binder[_, _]].erasure ==
        m.typeSignature.resultType.dealias.erasure

  private[internal] def isProvider(member: Symbol): Boolean =
    member.isMethod &&
      member.isPublic &&
      member.isAnnotatedWith(typeOf[provides])

  private[internal] def blueprintAnalysis: Blueprint = {

    val binders = mutable.Map.empty[Named[Type], Binder]
    val providers = mutable.Map.empty[Named[Type], Provider]

    for {
      baseClassSymbol <- blueprintBaseClassSymbols
      declaration <- baseClassSymbol.typeSignature.decls
      if !declaration.isConstructor
    } yield {

      val name = declaration.named
      val replicated = declaration.isAnnotatedWith(typeOf[replicated])
      val isOverride = declaration.isAnnotatedWith(typeOf[overrides]) ||
        baseClassSymbol.isAnnotatedWith(typeOf[overrides])

      if (isBinder(declaration)) {

        val targetType :: bindedType :: Nil =
          declaration.typeSignature.resultType.dealias.typeArgs
            .map(_.dealias)

        val named = Named(targetType, name)
        val props = Props(name, replicated)

        binders.get(named) match {

          case Some(Binder(_, props, true)) if isOverride =>
            c.abort(
              c.enclosingPosition,
              Log("Found multiple binders with {} for [{}], cannot figure out which one to use.", "`@overrides`", named)(Nil)
            )

          case Some(Binder(_, props, true)) =>
          // previous one is an override, skipping

          case Some(Binder(_, props, false)) if !isOverride =>
            c.warning(
              c.enclosingPosition,
              Log("Found multiple binders for [{}], consider using {} to force select one of them.", named, "`@overrides`")(Nil)
            )

          case _ =>
            binders += (named -> Binder(bindedType, props, isOverride))
        }

      } else if (isProvider(declaration)) {

        val targetType = declaration.typeSignature.resultType.dealias
        val named = Named(targetType, name)
        val props = Props(name, replicated)

        providers.get(named) match {

          case Some(Provider(_, props, true)) if isOverride =>
            c.abort(
              c.enclosingPosition,
              Log("Found multiple providers with {} for [{}], cannot figure out which one to use.", "`@overrides`", named)(Nil)
            )

          case Some(Provider(_, props, true)) =>
          // previous one is an override, skipping

          case Some(Provider(_, propsi, false)) if !isOverride =>
            c.warning(
              c.enclosingPosition,
              Log("Found multiple providers for [{}], consider using {} to force select one of them.", named, "`@overrides`")(Nil)
            )

          case _ =>
            providers += (named -> Provider(declaration, props, isOverride))
        }
      }
    }

    Blueprint(binders.to(Map), providers.to(Map))
  }
}
