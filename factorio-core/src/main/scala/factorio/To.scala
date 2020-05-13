package factorio

import scala.language.experimental.macros

class To[T] {

  def to[K <: T]: Binder[T, K] =
    new Binder[T, K] {}
}
