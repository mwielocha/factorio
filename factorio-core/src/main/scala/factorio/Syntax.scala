package factorio

trait Syntax {

  def bind[T]: To[T] =
    new To[T]

  def binder[T, K <: T] = new Binder[T, K] {}

  type to[K, T <: K] = Binder[K, T]

  type named = annotations.named
  type provides = annotations.provides
  type blueprint = annotations.blueprint
  type overrides = annotations.overrides
  type replicated = annotations.replicated
  type binds[B <: Binder[_, _]] = annotations.binds[B]

  object Blank

  @deprecated("Use Assembler[T] instead.", "0.0.2")
  def assemble[T]: Assembler[T] = Assembler[T]

  implicit def intellijHack[T, R](in: Assembler[T]): R => () => T = ???

}
