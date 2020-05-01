package io.mwielocha.factorio.auto.internal

trait Toolbox {

  def firstCharLowerCase(s: String): String =
    if (s.nonEmpty) s"${Character.toLowerCase(s.charAt(0))}${s.substring(1)}" else s

}
