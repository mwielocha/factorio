package factorio.`macro`

import factorio.annotations.{ provides, replicated }

import scala.reflect.macros.blackbox
import scala.collection.mutable

class Assembler[C <: blackbox.Context, T : C#WeakTypeTag, B : C#WeakTypeTag](override val c: C) extends Toolbox[C] {

  import c.universe._

  private final val bluerprintAnalyzer =
    new BluerprintAnalyzer[c.type, B](c)(weakTypeTag[B])

  import bluerprintAnalyzer._

  private lazy val rootType = weakTypeTag[T].tpe.dealias
  private lazy val blueprintType = weakTypeTag[B].tpe.dealias

  private lazy val blueprintAnalysis = bluerprintAnalyzer.blueprintAnalysis

  private lazy val blueprintTermName = uname(blueprintType)

  type ArgumentLists = List[List[TermName]]
  type ParameterLists = List[List[Type]]
  type NamedParameterLists = List[List[Named[Type]]]

  private abstract class Assembly {
    def `type`: Type
    def props: Props
    def parameterLists: NamedParameterLists
    lazy val tname = uname(`type`, props.name)
    def assemble: ArgumentLists => Tree
  }

  private case class ProvidedAssembly(
    `type`: Type,
    symbol: Symbol,
    props: Props,
    parameterLists: NamedParameterLists
  ) extends Assembly {

    override val assemble: ArgumentLists => Tree = {
      case args if props.repl =>
        q"""def $tname = $blueprintTermName.$symbol(...$args)"""
      case args =>
        q"""lazy val $tname = $blueprintTermName.$symbol(...$args)"""
    }
  }

  private case class ConstructorAssembly(
    `type`: Type,
    props: Props,
    parameterLists: NamedParameterLists
  ) extends Assembly {

    override val assemble: ArgumentLists => Tree = {
      case args if props.repl =>
        q"""def $tname = new ${`type`}(...$args)"""
      case args =>
        q"""lazy val $tname = new ${`type`}(...$args)"""
    }
  }

  private case class AssemblyTree(tname: TermName, tree: Tree, root: Boolean = false)

  def assemble(recipe: c.Expr[B]): Tree = {

    val stopWatch = StopWatch()

    val assemblies = assembleTrees
    val trees = assemblies.map(_.tree)

    val root = assemblies
      .find(_.root)
      .map(_.tname)
      .getOrElse {
        c.abort(
          c.enclosingPosition,
          Error("Don't know how to construct an instance of [{}]", rootType)(Nil)
        )
      }

    val output =
      q"""() => {
          val $blueprintTermName: $blueprintType = $recipe
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

  private def assembleTrees: Set[AssemblyTree] = {

    val trees = mutable.HashSet.empty[AssemblyTree]

    val assemblies = analyzeType(
      rootType,
      Props(root = true),
      Seq.empty,
      Map.empty
    )

    // values holds all types that we need to assemble
    for {
      assembly <- assemblies.values
      arguments = assembly.parameterLists.map {
        _.map { named =>
          assemblies
            .getOrElse(
              named,
              c.abort(
                c.enclosingPosition,
                Error(s"Couldn't create an instance of [{}] when constructing [{}]", named, assembly.`type`)(Nil)
              )
            ).tname
        }
      }
      tree = AssemblyTree(
        assembly.tname,
        assembly.assemble(arguments),
        root = assembly.props.root
      )
    } yield trees add tree

    trees.to(Set)
  }

  private def analyzeType(
    `type`: Type, // type
    props: Props,
    rootPath: Seq[Type] = Seq.empty, //path from root
    output: Map[Named[Type], Assembly] = Map.empty // output
  ): Map[Named[Type], Assembly] = {

    val Blueprint(binders, providers) = blueprintAnalysis

    // first, check if this is a binded type
    val bindedType = binders.view
      .mapValues(_.`type`)
      .getOrElse(Named(`type`, props.name), `type`)

    // let's also check what annotations does this type has
    val newProps = props.copy(
      repl = bindedType.typeSymbol
        .isAnnotatedWith(typeOf[replicated])
    )

    // second, check if we've already seen this type
    if (rootPath.contains(bindedType)) {
      c.abort(
        c.enclosingPosition,
        Error(s"Circular dependency detected: {}", (rootPath :+ bindedType).mkString(" -> "))(rootPath)
      )
    }

    // ok, we have a type, now let's see if there is a provide for it
    val bindedIndentifier = Named(bindedType, newProps.name)
    val providerOrNone = providers.get(bindedIndentifier)

    providerOrNone match {

      case Some(Provider(symbol, props)) =>
        // we have a provide, let's create an assembly

        val paramLists = symbol.asMethod.paramLists.namedTypeSignatures
        val assembly = ProvidedAssembly(`type`, symbol, newProps || props, paramLists)
        val newOutput = output + (bindedIndentifier -> assembly)

        analyzeParameterLists(paramLists, rootPath :+ `type`, newOutput)

      case None =>
        // no provider means we'll be calling a constructor but can we instantinate this type?

        if (bindedType.typeSymbol.isAbstract) {
          c.abort(
            c.enclosingPosition,
            Error(
              s"Cannot counstruct an instance of an abstract class " +
                s"[{}], provide a concrete class binder or an instance provider.",
              bindedType
            )(rootPath)
          )
        }

        // can we find a contructor then?

        val constructor = discoverConstructor(bindedType).getOrElse(
          c.abort(
            c.enclosingPosition,
            Error(
              s"Cannot find a public constructor for " +
                s"[{}], provide a concrete class binder or an instance provider.",
              bindedType
            )(rootPath)
          )
        )

        // if yes then let's create an assembly

        val paramLists = constructor.asMethod.paramLists.namedTypeSignatures
        val assembly = ConstructorAssembly(bindedType, newProps, paramLists)
        val newOutput = output + (Named(`type`, newProps.name) -> assembly)
        analyzeParameterLists(paramLists, rootPath :+ `type`, newOutput)
    }
  }

  private def analyzeParameterLists(
    parameterLists: NamedParameterLists,
    rootPath: Seq[Type],
    output: Map[Named[Type], Assembly]
  ): Map[Named[Type], Assembly] = {

    parameterLists.foldLeft(output) {
      case (output, list) =>
        list.foldLeft(output) {
          case (output, Named(parameterType, name)) =>
            analyzeType(parameterType, Props(name), rootPath, output)
        }
    }
  }
}
