package io.mwielocha.factorio

object Assembly {

  def apply[T: Assembler]: T =
    implicitly[Assembler[T]].assemble

}
