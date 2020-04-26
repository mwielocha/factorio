package io.mwielocha.factorio.auto

import io.mwielocha.factorio.{Assembler, DefaultComponent, Interface, SuperComponent, Recipe}

trait ComponentRecipe {
  requires: Recipe =>

  implicit val interfaceAssembler: Assembler[Interface] =
    smelt[Interface].`with`[DefaultComponent]

  implicit val componentAssembler: Assembler[SuperComponent] =
    smelt[SuperComponent].`with`[DefaultComponent]

}
