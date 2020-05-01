package io.mwielocha.factorio.auto.internal

import io.mwielocha.factorio.{ Binder, Provides }

import scala.reflect.macros.blackbox

class Assembler[C <: blackbox.Context, T : C#WeakTypeTag, R : C#WeakTypeTag](override val c: C) extends MacroToolbox[C] {

  import c.universe._

  case class Assem(name: TermName, tree: Tree)
  case class Named(tpe: Type, label: Option[String])
  case class Const(tpe: Type, name: TermName, const: Symbol, label: Option[String])

  type Binds = Map[Named, Type]
  type Provs = Map[Named, Symbol]

  def apply(recipe: c.Expr[R]): Tree = {

    val tpe = weakTypeTag[T].tpe.dealias
    val rtpe = weakTypeTag[R].tpe.dealias
    val rname = createUniqueName(rtpe)

    val bind = binderLookup(rtpe)
    val prov = providerLookup(rtpe)

    val assemblies = assemblyTrees(tpe, rname, bind, prov)
    val trees = assemblies.map { case Assem(_, tree) => tree }
    val entry = assemblies.last.name

    val output =
      q"""() => {
          val $rname: $rtpe = $recipe
         ..$trees
         $entry
      }"""

    println(output)
    output
  }

  private[auto] def assemblyTrees(
    targetType: Type,
    rname: TermName,
    bind: Binds,
    prov: Provs
  ): Seq[Assem] = {
    import c.universe._

    val trees = Seq.empty[Assem]

    val graph = constructDependencyGraph(bind, prov)(
      targetType,
      None,
      Seq.empty,
      Seq.empty
    ).reverse

    graph.foldLeft(trees) {
      case (trees, Const(tpe, fname, const, lab)) =>
        trees :+ {
          val args = const.asMethod.paramLists.map {
            _.flatMap { param =>
              val ptpe = param.typeSignature.dealias
              val plab = extractLabel(param)

              graph.find {
                case Const(tpe, _, _, lab) =>
                  tpe == ptpe && plab == lab
              } match {
                case Some(Const(_, name, _, _)) =>
                  Some(q"""$name""")
                case None => None
              }
            }
          }

          val isConst = const.asMethod.isConstructor

          val btpe = bind.getOrElse(Named(tpe, lab), tpe)

          val constTree =
            if (isConst) q"""new $btpe(...$args)"""
            else q"""$rname.$const(...$args)"""

          Assem(fname, q"""lazy val $fname: $tpe = $constTree""")
        }
    }
  }

  private[auto] def constructDependencyGraph(
    bind: Binds = Map.empty,
    prov: Provs = Map.empty
  )(
    tpe: Type, // type
    lab: Option[String],
    pth: Seq[Type] = Seq.empty, //path from root
    out: Seq[Const] = Seq.empty // output
  ): Seq[Const] = {

    val btpe = bind.getOrElse(Named(tpe, lab), tpe)

    if (pth.contains(btpe))
      c.abort(c.enclosingPosition, Error(s"Circular dependency detected: ${(pth :+ btpe).mkString(" -> ")}"))

    val alreadyVisited = out.view
      .map {
        case Const(vtpe, _, _, vlab) =>
          vtpe -> vlab
      }.to(Set)

    if (alreadyVisited(btpe -> lab)) out
    else {

      val const = lab match {

        case lab @ Some(v) =>
          prov.getOrElse(
            Named(btpe, lab),
            c.abort(
              c.enclosingPosition,
              Error(
                s"Proivider not found for an instance of [${Console.YELLOW}$btpe${Console.RED}] " +
                  s"labeled with [${Console.YELLOW}$v${Console.RED}]"
              )
            )
          )

        case None =>
          prov.getOrElse(
            Named(btpe, lab),
            discoverConstructor(btpe)
              .getOrElse {
                c.abort(c.enclosingPosition, Error(s"Cannot construct an instance of [${Console.YELLOW}$btpe${Console.RED}]"))
              }
          )
      }

      val newOut = out :+ Const(tpe, createUniqueLabel(btpe, lab), const, lab)

      const.asMethod.paramLists.foldLeft(newOut) {
        case (out, list) =>
          list.foldLeft(out) {
            case (out, p) =>
              val ptpe = p.typeSignature.dealias
              val lab = extractLabel(p)
              constructDependencyGraph(bind, prov)(ptpe, lab, pth :+ btpe, out)
          }

      }
    }
  }

  private[auto] def providerLookup(rtpe: Type): Provs = {

    val rprov = Map.empty[Named, Symbol]

    rtpe.baseClasses.foldLeft(rprov) {
      case (acc, clazz) =>
        clazz.typeSignature.members.foldLeft(acc) {
          case (acc, m) =>
            if (m.isMethod && m.isPublic && isAnnotated(m, typeOf[Provides])) {
              val tpe = m.typeSignature.resultType.dealias

              acc + (Named(tpe, extractLabel(m)) -> m)
            } else acc
        }
    }
  }

  private[auto] def binderLookup(rtpe: Type): Binds = {
    import c.universe._

    val rbind = Map.empty[Named, Type]

    def isBinder(m: Symbol): Boolean =
      m.typeSignature.resultType.erasure == typeOf[Binder[_, _]].erasure

    rtpe.baseClasses.foldLeft(rbind) {
      case (acc, clazz) =>
        clazz.typeSignature.members.foldLeft(acc) {
          case (acc, m) =>
            if (m.isMethod && m.isTerm && isBinder(m)) {
              val t :: k :: Nil = m.typeSignature.resultType.typeArgs
                .map(_.dealias)

              acc + (Named(t, extractLabel(m)) -> k)
            } else acc
        }
    }
  }
}
