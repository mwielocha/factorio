package io.mwielocha.factorio

trait Recipe {
  def bind[T]: To[T] =
    new To[T]
}
