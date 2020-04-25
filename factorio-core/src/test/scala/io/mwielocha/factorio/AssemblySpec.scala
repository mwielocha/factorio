package io.mwielocha.factorio

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AssemblySpec extends AnyFlatSpec with Matchers {

  class Component
  class Repository
  class App(val component: Component, val repository: Repository)

  "Assembly" should "assemble an app with custom assemblers" in {

    implicit val componentAssembler: Assembler[Component] = Assembler[Component](new Component)
    implicit val repositoryAssembler: Assembler[Repository] = Assembler[Repository](new Repository)
    implicit def appAssembler(
      implicit
        componentAssembler: Assembler[Component],
        repositoryAssembler: Assembler[Repository]
    ): Assembler[App] = Assembler[App](new App(componentAssembler.assemble, repositoryAssembler.assemble))

    val app = Assembly[App]

    app.component shouldBe componentAssembler.assemble
    app.repository shouldBe repositoryAssembler.assemble

  }
}
