package io.mwielocha.factorio.auto

import io.mwielocha.factorio.{Assembler, Assembly}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AutoAssemblySpec extends AnyFlatSpec with Matchers {

  class Component
  class Repository
  class App(val component: Component, val repository: Repository)

  "Assembly" should "assemble an app with auto assemblers" in {

    import Implicits._

    val app = Assembly[App]

    app.component shouldBe implicitly[Assembler[Component]].assemble
    app.repository shouldBe implicitly[Assembler[Repository]].assemble

  }
}
