package io.mwielocha.factorio.auto

import io.mwielocha.factorio._
import javax.inject.Named
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AssemblyMacroSpec extends AnyFlatSpec with Matchers {

  "Assembly macro" should "assemble a simple app" in {

    val assembler = assemble[TestApp, Unit](())

    val app = assembler()
    app.superComponent.repository shouldBe app.repository
  }

  it should "assemble an app from a recipe with a simple @provides method" in {

    val component = new Component

    class SimpleRecipe {
      @Provides
      def getComponent: Component =
        component
    }

    val assembler = assemble[TestApp, SimpleRecipe](new SimpleRecipe)

    val app = assembler()

    app.superComponent.component shouldBe component
  }

  it should "assemble an app from a recipe with a complex @provides method" in {

    val repository = new Repository

    class SimpleRecipe {
      @Provides
      def makeSuperComponent(component: Component): SuperComponent =
        new SuperComponent(component, repository)
    }

    val assembler = assemble[TestApp, SimpleRecipe](new SimpleRecipe)

    val app = assembler()

    app.repository should not be (repository)
    app.superComponent.repository shouldBe repository
  }

  it should "assemble an app from a recipe with binders" in {

    class TestAppRecipe extends Recipe {
      val bindSuperComponent = bind[SuperComponent].to[DefaultComponent]
    }

    val assembler = assemble[TestApp, TestAppRecipe](new TestAppRecipe)

    val app = assembler()

    app.superComponent.getClass shouldBe classOf[DefaultComponent]
  }

  it should "assemble an app from a recipe with labels" in {

    class LabeledAppRecipe extends Recipe {

      @Provides
      @Named("that")
      def thatComponent =
        new Component

      @Provides
      @Named("other")
      def otherComponent =
        new Component
    }

    val assembler = assemble[LabeledApp, LabeledAppRecipe](new LabeledAppRecipe)

    val app = assembler()

    app.that should not be (app.other)
  }

  it should "not compile when circular dependency exists" in {
    assertDoesNotCompile("assemble[OtherCircularComponent]")
  }
}
