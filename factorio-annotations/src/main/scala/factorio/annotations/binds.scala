package factorio.annotations

import factorio.Binder
import scala.annotation.StaticAnnotation

// class binds[B <: Binder[_, _]] private (
//   named: Option[String],
//   replicated: Option[replicated],
//   overrides: Option[overrides]
// ) extends StaticAnnotation {

//   def this(replicated: replicated) =
//     this(None, Some(replicated), None)

//   def this(overrides: overrides) =
//     this(None, None, Some(overrides))

//   def this(named: String) =
//     this(Some(named), None, None)

//   def this(named: String, overrides: overrides) =
//     this(Some(named), None, Some(overrides))

//   def this(named: String, replicated: replicated) =
//     this(Some(named), Some(replicated), None)

//   def this(named: String, replicated: replicated, overrides: overrides) =
//     this(Some(named), Some(replicated), Some(overrides))

// }

class binds[B <: Binder[_, _]](annotation: StaticAnnotation*) extends StaticAnnotation
