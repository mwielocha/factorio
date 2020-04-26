package io.mwielocha.factorio.auto

import io.mwielocha.factorio.{DefaultComponent, _}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AutoAssemblySpec extends AnyFlatSpec with Matchers {

  "Assembly" should "assemble an app with auto assemblers" in {

    implicit val make: Assembly = Assembly()

    val app = make[TestApp]
    val component = make[Component]
    val repository = make[Repository]

    app.superComponent.component shouldBe component
    app.superComponent.repository shouldBe repository
  }

  it should "assemble an app with auto and custom assemblers" in {

    implicit val make: Assembly = Assembly()

    implicit val interfaceAssembler: Assembler[Interface] =
      Assembler(() => new DefaultComponent(
        implicitly[Assembler[Component]].assemble,
        implicitly[Assembler[Repository]].assemble
      ))

    val app = make[TestApp]
    val component = make[Component]
    val repository = make[Repository]
    val interface = make[Interface]
    val defaultComponent = make[DefaultComponent]

    app.superComponent.component shouldBe component
    app.superComponent.repository shouldBe repository
    defaultComponent shouldNot be(interface)
  }

  it should "assemble an app with auto assemblers from recipe" in {

    implicit val make: Assembly = Assembly()

    val recipe = new Recipes with ComponentRecipe
    import recipe._

    val app = make[TestApp]
    val interface = make[Interface]
    val superComponent = make[SuperComponent]
    val defaultComponent = make[DefaultComponent]

    app.superComponent shouldBe superComponent

    superComponent shouldBe interface
    defaultComponent shouldBe interface
  }

  it should "assemble an app with auto assemblers with interfaces" in {

    implicit val make: Assembly = Assembly()

    val recipe = new Recipes with ComponentRecipe
    import recipe._

    val app = make[TestApp]
    val interface = make[Interface]
    val superComponent = make[SuperComponent]
    val defaultComponent = make[DefaultComponent]
    val interfaceComponent = make[InterfaceComponent]

    app.superComponent shouldBe superComponent

    superComponent shouldBe interface
    defaultComponent shouldBe interface
    interfaceComponent.component shouldBe superComponent
  }

  it should "assemble an app with auto assemblers with multi param components" in {

    implicit val make: Assembly = Assembly()

    val recipe = new Recipes with ComponentRecipe
    import recipe._


    val repository = make[Repository]
    val superComponent = make[SuperComponent]
    val multiParamListComponent = make[MultiParamListComponent]

    multiParamListComponent.component shouldBe superComponent
    multiParamListComponent.repository shouldBe repository
  }

  it should "assemble an app with auto assemblers with replicated components" in {

    implicit val make: Assembly = Assembly()

    implicit val replicatedAssembler: Assembler[Component] =
      replicated[Component]

    val componentA = make[Component]
    val componentB = make[Component]

    componentA shouldNot be(componentB)
  }
}
