package io.mwielocha.factorio

trait Syntax {

  def smelt[T]: Smelter[T] = new Smelter[T] {
    override def `with`[K <: T : Assembler](implicit asm: Assembly): Assembler[T] =
      implicitly[Assembler[K]].asInstanceOf[Assembler[T]]
  }
}
