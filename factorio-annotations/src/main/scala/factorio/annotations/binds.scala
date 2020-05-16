package factorio.annotations

import factorio.Binder
import scala.annotation.StaticAnnotation

class binds[B <: Binder[_, _]](annotation: StaticAnnotation*) extends StaticAnnotation
