package factorio

trait Syntax {

  object EmptyRecipe extends Recipe

  def assemble[T]: Assembler[T] = new Assembler[T]
}
