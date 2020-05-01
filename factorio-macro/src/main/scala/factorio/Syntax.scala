package factorio

trait Syntax {

  type Named = javax.inject.Named

  object EmptyRecipe extends Recipe

  def assemble[T]: Assembler[T] = new Assembler[T]
}
