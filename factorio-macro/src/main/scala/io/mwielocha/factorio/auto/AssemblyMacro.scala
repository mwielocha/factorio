package io.mwielocha.factorio.auto

import io.mwielocha.factorio.{Binder, Provides}
import io.mwielocha.factorio.auto.internal.MacroToolbox

import scala.reflect.macros.blackbox

object AssemblyMacro extends MacroToolbox {

  def assemble[T: c.WeakTypeTag, R: c.WeakTypeTag](c: blackbox.Context)(recipe: c.Expr[R]): c.Expr[() => T] = {
    import c.universe._

    val tpe = weakTypeTag[T].tpe.dealias
    val rtpe = weakTypeTag[R].tpe.dealias
    val rname = name(c)(rtpe)

    val bind = binderLookup(c)(rtpe)
    val prov = providerLookup(c)(rtpe)

    val assemblies = assemblyTrees(c)(tpe, rname, bind, prov)
    val names = assemblies.map { case (_, tree) => tree}
    val entry = assemblies.last._1

    val output =
      q"""() => {
          val $rname: $rtpe = $recipe
         ..$names
         $entry
      }"""

    println(output)
    c.Expr[() => T](output)
  }

  private [auto] def assemblyTrees(c: blackbox.Context)(
    targetType: c.Type,
    rname: c.TermName,
    bind: Map[(c.Type, Option[String]), c.Type],
    prov: Map[(c.Type, Option[String]), c.Symbol]
  ): Seq[(c.TermName, c.Tree)] = {
    import c.universe._

    type Assembly = (c.TermName, c.Tree)

    val trees = Seq.empty[Assembly]
    val graph = constructDependencyGraph(c)(bind, prov)(
      targetType,
      None,
      Seq.empty,
      Seq.empty,
    ).reverse

    graph.foldLeft(trees) {
      case (trees, (tpe, fname, const, lab)) =>
        trees :+ {
          val args = const.asMethod.paramLists.map {
            _.flatMap {
              param =>
                val ptpe = param.typeSignature.dealias
                val plab = extractLabel(c)(param)

                graph.find {
                  case (tpe, _, _, lab) =>
                    tpe == ptpe && plab == lab
                } match {
                  case Some((_, name, _, _)) =>
                    Some(q"""$name""")
                  case None => None
                }
            }
          }

          val isConst = const
            .asMethod
            .isConstructor

          val btpe = bind.getOrElse(tpe -> lab, tpe)

          val constTree =
            if(isConst) q"""new $btpe(...$args)"""
            else q"""$rname.$const(...$args)"""

          (fname, q"""lazy val $fname: $tpe = $constTree""")
        }
    }
  }

  private [auto] def constructDependencyGraph(c: blackbox.Context)(
    bind: Map[(c.Type, Option[String]), c.Type] = Map.empty,
    prov: Map[(c.Type, Option[String]), c.Symbol] = Map.empty
  )(
    tpe: c.Type, // type
    lab: Option[String],
    pth: Seq[c.Type] = Seq.empty, //path from root
    out: Seq[(c.Type, c.TermName, c.Symbol, Option[String])] = Seq.empty, // output
  ): Seq[(c.Type, c.TermName, c.Symbol, Option[String])] = {

    val btpe = bind.getOrElse(tpe -> lab, tpe)

    if(pth.contains(btpe))
      c.abort(c.enclosingPosition,
        Error(s"Circular dependency detected: ${(pth :+ btpe).mkString(" -> ")}")
      )

    val alreadyVisited = out.view.map {
      case (vtpe, _, _, vlab) =>
        vtpe -> vlab
    }.to(Set)

    if(alreadyVisited(btpe -> lab)) out else {

      val const = lab match {

        case lab @ Some(v) =>
          prov.getOrElse(btpe -> lab, c.abort(c.enclosingPosition,
            Error(s"Proivider not found for an instance of [${Console.YELLOW}$btpe${Console.RED}] " +
              s"labeled with [${Console.YELLOW}$v${Console.RED}]")
          ))

        case None =>
          prov.getOrElse(btpe -> None, discoverConstructor(c)(btpe)
            .getOrElse {
              c.abort(c.enclosingPosition,
                Error(s"Cannot construct an instance of [${Console.YELLOW}$btpe${Console.RED}]")
              )
            })
      }

      val newOut = out :+ (tpe, label(c)(btpe, lab), const, lab)

      const.asMethod.paramLists.foldLeft(newOut) {
        case (out, list) =>
          list.foldLeft(out) {
            case (out, p) =>
              val ptpe = p.typeSignature.dealias
              val lab = extractLabel(c)(p)
              constructDependencyGraph(c)(bind, prov)(ptpe, lab, pth :+ btpe, out)
          }

      }
    }
  }

  private [auto] def providerLookup(c: blackbox.Context)(rtpe: c.Type): Map[(c.Type, Option[String]), c.Symbol] = {
    import c.universe._

    val rprov = Map.empty[(c.Type, Option[String]), Symbol]

    rtpe.baseClasses.foldLeft(rprov) {
      case (acc, clazz) =>
        clazz.typeSignature.members.foldLeft(acc) {
          case (acc, m) =>
            if(m.isMethod && m.isPublic && isAnnotated(c)(m, typeOf[Provides])) {
              val tpe = m
                .typeSignature
                .resultType
                .dealias

              acc + (tpe -> extractLabel(c)(m) -> m)
            } else acc
        }
    }
  }

  private [auto] def binderLookup(c: blackbox.Context)(rtpe: c.Type): Map[(c.Type, Option[String]), c.Type] = {
    import c.universe._

    val rbind = Map.empty[(Type, Option[String]), Type]

    def isBinder(m: Symbol): Boolean =
      m.typeSignature
        .resultType
        .erasure == typeOf[Binder[_, _]].erasure

    rtpe.baseClasses.foldLeft(rbind) {
      case (acc, clazz) =>
        clazz.typeSignature.members.foldLeft(acc) {
          case (acc, m) =>
            if(m.isMethod && m.isTerm && isBinder(m)) {
              val t :: k :: Nil = m
                .typeSignature
                .resultType
                .typeArgs
                .map(_.dealias)

              acc + ((t -> extractLabel(c)(m)) -> k)
            } else acc
        }
    }
  }
}
