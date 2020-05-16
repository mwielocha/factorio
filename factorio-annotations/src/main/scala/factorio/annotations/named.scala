package factorio.annotations

import scala.annotation.StaticAnnotation

class named(name: String) extends StaticAnnotation

object named {
  def apply(in: String) = new named(in)
}
