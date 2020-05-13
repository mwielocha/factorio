package factorio.annotations

import factorio.Binder
import scala.annotation.StaticAnnotation

class binds[B <: Binder[_, _]] private (named: Option[String], replicated: Boolean, overrides: Boolean) extends StaticAnnotation {

  def this() =
    this(None, false, false)

  def this(named: String) =
    this(Some(named), false, false)

  def this(replicated: Boolean) =
    this(None, replicated, false)

  def this(named: String, replicated: Boolean) =
    this(Some(named), replicated, false)

  def this(replicated: Boolean, overrides: Boolean) =
    this(None, replicated, overrides)

  def this(named: String, replicated: Boolean, overrides: Boolean) =
    this(Some(named), replicated, overrides)

}
