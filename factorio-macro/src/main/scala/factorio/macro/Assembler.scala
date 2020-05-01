package factorio.`macro`

import factorio.{ Binder, Provides, Replicated }

import scala.reflect.macros.blackbox

class Assembler[C <: blackbox.Context, T : C#WeakTypeTag, R : C#WeakTypeTag](override val c: C) extends Toolbox[C] {

  import c.universe._

  private case class Named(tpe: Type, name: Option[String])
  private case class Assem(tname: TermName, tree: Tree, root: Boolean = false)
  private case class Const(tpe: Type, tname: TermName, const: Symbol, props: Props)
  private case class Props(name: Option[String] = None, replicated: Boolean = false, root: Boolean = false)

  private case class Binder(tpe: Type, props: Props)
  private case class Provider(sym: Symbol, props: Props)

  private type Binders = Map[Named, Binder]
  private type Providers = Map[Named, Provider]

  def assemble(recipe: c.Expr[R]): Tree = {

    val targetType = weakTypeTag[T].tpe.dealias
    val recipeTargetType = weakTypeTag[R].tpe.dealias
    val recipeTermName = uname(recipeTargetType)

    val binders = binderLookup(recipeTargetType)
    val providers = providerLookup(recipeTargetType)

    val assemblies = assemblyTrees(targetType, recipeTermName, binders, providers)
    val trees = assemblies.map(_.tree)

    val root = assemblies
      .find(_.root)
      .map(_.tname)
      .getOrElse {
        c.abort(
          c.enclosingPosition,
          Error("Couldn't find application root.")
        )
      }

    val output =
      q"""() => {
          val $recipeTermName: $recipeTargetType = $recipe
         ..$trees
         $root
      }"""

    println(output)
    output
  }

  private def assemblyTrees(
    targetType: Type,
    rname: TermName,
    bind: Binders,
    prov: Providers
  ): Seq[Assem] = {
    import c.universe._

    val trees = Seq.empty[Assem]

    val graph = constructDependencyGraph(bind, prov)(
      targetType,
      Props(root = true),
      Seq.empty,
      Seq.empty
    ).reverse

    graph.foldLeft(trees) {
      case (trees, Const(targetType, fname, const, props)) =>
        trees :+ {
          val args = const.asMethod.paramLists.map {
            _.flatMap { param =>
              val ptpe = param.typeSignature.dealias
              val plab = param.named

              graph.find {
                case Const(targetType, _, _, props) =>
                  targetType == ptpe && plab == props.name
              } match {
                case Some(Const(_, name, _, _)) =>
                  Some(q"""$name""")
                case None => None
              }
            }
          }

          val named = Named(targetType, props.name)

          val isConstructor = const.asMethod.isConstructor

          val Binder(typeOrBindedType, propsOrBindedProps) =
            bind.getOrElse(named, Binder(targetType, props))

          val constTree =
            if (isConstructor) q"""new $typeOrBindedType(...$args)"""
            else q"""$rname.$const(...$args)"""

          val replicated = props.replicated || propsOrBindedProps.replicated ||
            prov.view.mapValues(_.props.replicated).getOrElse(named, false)

          Assem(
            fname,
            if (replicated) {
              q"""def $fname: $targetType = $constTree"""
            } else q"""lazy val $fname: $targetType = $constTree""",
            propsOrBindedProps.root
          )
        }
    }
  }

  private def constructDependencyGraph(
    binders: Binders = Map.empty,
    providers: Providers = Map.empty
  )(
    targetType: Type, // type
    props: Props,
    path: Seq[Type] = Seq.empty, //path from root
    output: Seq[Const] = Seq.empty // output
  ): Seq[Const] = {

    val Binder(typeOrBindedType, propsOrBindedProps) =
      binders.getOrElse(Named(targetType, props.name), Binder(targetType, props))

    val propsWithReplicated = propsOrBindedProps.copy(
      replicated = props.replicated || typeOrBindedType.typeSymbol
        .isAnnotatedWith(typeOf[Replicated])
    )

    if (path.contains(typeOrBindedType))
      c.abort(c.enclosingPosition, Error(s"Circular dependency detected: ${(path :+ typeOrBindedType).mkString(" -> ")}"))

    val alreadyVisited = output.view
      .map {
        case Const(visitedType, _, _, Props(name, _, _)) =>
          visitedType -> name
      }.to(Set)

    if (alreadyVisited(typeOrBindedType -> propsWithReplicated.name)) output
    else {

      val const = propsWithReplicated.name match {

        case nameOrNone @ Some(name) =>
          providers.view
            .mapValues(_.sym).getOrElse(
              Named(typeOrBindedType, nameOrNone),
              c.abort(
                c.enclosingPosition,
                Error(
                  s"Proivider not found for an instance of [${Console.YELLOW}$typeOrBindedType${Console.RED}] " +
                    s"labeled with [${Console.YELLOW}$name${Console.RED}]"
                )
              )
            )

        case None =>
          providers.view
            .mapValues(_.sym).getOrElse(
              Named(typeOrBindedType, propsWithReplicated.name),
              discoverConstructor(typeOrBindedType)
                .getOrElse {
                  c.abort(c.enclosingPosition, Error(s"Cannot construct an instance of [${Console.YELLOW}$typeOrBindedType${Console.RED}]"))
                }
            )
      }

      val targetTermName = uname(typeOrBindedType, propsWithReplicated.name)
      val newOut = output :+ Const(targetType, targetTermName, const, propsWithReplicated)

      const.asMethod.paramLists.foldLeft(newOut) {
        case (out, list) =>
          list.foldLeft(out) {
            case (out, parameter) =>
              val parameterType = parameter.typeSignature.dealias
              val name = parameter.named
              val props = Props(name)
              constructDependencyGraph(binders, providers)(parameterType, props, path :+ typeOrBindedType, out)
          }

      }
    }
  }

  private def providerLookup(rtpe: Type): Providers = {

    val providers = Map.empty[Named, Provider]

    rtpe.baseClasses.foldLeft(providers) {
      case (acc, clazz) =>
        clazz.typeSignature.members.foldLeft(acc) {
          case (acc, m) =>
            if (m.isMethod && m.isPublic && m.isAnnotatedWith(typeOf[Provides])) {
              val targetType = m.typeSignature.resultType.dealias

              val name = m.named
              val replicated = m.isAnnotatedWith(typeOf[Replicated])
              val provider = Provider(m, Props(name, replicated))

              acc + (Named(targetType, name) -> provider)
            } else acc
        }
    }
  }

  private def binderLookup(rtpe: Type): Binders = {
    import c.universe._

    val binders = Map.empty[Named, Binder]

    def isBinder(m: Symbol): Boolean =
      m.typeSignature.resultType.erasure == typeOf[factorio.Binder[_, _]].erasure

    rtpe.baseClasses.foldLeft(binders) {
      case (acc, clazz) =>
        clazz.typeSignature.members.foldLeft(acc) {
          case (acc, m) =>
            if (m.isMethod && m.isTerm && isBinder(m)) {
              val targetType :: bindedType :: Nil = m.typeSignature.resultType.typeArgs
                .map(_.dealias)

              val name = m.named
              val replicated = m.isAnnotatedWith(typeOf[Replicated])
              val binder = Binder(bindedType, Props(name, replicated))

              acc + (Named(targetType, name) -> binder)
            } else acc
        }
    }
  }
}
