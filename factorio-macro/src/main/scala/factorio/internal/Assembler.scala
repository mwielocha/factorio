package factorio.internal

import factorio.annotations.replicated

import scala.collection.mutable
import scala.reflect.macros.blackbox
import factorio.annotations.provides

private[internal] class Assembler[C <: blackbox.Context, T : C#WeakTypeTag, B : C#WeakTypeTag](override val c: C) extends Toolbox[C] {

  import c.universe._

  private lazy val settings: Settings = Settings(c.settings)

  private final val bluerprintAnalyzer =
    new BluerprintAnalyzer[c.type, B](c)(weakTypeTag[B])

  import bluerprintAnalyzer._

  private lazy val rootType = weakTypeTag[T].tpe.dealiasAll
  private lazy val blueprintType = weakTypeTag[B].tpe.dealiasAll

  private lazy val blueprintAnalysis = bluerprintAnalyzer.blueprintAnalysis

  private lazy val blueprintTermName = uname(blueprintType)

  private type ArgumentLists = List[List[TermName]]
  private type ParameterLists = List[List[Named[Type]]]

  private case class NamedProps(named: Named[Type], props: Props)

  private abstract class Assembly(
    val `type`: Type,
    val props: Props,
    val parameterLists: ParameterLists
  ) {

    override def toString: String =
      s"Assembly(${`type`},$props,$parameterLists)"

    val tname = uname(`type`, props.name)
    def createTree: ArgumentLists => Tree

    val assignTree: ArgumentLists => Tree =
      args =>
        // if the type is marked as replicated we create a def instead of a lazy val
        if (props.repl) function(tname, `type`, createTree(args))
        else lazyValue(tname, `type`, createTree(args))
  }

  private object Assembly {

    // constructor-based assembly
    def apply(`type`: Type, bindedType: Type, props: Props)(parameterLists: ParameterLists): Assembly =
      new Assembly(`type`, props, parameterLists) {
        override val createTree: ArgumentLists => Tree =
          args => q"new $bindedType(...$args)"
      }

    // blueprint provider-based assembly
    def apply(`type`: Type, symbol: Symbol, props: Props)(parameterLists: ParameterLists): Assembly =
      new Assembly(`type`, props, parameterLists) {
        override val createTree: ArgumentLists => Tree =
          args => q"$blueprintTermName.$symbol(...$args)"
      }
  }

  private case class AssemblyTree(tname: TermName, tree: Tree, root: Boolean = false)

  def assemble(blueprint: c.Expr[B]): Tree = {

    val stopWatch = StopWatch()

    val assemblies = assembleTrees
    val trees = assemblies.map(_.tree)

    val root = assemblies
      .find(_.root)
      .map(_.tname)
      .getOrElse {
        c.abort(
          c.enclosingPosition,
          Log("Don't know how to construct an instance of [{}]", rootType)(Nil)
        )
      }

    val output =
      q"""() => {
          val $blueprintTermName: $blueprintType = $blueprint
         ..$trees
         $root
      }"""

    val verbose = output
      .toString().split("\n")
      .mkString(Console.GREEN, s"\n${Console.GREEN}", Console.RESET)

    val elapsed = stopWatch()

    if (settings.verbose)
      c.info(
        c.enclosingPosition,
        Log(s"\nDone in $elapsed.\n $verbose")(Nil),
        force = false
      )

    output
  }

  private def assembleTrees: Set[AssemblyTree] = {

    val trees = mutable.HashSet.empty[AssemblyTree]

    val assemblies: Map[Named[Type], Assembly] =
      analyzeType(
        rootType,
        Props(root = true),
        Seq.empty,
        Map.empty
      )

    if (settings.debug) {
      c.echo(
        c.enclosingPosition,
        assemblies
          .map {
            case (name, assembly) =>
              s"Assembly for key ${name.toString.yellow}: $assembly"
          }.mkString("\n\n", "\n", "\n\n")
      )
    }

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
                Log(
                  s"Couldn't create an instance of [{}] " +
                    s"when constructing [{}]",
                  named,
                  assembly.`type`
                )(Nil)
              )
            ).tname
        }
      }
      tree = AssemblyTree(
        assembly.tname,
        assembly.assignTree(arguments),
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
    val binder = binders.get(Named(`type`, props.name))
    val bindedType = binder.map(_.`type`).getOrElse(`type`)

    // let's also check what annotations does this type has
    val newProps = props || Props(
      repl = bindedType.typeSymbol
        .isAnnotatedWith(typeOf[replicated])
    )

    // second, check if we've already seen this type
    if (rootPath.contains(bindedType)) {
      c.abort(
        c.enclosingPosition,
        Log(s"Circular dependency detected: {}", (rootPath :+ bindedType).mkString(" -> "))(rootPath)
      )
    }

    // ok, we have a type, now let's see if there is a provide for it
    val bindedIndentifier = Named(bindedType, newProps.name)
    val providerOrNone = providers.get(bindedIndentifier)

    // we might already have this type from an interface binder, for example
    if (output.contains(bindedIndentifier)) output
    else {

      val bindedTypes: Map[Named[Type], Type] = binders.view.mapValues(_.`type`).to(Map)

      providerOrNone match {

        case Some(Provider(symbol, props, _)) =>
          // we have a provide, let's create an assembly

          val paramSymbolLists = symbol.asMethod.paramLists
          val paramPropsLists = namedPropsBindedTypeSignatures(paramSymbolLists, binders)
          val paramLists = paramPropsLists.map(_.map(_.named))
          val assembly = Assembly(`type`, symbol, newProps || props)(paramLists)
          val newOutput = output + (bindedIndentifier -> assembly)

          analyzeParameterLists(paramPropsLists, rootPath :+ `type`, newOutput)

        case None =>
          // no provider means we'll be calling a constructor but can we instantinate this type?

          if (bindedType.typeSymbol.isAbstract) {

            if (settings.debug) {
              val out = providers
                .map {
                  case (k, v) =>
                    new StringBuilder("[".yellow)
                      .append(k.toString.yellow)
                      .append("]".yellow)
                      .append(": ")
                      .append(v.toString)
                      .toString()
                }.mkString("\n")
              c.echo(c.enclosingPosition, Log("\nProviders:\n" + out)(Nil))
            }

            c.abort(
              c.enclosingPosition,
              Log(
                s"Cannot construct an instance of an abstract class " +
                  s"[{}], provide a concrete class binder or an instance provider.",
                bindedIndentifier
              )(rootPath)
            )
          }

          // can we find a contructor then?

          val constructor = discoverConstructor(bindedType).getOrElse(
            c.abort(
              c.enclosingPosition,
              Log(
                s"Cannot find a public constructor for " +
                  s"[{}], provide a concrete class binder or an instance provider.",
                bindedIndentifier
              )(rootPath)
            )
          )

          // if yes then let's create an assembly

          val paramSymbolLists = constructor.asMethod.paramLists
          val paramPropsLists = namedPropsBindedTypeSignatures(paramSymbolLists, binders)
          val paramLists = paramPropsLists.map(_.map(_.named))
          val assembly = Assembly(`type`, bindedType, newProps)(paramLists)
          val newOutput = output + (Named(`type`, newProps.name) -> assembly)

          analyzeParameterLists(paramPropsLists, rootPath :+ `type`, newOutput)
      }
    }
  }

  private def namedPropsBindedTypeSignatures(
    symbolLists: List[List[Symbol]],
    binders: Map[Named[Type], Binder]
  ): List[List[NamedProps]] = {
    symbolLists.map {
      for {
        symbol <- _
        name = symbol.named
        symbolType = symbol.typeSignature.dealiasAll
        identifier = Named(symbolType, name)
        Binder(bindedType, props, _) = binders
          .get(identifier)
          .getOrElse(Binder(symbolType, Props(name), false))
      } yield NamedProps(Named(bindedType, name), props)
    }
  }

  private def analyzeParameterLists(
    parameterPropsLists: List[List[NamedProps]],
    rootPath: Seq[Type],
    output: Map[Named[Type], Assembly]
  ): Map[Named[Type], Assembly] = {

    parameterPropsLists.foldLeft(output) {
      case (output, list) =>
        list.foldLeft(output) {
          case (output, NamedProps(Named(parameterType, name), props)) =>
            analyzeType(parameterType, props, rootPath, output)
        }
    }
  }
}
