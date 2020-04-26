package io.mwielocha.factorio

import scala.reflect.ClassTag
import scala.collection.mutable

class Assembly {

  private[this] val forge: mutable.Map[Class[_], AnyRef] = mutable.Map.empty

  private[factorio] def getOrRegister[T : ClassTag](assembler: () => Assembler[T]): Assembler[T] =
    synchronized {
      val runtimeClass = implicitly[ClassTag[T]].runtimeClass
      val that: Assembler[T] = forge
        .getOrElse(runtimeClass, assembler())
        .asInstanceOf[Assembler[T]]
      forge.update(runtimeClass, that)
      that
    }

  def apply[T: Assembler]: T = assemble

  def assemble[T : Assembler]: T = {
    implicit val ctx: Assembly = this
    implicitly[Assembler[T]].assemble
  }
}

object Assembly {
  def apply(): Assembly = new Assembly()
}
