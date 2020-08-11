package factorio.internal

import factorio.annotations.{ blueprint, provides, replicated, overrides, binds }

import scala.reflect.macros.blackbox
import scala.collection.mutable
import factorio.Binder

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
    typeOf[factorio.Binder[_, _]].erasure ==
      m.typeSignature.resultType.dealiasAll.erasure &&
      // accessor methods don't hold annotation so are not interesting to us
      !(m.isPublic && m.isTerm && m.asTerm.isGetter && !m.asTerm.isVal)

  private[internal] def isProvider(m: Symbol, scope: MemberScope): Boolean = {

    m.isMethod &&
    m.isPublic &&
    m.isAnnotatedWith(typeOf[provides]) | {

      m.asTerm.isGetter && (
        scope.exists { x =>
          x != m &&
          s"${x.name}".trim == s"${m.name}".trim &&
          x.isTerm &&
          x.asTerm.isVal &&
          x.isAnnotatedWith(typeOf[provides])
        }
      )
    }
  }

  private[internal] def analyzeBaseClassBinders(
    annotations: List[Annotation],
    binders: Map[Named[Type], Binder]
  ): Map[Named[Type], Binder] = {

    val output = mutable.Map.empty[Named[Type], Binder]

    for {
      annotation <- annotations
      if (annotation.tree.tpe.erasure == typeOf[binds[_]].erasure)
      annotationTree = annotation.tree
      targetType :: bindedType :: Nil = annotationTree.tpe.typeArgs.head.typeArgs.map(_.dealiasAll)
      binder @ Binder(_, Props(name, _, _), isOverride) = annotationTree.children.tail.foldLeft(Binder(bindedType, Props(), false)) {

        case (binder @ Binder(_, props, _), q"factorio.this.`package`.replicated") =>
          binder.copy(props = props || Props(repl = true))

        case (binder, q"factorio.this.`package`.overrides") =>
          binder.copy(isOverride = true)

        case (binder @ Binder(_, Props(Some(_), _, _), _), q"factorio.this.`package`.named.apply(${Literal(Constant(name: String)) })") =>
          c.abort(annotationTree.pos, Log("`@binds` annotation accepts only one `named` parameter.")(Nil))

        case (binder @ Binder(_, props, _), q"factorio.this.`package`.named.apply(${Literal(Constant(name: String)) })") =>
          binder.copy(props = props || Props(Some(name)))

        case (binder, _) =>
          c.abort(annotationTree.pos, Log("`@binds` annotation requires stable parameter values.")(Nil))
      }
      named = Named(targetType, name)
    } yield {

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
          output += (named -> binder)
      }

    }

    output.to(Map)
  }

  private[internal] def blueprintAnalysis: Blueprint = {

    val binders = mutable.Map.empty[Named[Type], Binder]
    val providers = mutable.Map.empty[Named[Type], Provider]

    for {
      baseClassSymbol <- blueprintBaseClassSymbols
      _ = binders ++= analyzeBaseClassBinders(baseClassSymbol.annotations, binders.to(Map))
      declarations = baseClassSymbol.typeSignature.decls
      declaration <- declarations.filterNot(_.isConstructor)
    } yield {

      val name = declaration.named
      val replicated = declaration.isAnnotatedWith(typeOf[replicated])
      val isOverride = declaration.isAnnotatedWith(typeOf[overrides]) ||
        baseClassSymbol.isAnnotatedWith(typeOf[overrides])

      if (isBinder(declaration)) {

        val targetType :: bindedType :: Nil =
          declaration.typeSignature.resultType.dealiasAll.typeArgs.map(_.dealiasAll)

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

      } else if (isProvider(declaration, declarations)) {

        val targetType = declaration.asMethod.returnType.dealiasAll

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
              Log("Found multiple providers for [{}], consider using {} to force-select one of them.", named, "`@overrides`")(Nil)
            )

          case _ =>
            providers += (named -> Provider(declaration, props, isOverride))
        }
      }
    }

    Blueprint(binders.to(Map), providers.to(Map))
  }
}
