package io.mwielocha.factorio.auto

import io.mwielocha.factorio.auto.internal.MacroToolbox

import scala.reflect.macros.blackbox

object AssemblerMacro extends MacroToolbox {



  private def constructTree[T: c.WeakTypeTag](c: blackbox.Context): (c.Type, c.Tree) = {
    import c.universe._

    lazy val targetType: Type = weakTypeTag[T].tpe.dealias

    val constructor = discoverConstructor(c)(targetType).getOrElse {
      c.abort(c.enclosingPosition,
        Error(s"Cannot construct an instance of [${Console.YELLOW}$targetType${Console.RED}], " +
          s"create custom assembler or provide a public constructor.")
      )
    }

    val constructorParameters = constructor.asMethod.paramLists

    checkForCircularDependencies(c)

    val constructorAssemblies: List[List[Tree]] = constructorParameters.map {
      _.map(param => q"""implicitly[Assembler[${param.typeSignature}]].assemble""")
    }

    val constructorAssembliesTree: Tree = Select(
      New(Ident(targetType.typeSymbol)),
      termNames.CONSTRUCTOR
    )

    targetType -> constructorAssemblies.foldLeft(constructorAssembliesTree) {
      (acc: Tree, args: List[Tree]) =>
        Apply(acc, args)
    }
  }

  def lazySingleton[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Assembler[T]] = {
    import c.universe._

    val (tpe, tree) = constructTree(c)

    val assembly =
      q"""Assembler(() => $tree)(
         implicitly[scala.reflect.ClassTag[$tpe]],
         implicitly[Assembly])"""


    c.Expr[Assembler[T]](assembly)
  }

  def eagerSingleton[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Assembler[T]] = {
    import c.universe._

    val (tpe, tree) = constructTree(c)

    val assembly =
      q"""Assembler.eager(() => $tree)(
         implicitly[scala.reflect.ClassTag[$tpe]],
         implicitly[Assembly])"""


    c.Expr[Assembler[T]](assembly)
  }

  def replicated[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Assembler[T]] = {
    import c.universe._

    val (tpe, tree) = constructTree(c)

    val assembly =
      q"""Assembler.replicated(() => $tree)(
         implicitly[scala.reflect.ClassTag[$tpe]],
         implicitly[Assembly])"""


    c.Expr[Assembler[T]](assembly)
  }
}
