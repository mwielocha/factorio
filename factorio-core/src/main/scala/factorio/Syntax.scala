package factorio

trait Syntax {

  def bind[T]: To[T] =
    new To[T]

  type named = annotations.named
  type provides = annotations.provides
  type blueprint = annotations.blueprint
  type overrides = annotations.overrides
  type replicated = annotations.replicated

  object Blank

  @deprecated("Use Assembler[T] instead.", "0.0.2")
  def assemble[T]: Assembler[T] = Assembler[T]

  implicit def intellijHack[T, R](in: Assembler[T]): R => () => T = ???

}
