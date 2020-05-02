package factorio.`macro`

import factorio.annotations.{ provides, replicated }

import scala.reflect.macros.blackbox

class Assembler[C <: blackbox.Context, T : C#WeakTypeTag, B : C#WeakTypeTag](override val c: C) extends Toolbox[C] {

  import c.universe._

  private final val bluerprintAnalyzer =
    new BluerprintAnalyzer[c.type, B](c)(weakTypeTag[B])

  import bluerprintAnalyzer.{ Provider, Binder, Blueprint }

  private case class Assembly(tpe: Type, tname: TermName, const: Symbol, props: Props)
  private case class AssemblyTree(tname: TermName, tree: Tree, root: Boolean = false)

  def assemble(recipe: c.Expr[B]): Tree = {

    val stopWatch = StopWatch()

    val targetType = weakTypeTag[T].tpe.dealias
    val blueprintTargetType = weakTypeTag[B].tpe.dealias
    val blueprintTermName = uname(blueprintTargetType)

    val Blueprint(binders, providers) = bluerprintAnalyzer.blueprintAnalysis

    val assemblies = assemblyTrees(targetType, blueprintTermName, binders, providers)
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
          val $blueprintTermName: $blueprintTargetType = $recipe
         ..$trees
         $root
      }"""

    val verbose = output
      .toString().split("\n")
      .mkString(Console.GREEN, s"\n${Console.GREEN}", Console.RESET)

    val elapsed = stopWatch()

    c.info(
      c.enclosingPosition,
      s"\n${Console.YELLOW}[Factorio]:" +
        s"\n${Console.YELLOW}Done in $elapsed.\n${verbose}",
      force = false
    )

    output
  }

  private def assemblyTrees(
    targetType: Type,
    rname: TermName,
    binders: Map[Named[Type], Binder],
    providers: Map[Named[Type], Provider]
  ): Seq[AssemblyTree] = {
    import c.universe._

    val trees = Seq.empty[AssemblyTree]

    val graph = constructDependencyGraph(binders, providers)(
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
            binders.getOrElse(named, Binder(targetType, props))

          val assemblyTree =
            if (isConstructor) q"""new $typeOrBindedType(...$args)"""
            else q"""$rname.$const(...$args)"""

          val replicated = props.repl || propsOrBindedProps.repl ||
            providers.view.mapValues(_.props.repl).getOrElse(named, false)

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
    binders: Map[Named[Type], Binder] = Map.empty,
    providers: Map[Named[Type], Provider] = Map.empty
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
      repl = props.repl || typeOrBindedType.typeSymbol
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

      val const = props.name match {

        case nameOrNone @ Some(name) if !isBinded =>
          providers.view
            .mapValues(_.sym).getOrElse(
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
            .mapValues(_.sym).getOrElse(
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
}
