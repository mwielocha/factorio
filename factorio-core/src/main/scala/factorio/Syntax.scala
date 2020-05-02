package factorio

trait Syntax {

  def bind[T]: To[T] =
    new To[T]

  type named = annotations.named
  type provides = annotations.provides
  type blueprint = annotations.blueprint
  type replicated = annotations.replicated

  object Blank

  def assemble[T]: Assembler[T] = new Assembler[T]

  implicit def intellijHack[T, R](in: Assembler[T]): R => () => T = ???
}
