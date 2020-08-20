package factorio

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

object WeirdCaseDomain {

  trait Thing
  class RealThing extends Thing

  class WantSomething(
    val some: Thing
  )

}

class WeirdCaseSpec extends AnyFlatSpec with Matchers {

  import WeirdCaseDomain._

  @blueprint
  class TestBlueprint {

    @provides
    def thing: Thing = new RealThing
  }

  "Assembly macro" should "assemble a component" in {

//    compilation fails if uncommented
//    val assembler = Assembler[WantSomething](new TestBlueprint)
//    assembler()

    succeed
  }

}
