package io.mwielocha.factorio.auto

import scala.reflect.macros.blackbox

object AssemblerMacro {

  private def constructTree[T: c.WeakTypeTag](c: blackbox.Context): (c.Type, c.Tree) = {
    import c.universe._

    lazy val targetType: Type = implicitly[c.WeakTypeTag[T]].tpe
    lazy val realTargetType: Type = targetType.dealias

    lazy val constructors = realTargetType.members
      .filter(m => m.isMethod && m.asMethod.isConstructor && m.isPublic)
      .filterNot(_.asMethod.fullName.endsWith("$init$"))

    val assemblies: List[List[Tree]] = constructors.find(_.asMethod.isPrimaryConstructor).map {
      _.asMethod.paramLists.map {
        _.map { param =>
          q"""implicitly[Assembler[${param.typeSignature}]].assemble"""
        }
      }
    } getOrElse c.abort(c.enclosingPosition,
      s"Cannot construct an instance of [$realTargetType], " +
        s"create custom assembler or provide a public constructor.")

    val constructionMethodTree: Tree = Select(New(Ident(realTargetType.typeSymbol)), termNames.CONSTRUCTOR)
    realTargetType -> assemblies.foldLeft(constructionMethodTree) {
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
