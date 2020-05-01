package factorio.`macro`

import factorio.annotations.{ provides, replicated }

import scala.reflect.macros.blackbox

class Assembler[C <: blackbox.Context, T : C#WeakTypeTag, R : C#WeakTypeTag](override val c: C) extends Toolbox[C] {

  import c.universe._

  private case class Named(tpe: Type, name: Option[String])
  private case class Assembly(tpe: Type, tname: TermName, const: Symbol, props: Props)
  private case class AssemblyTree(tname: TermName, tree: Tree, root: Boolean = false)

  private case class Props(
    name: Option[String] = None,
    replicated: Boolean = false,
    root: Boolean = false
  )

  private case class Binder(targetType: Type, props: Props)
  private case class Provider(symbol: Symbol, props: Props)

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
  ): Seq[AssemblyTree] = {
    import c.universe._

    val trees = Seq.empty[AssemblyTree]

    val graph = constructDependencyGraph(bind, prov)(
      targetType,
      Props(root = true),
      Seq.empty,
      Seq.empty
    ).reverse

    graph.foldLeft(trees) {
      case (trees, Assembly(targetType, fname, const, props)) =>
        trees :+ {
          val args = const.asMethod.paramLists.map {
            _.flatMap { param =>
              val named = param.named
              val parameterType = param.typeSignature.dealias

              graph.find {
                case Assembly(targetType, _, _, props) =>
                  targetType == parameterType && named == props.name
              } match {
                case Some(Assembly(_, name, _, _)) =>
                  Some(q"""$name""")
                case None => None
              }
            }
          }

          val named = Named(targetType, props.name)

          val isConstructor = const.asMethod.isConstructor

          val Binder(typeOrBindedType, propsOrBindedProps) =
            bind.getOrElse(named, Binder(targetType, props))

          val assemblyTree =
            if (isConstructor) q"""new $typeOrBindedType(...$args)"""
            else q"""$rname.$const(...$args)"""

          val replicated = props.replicated || propsOrBindedProps.replicated ||
            prov.view.mapValues(_.props.replicated).getOrElse(named, false)

          AssemblyTree(
            fname,
            if (replicated) {
              q"""def $fname: $targetType = $assemblyTree"""
            } else q"""lazy val $fname: $targetType = $assemblyTree""",
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
    output: Seq[Assembly] = Seq.empty // output
  ): Seq[Assembly] = {

    val isBinded = binders.contains(Named(targetType, props.name))

    val Binder(typeOrBindedType, propsOrBindedProps) =
      binders.getOrElse(Named(targetType, props.name), Binder(targetType, props))

    val propsWithReplicated = propsOrBindedProps.copy(
      replicated = props.replicated || typeOrBindedType.typeSymbol
        .isAnnotatedWith(typeOf[replicated])
    )

    if (path.contains(typeOrBindedType))
      c.abort(c.enclosingPosition, Error(s"Circular dependency detected: ${(path :+ typeOrBindedType).mkString(" -> ")}"))

    val alreadyVisited = output.view
      .map {
        case Assembly(visitedType, _, _, Props(name, _, _)) =>
          visitedType -> name
      }.to(Set)

    if (!alreadyVisited(typeOrBindedType -> props.name)) {

      println(s"$typeOrBindedType (${props.name}) - isBinded: " + isBinded)

      val const = props.name match {

        case nameOrNone @ Some(name) if !isBinded =>
          providers.view
            .mapValues(_.symbol).getOrElse(
              Named(typeOrBindedType, nameOrNone),
              c.abort(
                c.enclosingPosition,
                Error(
                  s"Proivider not found for an instance of " +
                    s"[${Console.YELLOW}$typeOrBindedType${Console.RED}] " +
                    s"discriminated with [${Console.YELLOW}$name${Console.RED}]"
                )
              )
            )

        case _ =>
          providers.view
            .mapValues(_.symbol).getOrElse(
              Named(typeOrBindedType, propsWithReplicated.name),
              discoverConstructor(typeOrBindedType)
                .getOrElse {
                  c.abort(
                    c.enclosingPosition,
                    Error(
                      s"Cannot construct an instance of " +
                        s"[${Console.YELLOW}$typeOrBindedType${Console.RED}]"
                    )
                  )
                }
            )

      }

      val targetTermName = uname(typeOrBindedType, propsWithReplicated.name)
      val newOut = output :+ Assembly(targetType, targetTermName, const, propsWithReplicated)

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
    } else output
  }

  private def providerLookup(recipeType: Type): Providers = {

    val providers = Map.empty[Named, Provider]

    recipeType.baseClasses.foldLeft(providers) {
      case (acc, baseClassSymbol) =>
        baseClassSymbol.typeSignature.members.foldLeft(acc) {
          case (acc, member) =>
            if (member.isMethod && member.isPublic && member.isAnnotatedWith(typeOf[provides])) {
              val targetType = member.typeSignature.resultType.dealias

              val name = member.named
              val replicated = member.isAnnotatedWith(typeOf[replicated])
              val provider = Provider(member, Props(name, replicated))

              acc + (Named(targetType, name) -> provider)
            } else acc
        }
    }
  }

  private def binderLookup(recipeType: Type): Binders = {
    import c.universe._

    val binders = Map.empty[Named, Binder]

    def isBinder(m: Symbol): Boolean =
      m.typeSignature.resultType.erasure == typeOf[factorio.Binder[_, _]].erasure

    recipeType.baseClasses.foldLeft(binders) {
      case (acc, baseClassSymbol) =>
        baseClassSymbol.typeSignature.members.foldLeft(acc) {
          case (acc, member) =>
            if (member.isTerm && isBinder(member)) {
              val targetType :: bindedType :: Nil = member.typeSignature.resultType.typeArgs
                .map(_.dealias)

              val name = member.named

              val replicated = member.isAnnotatedWith(typeOf[replicated])
              val binder = Binder(bindedType, Props(name, replicated))

              acc + (Named(targetType, name) -> binder)
            } else acc
        }
    }
  }
}
