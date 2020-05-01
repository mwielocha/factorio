package io.mwielocha.factorio

class To[T] {

  def to[K <: T]: Binder[T, K] =
    new Binder[T, K] {}
}
