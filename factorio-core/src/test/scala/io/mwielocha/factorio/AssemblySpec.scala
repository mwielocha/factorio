package io.mwielocha.factorio

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AssemblySpec extends AnyFlatSpec with Matchers {

  "Assembly" should "assemble an app with custom assemblers" in {

    implicit val assembly: Assembly = Assembly()

    implicit val componentAssembler: Assembler[Component] = Assembler[Component](() => new Component)
    implicit val repositoryAssembler: Assembler[Repository] = Assembler[Repository](() => new Repository)
    implicit def appAssembler(
      implicit
        componentAssembler: Assembler[Component],
        repositoryAssembler: Assembler[Repository]
    ): Assembler[SuperComponent] = Assembler[SuperComponent](() => new SuperComponent(componentAssembler.assemble, repositoryAssembler.assemble))

    val app = assembly[SuperComponent]

    app.component shouldBe componentAssembler.assemble
    app.repository shouldBe repositoryAssembler.assemble

  }
}
