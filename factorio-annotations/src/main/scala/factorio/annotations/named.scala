package factorio.annotations

import scala.annotation.StaticAnnotation

class named(name: String) extends StaticAnnotation

object named {
  def apply(name: String) = new named(name)
}
