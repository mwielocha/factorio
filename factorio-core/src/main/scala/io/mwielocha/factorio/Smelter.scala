package io.mwielocha.factorio

trait Smelter[T] {
  def `with`[K <: T: Assembler](implicit asm: Assembly): Assembler[T]
}
